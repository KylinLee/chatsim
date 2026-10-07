package io.github.kylinlee.chatsim.domain.rule

import io.github.kylinlee.chatsim.domain.OutgoingRoute
import io.github.kylinlee.chatsim.domain.ResolvedNumber

enum class RuleDecision {
    ALLOW,
    BLOCK,
    SILENT,
}

data class RuleHit(
    val ruleId: String,
    val ruleName: String,
    val error: String? = null,
)

data class IncomingOutcome(
    val decision: RuleDecision = RuleDecision.ALLOW,
    val resolution: ResolvedNumber? = null,
    val tags: Set<String> = emptySet(),
    val hits: List<RuleHit> = emptyList(),
)

data class OutgoingOutcome(
    val number: String,
    val subscriptionId: Int?,
    val roleId: Long?,
    val tags: Set<String> = emptySet(),
    val hits: List<RuleHit> = emptyList(),
)

fun OutgoingOutcome.toRoute(): OutgoingRoute = OutgoingRoute(number, subscriptionId, roleId)
