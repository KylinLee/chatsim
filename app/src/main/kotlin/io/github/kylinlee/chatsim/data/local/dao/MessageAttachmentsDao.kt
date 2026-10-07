package io.github.kylinlee.chatsim.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.kylinlee.chatsim.data.model.MessageAttachment

@Dao
interface MessageAttachmentsDao {
    @Query("SELECT * FROM message_attachments")
    fun getAll(): List<MessageAttachment>
}
