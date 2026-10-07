package io.github.kylinlee.chatsim.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "user_rule_tags",
    primaryKeys = ["rule_id", "tag"],
    indices = [Index(value = ["tag"])],
)
data class UserRuleTagEntity(
    @ColumnInfo(name = "rule_id") val ruleId: Long,
    @ColumnInfo(name = "tag") val tag: String,
)
