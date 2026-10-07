package io.github.kylinlee.chatsim.data.repository

import android.content.Context
import io.github.kylinlee.chatsim.di.IoDispatcher
import io.github.kylinlee.chatsim.common.extensions.conversationsDB
import io.github.kylinlee.chatsim.common.extensions.conversationSnippet
import io.github.kylinlee.chatsim.common.extensions.deleteMessage
import io.github.kylinlee.chatsim.common.extensions.getMessages
import io.github.kylinlee.chatsim.common.extensions.markMessageRead
import io.github.kylinlee.chatsim.common.extensions.markMessagesRead
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.common.extensions.moveMessageToRecycleBin
import io.github.kylinlee.chatsim.common.extensions.restoreMessageFromRecycleBin
import io.github.kylinlee.chatsim.common.MESSAGES_LIMIT
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.repository.MessageRepository
import io.github.kylinlee.chatsim.repository.ScheduledTaskRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val scheduledTaskRepository: ScheduledTaskRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MessageRepository {
    override suspend fun getConversationMessages(conversationId: Long, includeRecycleBin: Boolean): List<Message> =
        withContext(ioDispatcher) {
            when {
                includeRecycleBin -> context.messagesDB.getConversationMessages(conversationId)
                settings.useRecycleBin -> context.messagesDB.getNonRecycledConversationMessages(conversationId)
                else -> context.messagesDB.getConversationMessages(conversationId)
            }
        }

    override suspend fun getConversationMessagesFromRecycleBin(conversationId: Long): List<Message> = withContext(ioDispatcher) {
        context.messagesDB.getConversationMessagesFromRecycleBin(conversationId)
    }

    override suspend fun getMessagesWithText(query: String): List<Message> = withContext(ioDispatcher) {
        context.messagesDB.getMessagesWithText(query)
    }

    override suspend fun getRecycleBinMessages(): List<Message> = withContext(ioDispatcher) {
        context.messagesDB.getAllRecycleBinMessages()
    }

    override suspend fun getRecycleBinCount(): Int = withContext(ioDispatcher) {
        context.messagesDB.getRecycleBinCount()
    }

    override suspend fun loadSystemMessages(
        threadId: Long,
        getImageResolutions: Boolean,
        dateFrom: Int,
        includeScheduledMessages: Boolean,
        limit: Int,
    ): List<Message> = withContext(ioDispatcher) {
        context.getMessages(
            threadId = threadId,
            getImageResolutions = getImageResolutions,
            dateFrom = dateFrom,
            includeScheduledMessages = includeScheduledMessages,
            limit = limit,
        )
    }

    override suspend fun persistMessages(messages: List<Message>) = withContext(ioDispatcher) {
        val existingDeliveryStatus = context.messagesDB.getByIds(messages.map { it.id })
            .associate { it.id to it.deliveryStatus }
        messages.chunked(30).forEach { chunk ->
            context.messagesDB.insertMessages(
                *chunk.map { message ->
                    existingDeliveryStatus[message.id]?.let { message.copy(deliveryStatus = it) } ?: message
                }.toTypedArray()
            )
        }
    }

    override suspend fun insertOrUpdate(message: Message) = withContext(ioDispatcher) {
        context.messagesDB.insertOrUpdate(message)
    }

    override suspend fun markRead(id: Long, isMms: Boolean) = withContext(ioDispatcher) {
        context.markMessageRead(id, isMms)
    }

    override suspend fun markConversationRead(conversationId: Long) = withContext(ioDispatcher) {
        val ids = context.messagesDB.getConversationMessageIds(conversationId)
        context.markMessagesRead(ids)
        context.messagesDB.markConversationRead(conversationId)
    }

    override suspend fun updateType(id: Long, type: Int) {
        withContext(ioDispatcher) {
            context.messagesDB.updateType(id, type)
        }
    }

    override suspend fun updateStatus(id: Long, status: Int) {
        withContext(ioDispatcher) {
            context.messagesDB.updateStatus(id, status)
        }
    }

    override suspend fun deleteMessage(message: Message) {
        withContext(ioDispatcher) {
            if (message.isScheduled) {
                scheduledTaskRepository.deleteScheduledMessage(message.id)
            } else {
                context.deleteMessage(message.id, message.isMMS)
            }
        }
    }

    override suspend fun deleteMessages(messages: List<Message>) {
        withContext(ioDispatcher) {
            messages.forEach { deleteMessage(it) }
            messages.map { it.conversationId }.distinct().forEach { refreshConversation(it) }
        }
    }

    override suspend fun moveMessagesToRecycleBin(messages: List<Message>) {
        withContext(ioDispatcher) {
            messages.forEach { message ->
                if (message.isScheduled) {
                    scheduledTaskRepository.deleteScheduledMessage(message.id)
                } else {
                    context.moveMessageToRecycleBin(message.id)
                }
            }
            messages.map { it.conversationId }.distinct().forEach { refreshConversation(it) }
        }
    }

    override suspend fun restoreMessage(id: Long) = withContext(ioDispatcher) {
        context.restoreMessageFromRecycleBin(id)
    }

    override suspend fun restoreAllMessages(conversationId: Long) = withContext(ioDispatcher) {
        context.messagesDB.getConversationMessagesFromRecycleBin(conversationId).forEach { message ->
            context.restoreMessageFromRecycleBin(message.id)
        }
        refreshConversation(conversationId)
    }

    private fun refreshConversation(conversationId: Long) {
        if (conversationId <= 0) return
        val conversation = context.conversationsDB.getConversation(conversationId) ?: return
        val date = context.messagesDB.getLatestConversationDate(conversationId) ?: 0
        if (date == 0) {
            context.conversationsDB.deleteConversation(conversationId)
        } else {
            val latest = context.messagesDB.getLatestConversationMessage(conversationId)
            context.conversationsDB.insertOrUpdate(conversation.copy(snippet = context.conversationSnippet(latest), date = date))
        }
    }
}
