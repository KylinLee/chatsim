package io.github.kylinlee.chatsim.repository

import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import kotlinx.coroutines.flow.StateFlow

interface ConversationRepository {
    val conversations: StateFlow<List<ConversationThread>>

    suspend fun loadConversations(): List<ConversationThread>

    suspend fun syncWithSystem(): List<ConversationThread>

    suspend fun refresh(): List<ConversationThread>

    fun getCachedConversations(): List<ConversationThread>

    fun search(query: String): List<ConversationThread>

    suspend fun getConversation(conversationId: Long): ConversationThread?

    suspend fun findConversationId(roleId: Long, peerNumber: String): Long?

    suspend fun getOrCreateConversation(roleId: Long, peerNumber: String): Long

    /** The system provider thread used by a (role, peer) conversation. */
    suspend fun getSystemThreadId(roleId: Long, peerNumber: String): Long

    /** Persists the messages of a system thread into its (already resolved) conversation. */
    suspend fun persistSystemMessages(conversationId: Long, peerNumber: String, messages: List<Message>)

    /** Re-creates the conversations of a number from its system threads and re-imports their messages. Returns the restored conversation count. */
    suspend fun restoreConversations(peerNumber: String): Int

    suspend fun getRecycleBinConversations(): List<ConversationThread>

    suspend fun markRead(conversationId: Long)

    suspend fun markUnread(conversationId: Long)

    suspend fun deleteConversation(conversationId: Long)

    /** Recomputes the snippet/date of a conversation from its latest persisted message. */
    suspend fun refreshConversation(conversationId: Long)

    /** Applies a freshly persisted message to its conversation (snippet, date, read state). */
    suspend fun applyMessage(conversationId: Long, message: Message, markUnread: Boolean)

    fun pinConversation(conversationId: Long)

    fun unpinConversation(conversationId: Long)
}
