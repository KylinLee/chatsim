package io.github.kylinlee.chatsim.data.rules

import android.provider.CallLog
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.domain.rule.user.CallKind
import io.github.kylinlee.chatsim.domain.rule.user.RecordKind
import io.github.kylinlee.chatsim.domain.rule.user.RuleRecord
import io.github.kylinlee.chatsim.domain.rule.user.SmsDirection

/** 把本地消息/通话记录映射成规则求值用的统一记录。 */
fun Message.toRuleRecord(roleId: Long? = null): RuleRecord = RuleRecord(
    address = senderPhoneNumber,
    roleId = roleId,
    body = body,
    kind = if (isCall) RecordKind.CALL else RecordKind.SMS,
    smsDirection = if (isCall) null else if (isReceivedMessage()) SmsDirection.INBOX else SmsDirection.SENT,
    callKind = if (isCall) callKindOf(callType, callDuration) else null,
    callType = if (isCall) callType else 0,
    callDuration = if (isCall) callDuration else 0,
    timestamp = millis(),
)

fun callKindOf(type: Int, duration: Int): CallKind = when (type) {
    CallLog.Calls.MISSED_TYPE -> CallKind.MISSED
    CallLog.Calls.REJECTED_TYPE -> CallKind.REJECTED
    CallLog.Calls.BLOCKED_TYPE -> CallKind.BLOCKED
    CallLog.Calls.VOICEMAIL_TYPE -> CallKind.VOICEMAIL
    CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallKind.ANSWERED_EXTERNALLY
    CallLog.Calls.OUTGOING_TYPE -> if (duration > 0) CallKind.OUTGOING else CallKind.NOT_CONNECTED
    else -> if (duration > 0) CallKind.INCOMING else CallKind.NOT_CONNECTED
}
