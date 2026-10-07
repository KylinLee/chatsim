package io.github.kylinlee.chatsim.domain.rule.builtin

import io.github.kylinlee.chatsim.domain.rule.CompiledRule
import io.github.kylinlee.chatsim.domain.rule.RuleAction
import io.github.kylinlee.chatsim.domain.rule.RuleContext
import io.github.kylinlee.chatsim.domain.rule.RuleEvent
import io.github.kylinlee.chatsim.domain.rule.RuleSource
import io.github.kylinlee.chatsim.domain.rule.RuleTrigger
import io.github.kylinlee.chatsim.domain.rule.ValueSource

data class RuleDefinition(
    val id: String,
    val name: String,
    val priority: Int,
    val triggers: Set<RuleTrigger>,
    val pattern: String? = null,
    val actions: List<RuleAction>,
    val matcher: ((RuleEvent, RuleContext) -> Boolean)? = null,
)

object RoleRules {
    const val ROLE_RESOLUTION_ID = "builtin.role.resolution"
    const val OUTGOING_ROUTE_ID = "builtin.role.outgoing"

    fun definitions(): List<RuleDefinition> = listOf(
        RuleDefinition(
            id = ROLE_RESOLUTION_ID,
            name = "Role resolution",
            priority = 150,
            triggers = setOf(
                RuleTrigger.SMS_IN,
                RuleTrigger.MMS_IN,
                RuleTrigger.CALL_IN,
                RuleTrigger.CALL_LOG,
            ),
            actions = listOf(
                RuleAction.AssignRole(
                    role = ValueSource.IncomingRoleId,
                    stripPrefix = true,
                )
            ),
        ),
        RuleDefinition(
            id = OUTGOING_ROUTE_ID,
            name = "Outgoing role route",
            priority = 150,
            triggers = setOf(RuleTrigger.MSG_OUT, RuleTrigger.CALL_OUT),
            actions = listOf(
                RuleAction.ApplyPrefix(ValueSource.RequestedRoleId),
                RuleAction.AssignSubscription(ValueSource.RequestedRoleSubscriptionId),
            ),
        ),
    )
}

class BuiltinRuleSource(
    private val definitions: List<RuleDefinition> = RoleRules.definitions() + BlacklistRules.definitions(),
) : RuleSource {
    private val compiled: List<CompiledRule> by lazy {
        definitions.map { definition ->
            CompiledRule(
                id = definition.id,
                name = definition.name,
                priority = definition.priority,
                triggers = definition.triggers,
                pattern = definition.pattern?.let { Regex(it) },
                actions = definition.actions,
                matcher = definition.matcher,
            )
        }
    }

    override fun rules(): List<CompiledRule> = compiled
}
