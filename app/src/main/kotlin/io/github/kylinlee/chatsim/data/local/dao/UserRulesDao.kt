package io.github.kylinlee.chatsim.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.kylinlee.chatsim.data.model.UserRuleEntity
import io.github.kylinlee.chatsim.data.model.UserRuleTagEntity

@Dao
interface UserRulesDao {
    @Query("SELECT * FROM user_rules ORDER BY sort_order, id")
    fun getAll(): List<UserRuleEntity>

    @Query("SELECT * FROM user_rules WHERE id = :id")
    fun getById(id: Long): UserRuleEntity?

    @Query("SELECT COUNT(*) FROM user_rules WHERE action = :action")
    fun countByAction(action: String): Int

    @Insert
    fun insert(rule: UserRuleEntity): Long

    @Update
    fun update(rule: UserRuleEntity)

    @Query("UPDATE user_rules SET enabled = :enabled WHERE id = :id")
    fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE user_rules SET enabled = 0 WHERE id IN (SELECT rule_id FROM user_rule_tags WHERE tag = :tag)")
    fun disableRulesWithTag(tag: String)

    @Query("DELETE FROM user_rules WHERE id = :id")
    fun delete(id: Long)

    @Query("SELECT * FROM user_rule_tags")
    fun getAllRuleTags(): List<UserRuleTagEntity>

    @Query("DELETE FROM user_rule_tags WHERE rule_id = :ruleId")
    fun deleteRuleTags(ruleId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRuleTag(tag: UserRuleTagEntity)
}
