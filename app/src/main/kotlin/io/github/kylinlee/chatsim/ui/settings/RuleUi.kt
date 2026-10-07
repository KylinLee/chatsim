package io.github.kylinlee.chatsim.ui.settings

import android.provider.CallLog
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.domain.rule.user.CallKind
import io.github.kylinlee.chatsim.domain.rule.user.RULE_RECORD_ANY
import io.github.kylinlee.chatsim.domain.rule.user.RuleCondition
import io.github.kylinlee.chatsim.domain.rule.user.RuleConnector
import io.github.kylinlee.chatsim.domain.rule.user.RuleObject
import io.github.kylinlee.chatsim.domain.rule.user.RuleOperator
import io.github.kylinlee.chatsim.domain.rule.user.SmsDirection
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import io.github.kylinlee.chatsim.ui.common.DAY_MILLIS
import io.github.kylinlee.chatsim.ui.common.formatDuration
import io.github.kylinlee.chatsim.ui.common.formatTimestamp
import java.util.Date

@Composable
fun ruleActionLabel(action: UserRuleAction): String = stringResource(
    when (action) {
        UserRuleAction.ADD_TAG -> R.string.rule_action_add_tag
        UserRuleAction.BLOCK -> R.string.rule_action_block
        UserRuleAction.TRASH -> R.string.rule_action_trash
    }
)

@Composable
fun ruleObjectLabel(ruleObject: RuleObject): String = stringResource(
    when (ruleObject) {
        RuleObject.PHONE -> R.string.rule_object_phone
        RuleObject.ROLE -> R.string.rule_object_role
        RuleObject.CONTENT -> R.string.rule_object_content
        RuleObject.SMS_RECORD -> R.string.rule_object_sms_record
        RuleObject.CALL_RECORD -> R.string.rule_object_call_record
    }
)

@Composable
fun ruleOperatorLabel(operator: RuleOperator): String = stringResource(
    when (operator) {
        RuleOperator.CONTAINS -> R.string.rule_operator_contains
        RuleOperator.STARTS_WITH -> R.string.rule_operator_starts_with
        RuleOperator.ENDS_WITH -> R.string.rule_operator_ends_with
        RuleOperator.EQUALS -> R.string.rule_operator_equals
        RuleOperator.BEFORE -> R.string.rule_operator_before
        RuleOperator.AFTER -> R.string.rule_operator_after
        RuleOperator.WITHIN_DAYS -> R.string.rule_operator_within_days
        RuleOperator.BEFORE_DAYS -> R.string.rule_operator_before_days
    }
)

@Composable
fun callKindLabel(kind: CallKind): String = stringResource(
    when (kind) {
        CallKind.INCOMING -> R.string.incoming_call
        CallKind.OUTGOING -> R.string.outgoing_call
        CallKind.MISSED -> R.string.missed_call
        CallKind.REJECTED -> R.string.rejected_call
        CallKind.BLOCKED -> R.string.blocked_call
        CallKind.VOICEMAIL -> R.string.voicemail_call
        CallKind.ANSWERED_EXTERNALLY -> R.string.answered_externally_call
        CallKind.NOT_CONNECTED -> R.string.call_not_connected
    }
)

/** 规则命中页通话记录的详细文案，例如「呼出电话 · 3:06」「呼入电话 · 已拒接」。 */
@Composable
fun callHitLabel(callType: Int, callDuration: Int, blocked: Boolean): String {
    val direction = stringResource(
        if (callType == CallLog.Calls.OUTGOING_TYPE) R.string.outgoing_call else R.string.call_direction_incoming
    )
    val detail = when {
        callDuration > 0 -> formatDuration(callDuration)
        callType == CallLog.Calls.MISSED_TYPE -> stringResource(R.string.call_status_missed)
        callType == CallLog.Calls.REJECTED_TYPE -> stringResource(R.string.rejected_call)
        callType == CallLog.Calls.BLOCKED_TYPE || blocked -> stringResource(R.string.blocked_call)
        callType == CallLog.Calls.VOICEMAIL_TYPE -> stringResource(R.string.voicemail_call)
        callType == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> stringResource(R.string.answered_externally_call)
        else -> stringResource(R.string.call_not_connected)
    }
    return "$direction · $detail"
}

@Composable
fun smsDirectionLabel(direction: SmsDirection): String = stringResource(
    when (direction) {
        SmsDirection.INBOX -> R.string.rule_sms_received
        SmsDirection.SENT -> R.string.rule_sms_sent
    }
)

@Composable
fun ruleConnectorLabel(connector: RuleConnector): String = stringResource(
    when (connector) {
        RuleConnector.AND -> R.string.rule_and
        RuleConnector.OR -> R.string.rule_or
    }
)

/**
 * 规则列表里的表达式摘要，例如「电话号码 包含 138 与 短信内容 开头字符为 验证码」。
 * 相对时间条件按操作符展示为「N 天以内」（大于）或「N 天以前」（小于）。
 */
@Composable
fun ruleExpressionSummary(conditions: List<RuleCondition>, roles: Map<Long, String>): String {
    if (conditions.isEmpty()) return ""

    return conditions.mapIndexed { index, condition ->
        val relativeMillis = condition.value.toLongOrNull()?.takeIf { it < 0 && condition.operator.isTimeOperator }
        val text = if (relativeMillis != null) {
            val days = -relativeMillis / DAY_MILLIS
            val before = condition.operator == RuleOperator.BEFORE || condition.operator == RuleOperator.BEFORE_DAYS
            val phrase = stringResource(
                if (before) R.string.rule_days_before else R.string.rule_days_within,
                days,
            )
            "${ruleObjectLabel(condition.field)} $phrase"
        } else {
            val value = when (condition.field) {
                RuleObject.ROLE -> roles[condition.value.toLongOrNull()] ?: condition.value
                RuleObject.SMS_RECORD -> when {
                    condition.value == RULE_RECORD_ANY -> stringResource(R.string.rule_record_any)
                    condition.operator.isTimeOperator -> formatTimeValue(condition.value)
                    else -> SmsDirection.entries.firstOrNull { it.name == condition.value }?.let { smsDirectionLabel(it) } ?: condition.value
                }

                RuleObject.CALL_RECORD -> when {
                    condition.value == RULE_RECORD_ANY -> stringResource(R.string.rule_record_any)
                    condition.operator.isTimeOperator -> formatTimeValue(condition.value)
                    else -> CallKind.entries.firstOrNull { it.name == condition.value }?.let { callKindLabel(it) } ?: condition.value
                }

                else -> condition.value
            }
            "${ruleObjectLabel(condition.field)} ${ruleOperatorLabel(condition.operator)} $value"
        }

        if (index < conditions.lastIndex) {
            "$text ${ruleConnectorLabel(conditions[index].connector ?: RuleConnector.AND)}"
        } else {
            text
        }
    }.joinToString(" ")
}

@Composable
private fun formatTimeValue(value: String): String {
    val millis = value.toLongOrNull() ?: return value
    val formatted = formatTimestamp(millis / 1000)
    return formatted.ifBlank { Date(millis).toString() }
}
