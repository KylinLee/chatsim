package io.github.kylinlee.chatsim.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import io.github.kylinlee.chatsim.data.model.Attachment

@Dao
interface AttachmentsDao {
    @Query("SELECT * FROM attachments")
    fun getAll(): List<Attachment>
}
