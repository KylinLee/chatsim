package io.github.kylinlee.chatsim.repository

import io.github.kylinlee.chatsim.domain.model.TimelineItem
import io.github.kylinlee.chatsim.common.MESSAGES_LIMIT
import io.github.kylinlee.chatsim.data.model.Message

interface TimelineRepository {
    suspend fun getTimeline(
        conversationId: Long,
        roleId: Long,
        peerNumber: String,
        includeRecycleBin: Boolean = false,
    ): List<TimelineItem>

    suspend fun loadOlderMessages(
        threadId: Long,
        beforeDate: Int,
        limit: Int = MESSAGES_LIMIT,
    ): List<Message>
}
