package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.domain.model.ConversationThread

/**
 * 号码 → 最近会话时间。会话本身已包含短信与通话记录，[calls] 仅用于兼容联系人列表的旧口径。
 */
object RecentContactTimes {
    fun build(
        conversations: List<ConversationThread>,
        calls: List<CallRecord> = emptyList(),
    ): Map<String, Long> {
        val times = HashMap<String, Long>()
        conversations.forEach { thread ->
            if (thread.isGroupConversation) return@forEach
            record(times, thread.peerNumber, thread.date)
        }
        calls.forEach { call ->
            record(times, call.phoneNumber, call.startTS.toLong())
        }
        return times
    }

    fun latestForNumber(times: Map<String, Long>, number: String): Long {
        var latest = 0L
        ContactFilter.phoneKeys(number).forEach { key ->
            latest = maxOf(latest, times[key] ?: 0L)
        }
        return latest
    }

    private fun record(times: MutableMap<String, Long>, number: String, timestamp: Long) {
        if (timestamp <= 0) return

        ContactFilter.phoneKeys(number).forEach { key ->
            times[key] = maxOf(times[key] ?: 0L, timestamp)
        }
    }
}
