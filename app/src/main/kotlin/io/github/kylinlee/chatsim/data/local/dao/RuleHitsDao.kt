package io.github.kylinlee.chatsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.kylinlee.chatsim.data.model.RuleHitEntity

@Dao
interface RuleHitsDao {
    @Insert
    fun insert(hit: RuleHitEntity): Long

    @Query("SELECT * FROM rule_hits WHERE action = :action ORDER BY record_date DESC")
    fun getByAction(action: String): List<RuleHitEntity>

    @Query("SELECT * FROM rule_hits WHERE action = :action AND rule_id = :ruleId ORDER BY record_date DESC")
    fun getByActionAndRule(action: String, ruleId: Long): List<RuleHitEntity>

    @Query("SELECT * FROM rule_hits WHERE id IN (:ids)")
    fun getByIds(ids: List<Long>): List<RuleHitEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM rule_hits WHERE rule_id = :ruleId AND message_id = :messageId)")
    fun exists(ruleId: Long, messageId: Long): Boolean

    @Query("DELETE FROM rule_hits WHERE rule_id = :ruleId")
    fun deleteForRule(ruleId: Long)

    @Query("DELETE FROM rule_hits WHERE id IN (:ids)")
    fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM rule_hits WHERE message_id IN (:messageIds)")
    fun deleteByMessageIds(messageIds: List<Long>)
}
