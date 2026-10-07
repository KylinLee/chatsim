package io.github.kylinlee.chatsim.domain.rule.builtin

import io.github.kylinlee.chatsim.domain.rule.RuleAction
import io.github.kylinlee.chatsim.domain.rule.RuleTrigger

/** 内置黑名单规则：命中系统拦截号码即拦截，优先于角色解析。 */
object BlacklistRules {
    const val BLACKLIST_ID = "builtin.blacklist"

    fun definitions(): List<RuleDefinition> = listOf(
        RuleDefinition(
            id = BLACKLIST_ID,
            name = "Blacklist",
            priority = 200,
            triggers = setOf(
                RuleTrigger.SMS_IN,
                RuleTrigger.MMS_IN,
                RuleTrigger.CALL_IN,
                RuleTrigger.CALL_LOG,
            ),
            actions = listOf(RuleAction.Block),
            matcher = { event, ctx -> ctx.isBlocked(event.address) },
        ),
    )
}
