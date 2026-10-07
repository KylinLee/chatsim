package io.github.kylinlee.chatsim.domain.rule

import io.github.kylinlee.chatsim.domain.ResolvedNumber
import io.github.kylinlee.chatsim.domain.RolePrefixer
import io.github.kylinlee.chatsim.domain.RoleResolver

class RuleEngine(
    private val source: RuleSource,
) {
    fun evaluateIncoming(event: IncomingEvent, ctx: RuleContext): IncomingOutcome {
        var resolution: ResolvedNumber? = null
        var decision = RuleDecision.ALLOW
        val hits = mutableListOf<RuleHit>()

        matchingRules(event).forEach { rule ->
            if (!matches(rule, event, ctx)) return@forEach

            rule.actions.forEach { action ->
                when (action) {
                    is RuleAction.Block -> {
                        if (decision != RuleDecision.BLOCK) {
                            decision = RuleDecision.BLOCK
                            hits.add(RuleHit(rule.id, rule.name))
                        }
                    }

                    is RuleAction.AssignRole -> {
                        if (resolution != null) return@forEach
                        val roleId = evalValue(action.role, event, ctx)?.toLongValue() ?: return@forEach
                        val role = ctx.roleById(roleId) ?: return@forEach
                        val peerNumber = if (action.stripPrefix) {
                            RolePrefixer.stripPrefix(event.address, role, event.channel)
                        } else {
                            event.address
                        }
                        resolution = ResolvedNumber(peerNumber, role)
                    }

                    else -> Unit
                }
            }
        }

        return IncomingOutcome(decision = decision, resolution = resolution, hits = hits)
    }

    fun routeOutgoing(event: OutgoingEvent, ctx: RuleContext): OutgoingOutcome {
        var number = event.address
        var subscriptionId = event.subscriptionId
        var roleId: Long? = null
        var subscriptionAssigned = false

        matchingRules(event).forEach { rule ->
            if (roleId != null && subscriptionAssigned) return@forEach
            if (!matches(rule, event, ctx)) return@forEach

            rule.actions.forEach { action ->
                when (action) {
                    is RuleAction.ApplyPrefix -> {
                        if (roleId != null) return@forEach
                        val resolvedRoleId = evalValue(action.role, event, ctx)?.toLongValue() ?: return@forEach
                        val role = ctx.roleById(resolvedRoleId) ?: return@forEach
                        if (!RolePrefixer.isAlreadyPrefixed(number, ctx.roles, event.channel)) {
                            number = RolePrefixer.applyPrefix(number, role, event.channel)
                        }
                        roleId = role.id
                    }

                    is RuleAction.AssignSubscription -> {
                        if (subscriptionAssigned) return@forEach
                        val resolvedSubscriptionId = evalValue(action.subscriptionId, event, ctx)?.toIntValue()
                            ?: return@forEach
                        subscriptionId = resolvedSubscriptionId
                        subscriptionAssigned = true
                    }

                    else -> Unit
                }
            }
        }

        return OutgoingOutcome(number = number, subscriptionId = subscriptionId, roleId = roleId)
    }

    private fun matchingRules(event: RuleEvent): List<CompiledRule> =
        source.rules()
            .filter { rule -> rule.triggers.any { it.accepts(event) } }
            .sortedWith(compareByDescending<CompiledRule> { it.priority }.thenBy { it.id })

    private fun matches(rule: CompiledRule, event: RuleEvent, ctx: RuleContext): Boolean =
        (rule.pattern?.containsMatchIn(event.address) ?: true) &&
            (rule.matcher?.invoke(event, ctx) ?: true)

    private fun evalValue(value: ValueSource, event: RuleEvent, ctx: RuleContext): Any? = when (value) {
        is ValueSource.Literal -> value.value

        ValueSource.IncomingRoleId -> (event as? IncomingEvent)?.let { incoming ->
            RoleResolver.resolveIncoming(
                channel = incoming.channel,
                address = incoming.address,
                subscriptionId = incoming.subscriptionId,
                roles = ctx.roles,
                fallbackRoleId = ctx.fallbackRoleId,
            )?.role?.id
        }

        ValueSource.RequestedRoleId -> (event as? OutgoingEvent)?.requestedRoleId

        ValueSource.RequestedRoleSubscriptionId -> (event as? OutgoingEvent)?.requestedRoleId
            ?.let { requestedRoleId -> ctx.roles.firstOrNull { it.id == requestedRoleId }?.subscriptionId }
    }

    private fun Any.toLongValue(): Long? = when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }

    private fun Any.toIntValue(): Int? = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }
}
