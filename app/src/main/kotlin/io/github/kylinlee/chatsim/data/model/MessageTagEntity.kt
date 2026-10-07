package io.github.kylinlee.chatsim.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "message_tags",
    primaryKeys = ["message_id", "tag"],
    indices = [Index(value = ["message_id"]), Index(value = ["tag"])],
)
data class MessageTagEntity(
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "tag") val tag: String,
    @ColumnInfo(name = "rule_id") val ruleId: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
