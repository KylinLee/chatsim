package io.github.kylinlee.chatsim.repository

import io.github.kylinlee.chatsim.common.MESSAGES_LIMIT
import io.github.kylinlee.chatsim.data.model.Message

interface MessageRepository {
    suspend fun getConversationMessages(conversationId: Long, includeRecycleBin: Boolean = false): List<Message>

    suspend fun getConversationMessagesFromRecycleBin(conversationId: Long): List<Message>

    suspend fun getMessagesWithText(query: String): List<Message>

    suspend fun getRecycleBinMessages(): List<Message>

    suspend fun getRecycleBinCount(): Int

    suspend fun loadSystemMessages(
        threadId: Long,
        getImageResolutions: Boolean = true,
        dateFrom: Int = -1,
        includeScheduledMessages: Boolean = true,
        limit: Int = MESSAGES_LIMIT,
    ): List<Message>

    suspend fun persistMessages(messages: List<Message>)

    suspend fun insertOrUpdate(message: Message)

    suspend fun markRead(id: Long, isMms: Boolean)

    suspend fun markConversationRead(conversationId: Long)

    suspend fun updateType(id: Long, type: Int)

    suspend fun updateStatus(id: Long, status: Int)

    suspend fun deleteMessage(message: Message)

    suspend fun deleteMessages(messages: List<Message>)

    suspend fun moveMessagesToRecycleBin(messages: List<Message>)

    suspend fun restoreMessage(id: Long)

    suspend fun restoreAllMessages(conversationId: Long)
}
