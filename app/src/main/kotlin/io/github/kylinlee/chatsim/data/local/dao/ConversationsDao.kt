package io.github.kylinlee.chatsim.data.local.dao

import androidx.room.*
import io.github.kylinlee.chatsim.data.model.Conversation
import io.github.kylinlee.chatsim.data.model.ConversationWithSnippetOverride

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: Conversation): Long

    @Query("SELECT (SELECT body FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL AND messages.conversation_id = conversations.conversation_id AND messages.is_call = 0 ORDER BY messages.date DESC LIMIT 1) as new_snippet, * FROM conversations")
    fun getAllWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAll(): List<Conversation> {
        return getAllWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT (SELECT body FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND messages.conversation_id = conversations.conversation_id AND messages.is_call = 0 ORDER BY messages.date DESC LIMIT 1) as new_snippet, * FROM conversations WHERE (SELECT COUNT(*) FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND messages.conversation_id = conversations.conversation_id) > 0")
    fun getAllWithMessagesInRecycleBinWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAllWithMessagesInRecycleBin(): List<Conversation> {
        return getAllWithMessagesInRecycleBinWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT * FROM conversations WHERE conversation_id = :conversationId")
    fun getConversation(conversationId: Long): Conversation?

    @Query("SELECT * FROM conversations")
    fun getAllConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE role_id = :roleId AND peer_number = :peerNumber LIMIT 1")
    fun getConversationByKey(roleId: Long, peerNumber: String): Conversation?

    @Query("SELECT * FROM conversations WHERE role_id = :roleId")
    fun getConversationsForRole(roleId: Long): List<Conversation>

    @Query("SELECT * FROM conversations WHERE read = 0 AND date > 0")
    fun getUnreadConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE title LIKE :text")
    fun getConversationsWithText(text: String): List<Conversation>

    @Query("UPDATE conversations SET read = 1 WHERE conversation_id = :conversationId")
    fun markRead(conversationId: Long)

    @Query("UPDATE conversations SET read = 0 WHERE conversation_id = :conversationId")
    fun markUnread(conversationId: Long)

    @Query("DELETE FROM conversations WHERE conversation_id = :conversationId")
    fun deleteConversation(conversationId: Long)
}
