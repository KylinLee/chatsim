package io.github.kylinlee.chatsim.data.repository

import io.github.kylinlee.chatsim.di.IoDispatcher
import io.github.kylinlee.chatsim.domain.TimelineMerger
import io.github.kylinlee.chatsim.domain.model.TimelineItem
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.repository.CallLogRepository
import io.github.kylinlee.chatsim.repository.MessageRepository
import io.github.kylinlee.chatsim.repository.TimelineRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineRepositoryImpl @Inject constructor(
    private val messageRepository: MessageRepository,
    private val callLogRepository: CallLogRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TimelineRepository {
    override suspend fun getTimeline(
        conversationId: Long,
        roleId: Long,
        peerNumber: String,
        includeRecycleBin: Boolean,
    ): List<TimelineItem> = withContext(ioDispatcher) {
        val messages = messageRepository.getConversationMessages(conversationId, includeRecycleBin)
        val threadCalls = callLogRepository.getConversationCalls(conversationId)

        TimelineMerger.merge(messages, threadCalls)
    }

    override suspend fun loadOlderMessages(
        threadId: Long,
        beforeDate: Int,
        limit: Int,
    ): List<Message> = withContext(ioDispatcher) {
        val messages = messageRepository.loadSystemMessages(
            threadId = threadId,
            getImageResolutions = false,
            dateFrom = beforeDate,
            includeScheduledMessages = false,
            limit = limit,
        )

        if (messages.isNotEmpty()) {
            messageRepository.persistMessages(messages)
        }

        messages.sortedBy { it.date }
    }
}
