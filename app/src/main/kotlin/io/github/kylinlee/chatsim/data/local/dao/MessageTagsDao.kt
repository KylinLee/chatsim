package io.github.kylinlee.chatsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import io.github.kylinlee.chatsim.data.model.MessageTagEntity

@Dao
interface MessageTagsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tags: List<MessageTagEntity>)
}
