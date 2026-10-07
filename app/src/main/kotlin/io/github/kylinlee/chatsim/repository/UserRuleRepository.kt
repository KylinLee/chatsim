package io.github.kylinlee.chatsim.repository

import io.github.kylinlee.chatsim.data.model.RuleHitEntity
import io.github.kylinlee.chatsim.data.model.UserTagEntity
import io.github.kylinlee.chatsim.domain.rule.user.UserRule
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import kotlinx.coroutines.flow.StateFlow

interface UserRuleRepository {
    val rules: StateFlow<List<UserRule>>

    val tags: StateFlow<List<UserTagEntity>>

    suspend fun refresh()

    suspend fun saveRule(rule: UserRule): Long

    suspend fun setRuleEnabled(id: Long, enabled: Boolean)

    suspend fun deleteRule(id: Long)

    suspend fun deleteTag(name: String): Boolean

    suspend fun addTag(name: String)

    suspend fun trashRuleCount(): Int

    /** 手动运行所有启用规则（拦截除外），返回处理条数。 */
    suspend fun runRules(): Int

    /** 手动运行单条规则，返回处理条数。 */
    suspend fun runRule(id: Long): Int

    suspend fun hits(action: UserRuleAction): List<RuleHitEntity>

    /**
     * 先删除选中的命中记录，再删除仍存在的关联消息/通话记录（不存在的跳过）；
     * [toRecycleBin] 且启用回收站时移入回收站，否则直接删除。
     */
    suspend fun deleteRecords(hitIds: Set<Long>, toRecycleBin: Boolean)
}
