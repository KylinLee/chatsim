package io.github.kylinlee.chatsim.domain.rule

import kotlinx.serialization.Serializable

@Serializable
sealed interface ValueSource {
    @Serializable
    data class Literal(val value: String) : ValueSource

    @Serializable
    data object IncomingRoleId : ValueSource

    @Serializable
    data object RequestedRoleId : ValueSource

    @Serializable
    data object RequestedRoleSubscriptionId : ValueSource
}

@Serializable
sealed interface RuleAction {
    @Serializable
    data class AssignRole(
        val role: ValueSource,
        val stripPrefix: Boolean = false,
    ) : RuleAction

    @Serializable
    data class ApplyPrefix(val role: ValueSource) : RuleAction

    @Serializable
    data class AssignSubscription(val subscriptionId: ValueSource) : RuleAction

    @Serializable
    data object Block : RuleAction
}
