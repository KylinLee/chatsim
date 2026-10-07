package io.github.kylinlee.chatsim.data.local.dao

import androidx.room.*
import io.github.kylinlee.chatsim.data.model.RecycleBinMessage
import io.github.kylinlee.chatsim.data.model.Message

@Dao
interface MessagesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecycleBinEntry(recycleBinMessage: RecycleBinMessage)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(vararg message: Message)

    @Query("SELECT * FROM messages")
    fun getAll(): List<Message>

    @Query("SELECT id FROM messages WHERE id IN (:ids)")
    fun getExistingIds(ids: List<Long>): List<Long>

    @Query("SELECT * FROM messages WHERE id IN (:ids)")
    fun getByIds(ids: List<Long>): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL")
    fun getAllRecycleBinMessages(): List<Message>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId AND is_call = 0")
    fun getConversationMessages(conversationId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE is_call = 1 ORDER BY date DESC")
    fun getAllCalls(): List<Message>

    @Query("SELECT * FROM messages WHERE is_call = 1 AND conversation_id = :conversationId ORDER BY date DESC")
    fun getConversationCalls(conversationId: Long): List<Message>

    @Query("DELETE FROM messages WHERE is_call = 1")
    fun deleteAllCalls()

    @Query("SELECT id FROM messages WHERE is_call = 1")
    fun getCallIds(): List<Long>

    @Query("SELECT DISTINCT conversation_id FROM messages WHERE is_call = 1")
    fun getCallConversationIds(): List<Long>

    @Query("SELECT MAX(date) FROM messages WHERE conversation_id = :conversationId")
    fun getLatestConversationDate(conversationId: Long): Int?

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL AND conversation_id = :conversationId AND is_call = 0")
    fun getNonRecycledConversationMessages(conversationId: Long): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND conversation_id = :conversationId AND is_call = 0")
    fun getConversationMessagesFromRecycleBin(conversationId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY date DESC LIMIT 1")
    fun getLatestConversationMessage(conversationId: Long): Message?

    @Query("SELECT DISTINCT thread_id FROM messages WHERE conversation_id = :conversationId AND is_call = 0")
    fun getConversationThreadIds(conversationId: Long): List<Long>

    @Query("SELECT id FROM messages WHERE conversation_id = :conversationId AND is_call = 0")
    fun getConversationMessageIds(conversationId: Long): List<Long>

    @Query("SELECT DISTINCT conversation_id FROM messages WHERE is_scheduled = 1")
    fun getScheduledConversationIds(): List<Long>

    @Query("SELECT COUNT(*) FROM recycle_bin_messages")
    fun getRecycleBinCount(): Int

    @Query("SELECT * FROM messages WHERE body LIKE :text")
    fun getMessagesWithText(text: String): List<Message>

    @Query("UPDATE messages SET read = 1 WHERE id = :id")
    fun markRead(id: Long)

    @Query("UPDATE messages SET read = 1 WHERE conversation_id = :conversationId")
    fun markConversationRead(conversationId: Long)

    @Query("UPDATE messages SET type = :type WHERE id = :id")
    fun updateType(id: Long, type: Int): Int

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    fun updateStatus(id: Long, status: Int): Int

    @Query("UPDATE messages SET delivery_status = :status WHERE id = :id")
    fun updateDeliveryStatus(id: Long, status: Int): Int

    @Transaction
    fun delete(id: Long) {
        deleteFromMessages(id)
        deleteFromRecycleBin(id)
    }

    @Query("DELETE FROM messages WHERE id = :id")
    fun deleteFromMessages(id: Long)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    fun deleteFromMessages(ids: List<Long>)

    @Query("DELETE FROM recycle_bin_messages WHERE id = :id")
    fun deleteFromRecycleBin(id: Long)

    @Transaction
    fun deleteConversationMessages(conversationId: Long) {
        deleteConversationMessagesFromRecycleBin(conversationId)
        deleteAllConversationMessages(conversationId)
    }

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    fun deleteAllConversationMessages(conversationId: Long)

    @Query("DELETE FROM recycle_bin_messages WHERE id IN (SELECT id FROM messages WHERE conversation_id = :conversationId)")
    fun deleteConversationMessagesFromRecycleBin(conversationId: Long)

    @Query("DELETE FROM messages")
    fun deleteAll()
}
