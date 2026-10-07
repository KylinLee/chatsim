package io.github.kylinlee.chatsim.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 规则命中记录；拦截的记录没有落库，用快照字段展示。 */
@Entity(
    tableName = "rule_hits",
    indices = [Index(value = ["action"]), Index(value = ["rule_id"])],
)
data class RuleHitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "rule_id") val ruleId: Long,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "record_kind") val recordKind: String,
    @ColumnInfo(name = "call_type", defaultValue = "0") val callType: Int = 0,
    @ColumnInfo(name = "call_duration", defaultValue = "0") val callDuration: Int = 0,
    @ColumnInfo(name = "message_id") val messageId: Long = 0,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "call_kind") val callKind: String? = null,
    @ColumnInfo(name = "record_date") val recordDate: Long,
    @ColumnInfo(name = "hit_at") val hitAt: Long = System.currentTimeMillis(),
)
