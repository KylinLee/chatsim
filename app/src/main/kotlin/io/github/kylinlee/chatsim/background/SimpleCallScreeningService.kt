package io.github.kylinlee.chatsim.background


import io.github.kylinlee.chatsim.common.*
import android.os.Build
import android.provider.CallLog
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import io.github.kylinlee.chatsim.common.extensions.config
import io.github.kylinlee.chatsim.common.extensions.normalizePhoneNumber
import io.github.kylinlee.chatsim.data.contacts.SimpleContactsHelper
import io.github.kylinlee.chatsim.data.rules.IncomingRuleExecutor
import io.github.kylinlee.chatsim.data.rules.UserRuleExecutor
import io.github.kylinlee.chatsim.domain.rule.IncomingKind
import io.github.kylinlee.chatsim.domain.rule.RuleDecision
import io.github.kylinlee.chatsim.domain.rule.user.CallKind
import io.github.kylinlee.chatsim.domain.rule.user.RecordKind
import io.github.kylinlee.chatsim.domain.rule.user.RuleRecord

@RequiresApi(Build.VERSION_CODES.N)
class SimpleCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        when {
            number != null && isBlocked(number, callDetails) -> {
                respondToCall(callDetails, isBlocked = true)
            }

            number != null && config.blockUnknownNumbers -> {
                val simpleContactsHelper = SimpleContactsHelper(this)
                simpleContactsHelper.exists(number) { exists ->
                    respondToCall(callDetails, isBlocked = !exists)
                }
            }

            number == null && config.blockHiddenNumbers -> {
                respondToCall(callDetails, isBlocked = true)
            }

            else -> {
                respondToCall(callDetails, isBlocked = false)
            }
        }
    }

    /** 统一入站判定：内置规则（黑名单）优先，其次是用户规则的拦截动作。 */
    private fun isBlocked(number: String, callDetails: Call.Details): Boolean {
        val subscriptionId = callDetails.accountHandle?.id?.toIntOrNull()
        val builtinOutcome = IncomingRuleExecutor.evaluateBuiltin(
            this,
            IncomingKind.CALL,
            number.normalizePhoneNumber(),
            subscriptionId,
        )
        if (builtinOutcome.decision == RuleDecision.BLOCK) return true

        val resolved = builtinOutcome.resolution
        val record = RuleRecord(
            address = resolved?.peerNumber ?: number,
            roleId = resolved?.role?.id,
            body = "",
            kind = RecordKind.CALL,
            callKind = CallKind.INCOMING,
            callType = CallLog.Calls.INCOMING_TYPE,
            timestamp = System.currentTimeMillis(),
        )
        val ruleOutcome = UserRuleExecutor.evaluate(this, record)
        if (!ruleOutcome.blocked) return false

        UserRuleExecutor.recordBlockedHits(this, ruleOutcome, record, resolved?.peerNumber ?: number)
        return true
    }

    private fun respondToCall(callDetails: Call.Details, isBlocked: Boolean) {
        val response = CallResponse.Builder()
            .setDisallowCall(isBlocked)
            .setRejectCall(isBlocked)
            .setSkipCallLog(isBlocked)
            .setSkipNotification(isBlocked)
            .build()

        respondToCall(callDetails, response)
    }
}
