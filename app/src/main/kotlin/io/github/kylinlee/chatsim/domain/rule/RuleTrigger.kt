package io.github.kylinlee.chatsim.domain.rule

import io.github.kylinlee.chatsim.domain.model.RoleChannel

enum class RuleTrigger {
    SMS_IN,
    MMS_IN,
    CALL_IN,
    CALL_LOG,
    MSG_OUT,
    CALL_OUT;

    fun accepts(event: RuleEvent): Boolean = when (this) {
        SMS_IN -> event is IncomingEvent && event.kind == IncomingKind.SMS
        MMS_IN -> event is IncomingEvent && event.kind == IncomingKind.MMS
        CALL_IN -> event is IncomingEvent && event.kind == IncomingKind.CALL
        CALL_LOG -> event is IncomingEvent && event.kind == IncomingKind.CALL_LOG
        MSG_OUT -> event is OutgoingEvent && event.channel == RoleChannel.SMS
        CALL_OUT -> event is OutgoingEvent && event.channel == RoleChannel.CALL
    }
}
