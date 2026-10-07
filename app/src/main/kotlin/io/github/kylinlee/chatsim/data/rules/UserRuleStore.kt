package io.github.kylinlee.chatsim.data.rules

import android.content.Context
import io.github.kylinlee.chatsim.common.extensions.messageTagsDB
import io.github.kylinlee.chatsim.common.extensions.ruleHitsDB
import io.github.kylinlee.chatsim.common.extensions.userRulesDB
import io.github.kylinlee.chatsim.common.extensions.userTagsDB
import io.github.kylinlee.chatsim.data.model.MessageTagEntity
import io.github.kylinlee.chatsim.data.model.RuleHitEntity
import io.github.kylinlee.chatsim.data.model.UserRuleEntity
import io.github.kylinlee.chatsim.data.model.UserRuleTagEntity
import io.github.kylinlee.chatsim.data.model.UserTagEntity
import io.github.kylinlee.chatsim.domain.rule.user.BuiltinTags
import io.github.kylinlee.chatsim.domain.rule.user.RuleRecord
import io.github.kylinlee.chatsim.domain.rule.user.UserRule
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleEngine
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleExpression
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleOutcome

/** 用户规则的同步存取与内存缓存；无 Hilt 的 Receiver 也直接使用。 */
object UserRuleStore {
    @Volatile
    private var cache: List<UserRule>? = null

    fun rules(context: Context): List<UserRule> =
        cache ?: synchronized(this) {
            cache ?: load(context).also { cache = it }
        }

    fun reload(context: Context): List<UserRule> = synchronized(this) {
        load(context).also { cache = it }
    }

    fun invalidate() {
        cache = null
    }

    fun evaluate(context: Context, record: RuleRecord): UserRuleOutcome =
        UserRuleEngine.evaluate(record, rules(context))

    fun save(context: Context, rule: UserRule): Long {
        val dao = context.userRulesDB
        val old = if (rule.id > 0) dao.getById(rule.id) else null
        val entity = rule.toEntity()
        val id = if (rule.id > 0) {
            dao.update(entity)
            rule.id
        } else {
            dao.insert(entity)
        }

        if (old != null && (old.expression != entity.expression || old.action != entity.action)) {
            context.ruleHitsDB.deleteForRule(id)
        }

        dao.deleteRuleTags(id)
        rule.tags.forEach { tag -> dao.insertRuleTag(UserRuleTagEntity(id, tag)) }
        ensureTags(context, rule.tags)
        invalidate()
        return id
    }

    fun setEnabled(context: Context, id: Long, enabled: Boolean) {
        context.userRulesDB.setEnabled(id, enabled)
        invalidate()
    }

    fun delete(context: Context, id: Long) {
        context.userRulesDB.delete(id)
        context.userRulesDB.deleteRuleTags(id)
        context.ruleHitsDB.deleteForRule(id)
        invalidate()
    }

    fun ensureTags(context: Context, tags: List<String>) {
        val dao = context.userTagsDB
        tags.forEach { tag ->
            if (tag.isNotBlank() && !dao.exists(tag)) {
                dao.insert(UserTagEntity(name = tag, isBuiltin = BuiltinTags.isBuiltin(tag)))
            }
        }
    }

    /** 删除自定义标签；引用它的规则自动停用。内置标签不可删。 */
    fun deleteTag(context: Context, name: String): Boolean {
        if (BuiltinTags.isBuiltin(name)) return false
        if (context.userTagsDB.deleteCustom(name) <= 0) return false

        context.userRulesDB.disableRulesWithTag(name)
        invalidate()
        return true
    }

    fun countByAction(context: Context, action: UserRuleAction): Int =
        context.userRulesDB.countByAction(action.name)

    fun addMessageTags(context: Context, messageId: Long, tags: List<String>, ruleId: Long) {
        if (tags.isEmpty()) return
        context.messageTagsDB.insertAll(tags.map { MessageTagEntity(messageId, it, ruleId) })
    }

    fun recordHit(
        context: Context,
        rule: UserRule,
        record: RuleRecord,
        messageId: Long = 0,
        title: String = "",
    ) {
        context.ruleHitsDB.insert(
            RuleHitEntity(
                ruleId = rule.id,
                action = rule.action.name,
                recordKind = record.kind.name,
                callType = record.callType,
                callDuration = record.callDuration,
                messageId = messageId,
                address = record.address,
                title = title,
                body = record.body,
                callKind = record.callKind?.name,
                recordDate = record.timestamp,
            )
        )
    }

    fun hits(context: Context, action: UserRuleAction): List<RuleHitEntity> =
        context.ruleHitsDB.getByAction(action.name)

    /** 0 表示没有关联记录（拦截快照），不参与判重；通话记录为负 id，需正常判重。 */
    fun hitExists(context: Context, ruleId: Long, messageId: Long): Boolean =
        messageId != 0L && context.ruleHitsDB.exists(ruleId, messageId)

    private fun load(context: Context): List<UserRule> {
        val tagsByRule = context.userRulesDB.getAllRuleTags().groupBy({ it.ruleId }, { it.tag })
        return context.userRulesDB.getAll().map { entity ->
            UserRule(
                id = entity.id,
                name = entity.name,
                action = runCatching { UserRuleAction.valueOf(entity.action) }.getOrDefault(UserRuleAction.ADD_TAG),
                tags = tagsByRule[entity.id].orEmpty(),
                conditions = UserRuleExpression.decode(entity.expression).orEmpty(),
                enabled = entity.enabled,
                sortOrder = entity.sortOrder,
            )
        }
    }

    private fun UserRule.toEntity(): UserRuleEntity = UserRuleEntity(
        id = id,
        name = name,
        action = action.name,
        expression = UserRuleExpression.encode(conditions),
        enabled = enabled,
        sortOrder = sortOrder,
    )
}
