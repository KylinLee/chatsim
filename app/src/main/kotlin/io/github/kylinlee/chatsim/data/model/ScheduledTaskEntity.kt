package io.github.kylinlee.chatsim.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_tasks",
    indices = [
        Index(value = ["state"]),
        Index(value = ["trigger_at"]),
        Index(value = ["conversation_id"]),
    ],
)
data class ScheduledTaskEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "conversation_id") val conversationId: Long = 0,
    @ColumnInfo(name = "trigger_at") val triggerAt: Long,
    @ColumnInfo(name = "payload") val payload: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)
