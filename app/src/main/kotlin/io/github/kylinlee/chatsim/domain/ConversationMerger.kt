package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import kotlin.math.abs

object ConversationMerger {
    fun merge(threads: List<ConversationThread>, calls: List<CallRecord>): List<ConversationThread> {
        if (calls.isEmpty()) return threads.sortedByDescending { it.date }

        val callsByKey = calls.groupBy { call -> call.role?.id to ContactFilter.normalizePhoneNumber(call.displayNumber) }
        val merged = mutableListOf<ConversationThread>()
        val matchedKeys = mutableSetOf<Pair<Long?, String>>()

        threads.forEach { thread ->
            val key = thread.roleId to ContactFilter.normalizePhoneNumber(thread.peerNumber)
            matchedKeys.add(key)

            val latestCall = callsByKey[key]?.maxByOrNull { it.startTS }
            merged.add(
                if (latestCall != null) {
                    thread.copy(lastCall = latestCall, date = maxOf(thread.date, latestCall.startTS.toLong()))
                } else {
                    thread
                }
            )
        }

        callsByKey.forEach { (key, numberCalls) ->
            val (roleId, peerNumber) = key
            if (peerNumber.isEmpty() || key in matchedKeys) return@forEach

            val latestCall = numberCalls.maxByOrNull { it.startTS } ?: return@forEach
            merged.add(
                ConversationThread(
                    conversationId = syntheticConversationId(roleId ?: 0L, peerNumber),
                    roleId = roleId ?: 0L,
                    role = latestCall.role,
                    peerNumber = latestCall.displayNumber,
                    title = latestCall.name,
                    photoUri = latestCall.photoUri,
                    snippet = "",
                    date = latestCall.startTS.toLong(),
                    read = true,
                    isGroupConversation = false,
                    isScheduled = false,
                    isPinned = false,
                    isAvailable = true,
                    lastCall = latestCall,
                    hasMessages = false,
                )
            )
        }

        return merged.sortedByDescending { it.date }
    }

    fun syntheticConversationId(roleId: Long, peerNumber: String): Long =
        -(abs(roleId.hashCode() * 31 + peerNumber.hashCode()).toLong() + 1L)
}
