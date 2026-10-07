package io.github.kylinlee.chatsim.data.rules

import android.content.Context
import io.github.kylinlee.chatsim.common.extensions.config
import io.github.kylinlee.chatsim.common.extensions.conversationSnippet
import io.github.kylinlee.chatsim.common.extensions.conversationsDB
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.common.extensions.moveMessageToRecycleBin
import io.github.kylinlee.chatsim.common.extensions.ruleHitsDB
import io.github.kylinlee.chatsim.domain.rule.user.RuleRecord
import io.github.kylinlee.chatsim.domain.rule.user.UserRule
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleEngine
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleOutcome

/** 把规则结果落到数据库：拦截快照、标签、垃圾箱与命中记录。 */
object UserRuleExecutor {
    fun evaluate(context: Context, record: RuleRecord): UserRuleOutcome =
        UserRuleStore.evaluate(context, record)

    /** 拦截：记录没有落库，只写快照到管理页。 */
    fun recordBlockedHits(context: Context, outcome: UserRuleOutcome, record: RuleRecord, title: String = "") {
        outcome.hits.forEach { rule ->
            UserRuleStore.recordHit(context, rule, record, messageId = 0, title = title)
        }
    }

    /** 已入库记录：打标签 + 移入垃圾箱 + 写命中（拦截对已入库记录不生效）。 */
    fun applyToStored(
        context: Context,
        messageId: Long,
        outcome: UserRuleOutcome,
        record: RuleRecord,
        title: String = "",
    ) {
        if (outcome.blocked || !outcome.hasActions) return

        if (outcome.trash && context.config.useRecycleBin) {
            val conversationId = context.messagesDB.getByIds(listOf(messageId)).firstOrNull()?.conversationId ?: 0L
            context.moveMessageToRecycleBin(messageId)
            refreshConversation(context, conversationId)
        }

        outcome.hits.forEach { rule ->
            if (rule.tags.isNotEmpty()) {
                UserRuleStore.addMessageTags(context, messageId, rule.tags, rule.id)
            }
            if (!UserRuleStore.hitExists(context, rule.id, messageId)) {
                UserRuleStore.recordHit(context, rule, record, messageId, title)
            }
        }
    }

    /** 手动运行：对已入库的短信与通话执行标签/垃圾箱动作，拦截规则跳过。返回处理条数。 */
    fun runAll(context: Context, rules: List<UserRule>): Int {
        val active = rules.filter { it.enabled && it.action != UserRuleAction.BLOCK }
        return runRules(context, active)
    }

    /** 手动运行单条规则（拦截规则不参与）。返回处理条数。 */
    fun runRule(context: Context, rule: UserRule): Int {
        if (!rule.enabled || rule.action == UserRuleAction.BLOCK) return 0
        return runRules(context, listOf(rule))
    }

    private fun runRules(context: Context, rules: List<UserRule>): Int {
        if (rules.isEmpty()) return 0

        // 运行即重建：先清空这些规则的历史命中，再按当前条件重记，避免陈旧与重复
        rules.forEach { context.ruleHitsDB.deleteForRule(it.id) }

        val roleByConversation = context.conversationsDB.getAllConversations()
            .associate { it.conversationId to it.roleId }

        val now = System.currentTimeMillis()
        var processed = 0
        context.messagesDB.getAll().chunked(200).forEach { chunk ->
            chunk.forEach { message ->
                val record = message.toRuleRecord(roleByConversation[message.conversationId])
                val outcome = UserRuleEngine.evaluate(record, rules, now)
                if (!outcome.hasActions) return@forEach

                applyToStored(context, message.id, outcome, record, message.senderName)
                processed++
            }
        }
        return processed
    }

    /** 移动/删除记录后重算会话摘要；没有剩余消息时删除会话。 */
    private fun refreshConversation(context: Context, conversationId: Long) {
        if (conversationId <= 0) return
        val conversation = context.conversationsDB.getConversation(conversationId) ?: return
        val date = context.messagesDB.getLatestConversationDate(conversationId) ?: 0
        if (date == 0) {
            context.conversationsDB.deleteConversation(conversationId)
        } else {
            val latest = context.messagesDB.getLatestConversationMessage(conversationId)
            context.conversationsDB.insertOrUpdate(
                conversation.copy(snippet = context.conversationSnippet(latest), date = date)
            )
        }
    }
}
