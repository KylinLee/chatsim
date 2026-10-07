package io.github.kylinlee.chatsim.repository

import io.github.kylinlee.chatsim.data.model.RecentCall
import io.github.kylinlee.chatsim.domain.model.CallRecord
import kotlinx.coroutines.flow.StateFlow

interface CallLogRepository {
    val recentCalls: StateFlow<List<CallRecord>>

    /** Imports the system call log into the local database, resolving each call to its conversation. */
    suspend fun syncCalls(maxSize: Int = DEFAULT_MAX_SIZE)

    suspend fun loadRecentCalls(maxSize: Int = DEFAULT_MAX_SIZE): List<CallRecord>

    /** The calls of a conversation, read from the local database. */
    suspend fun getConversationCalls(conversationId: Long): List<CallRecord>

    suspend fun refresh(): List<CallRecord>

    fun getCachedCalls(): List<CallRecord>

    /** Drops the cached calls so the next access re-resolves roles from the call log. */
    fun invalidateCache()

    fun search(query: String): List<CallRecord>

    suspend fun removeCalls(ids: List<Int>)

    /** Removes every call log entry that belongs to the (role, peer number) conversation. */
    suspend fun removeCallsForConversation(roleId: Long, peerNumber: String)

    suspend fun removeAllCalls()

    suspend fun restoreCalls(calls: List<RecentCall>)

    suspend fun getCallsForExport(maxSize: Int = Int.MAX_VALUE): List<RecentCall>

    companion object {
        const val DEFAULT_MAX_SIZE = 200
    }
}
