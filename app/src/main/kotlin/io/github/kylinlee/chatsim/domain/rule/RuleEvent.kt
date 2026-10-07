package io.github.kylinlee.chatsim.domain.rule

import io.github.kylinlee.chatsim.domain.model.RoleChannel

sealed interface RuleEvent {
    val channel: RoleChannel
    val address: String
    val subscriptionId: Int?
    val timestamp: Long
}

enum class IncomingKind {
    SMS,
    MMS,
    CALL,
    CALL_LOG,
}

data class IncomingEvent(
    val kind: IncomingKind,
    override val address: String,
    override val subscriptionId: Int?,
    override val timestamp: Long = System.currentTimeMillis(),
) : RuleEvent {
    override val channel: RoleChannel
        get() = when (kind) {
            IncomingKind.SMS, IncomingKind.MMS -> RoleChannel.SMS
            IncomingKind.CALL, IncomingKind.CALL_LOG -> RoleChannel.CALL
        }
}

data class OutgoingEvent(
    override val address: String,
    override val channel: RoleChannel,
    val requestedRoleId: Long?,
    override val subscriptionId: Int?,
    override val timestamp: Long = System.currentTimeMillis(),
) : RuleEvent
