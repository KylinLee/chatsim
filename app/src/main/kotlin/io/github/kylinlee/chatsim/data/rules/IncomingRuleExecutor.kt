package io.github.kylinlee.chatsim.data.rules

import android.content.Context
import android.provider.CallLog
import io.github.kylinlee.chatsim.common.extensions.getBlockedNumbers
import io.github.kylinlee.chatsim.common.extensions.isNumberBlocked
import io.github.kylinlee.chatsim.data.local.SharedPrefsRoleStore
import io.github.kylinlee.chatsim.domain.rule.IncomingEvent
import io.github.kylinlee.chatsim.domain.rule.IncomingKind
import io.github.kylinlee.chatsim.domain.rule.IncomingOutcome
import io.github.kylinlee.chatsim.domain.rule.RoleRuleContext
import io.github.kylinlee.chatsim.domain.rule.RuleDecision
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import io.github.kylinlee.chatsim.domain.rule.user.CallKind
import io.github.kylinlee.chatsim.domain.rule.user.RecordKind
import io.github.kylinlee.chatsim.domain.rule.user.RuleRecord

/** 入站判定的统一入口：内置规则（黑名单 + 角色解析）先行，黑名单命中即拦截。 */
object IncomingRuleExecutor {
    fun evaluateBuiltin(
        context: Context,
        kind: IncomingKind,
        address: String,
        subscriptionId: Int?,
    ): IncomingOutcome {
        val roleStore = SharedPrefsRoleStore(context)
        val roles = roleStore.loadRoles()
        val activeRole = roles.firstOrNull { it.id == roleStore.loadActiveId() } ?: roles.firstOrNull()
        val blockedNumbers = context.getBlockedNumbers()

        return RuleEngines.engine.evaluateIncoming(
            IncomingEvent(kind, address, subscriptionId),
            RoleRuleContext(
                roles = roles,
                activeRoleId = activeRole?.id,
                fallbackRoleId = activeRole?.id,
                isBlocked = { number -> context.isNumberBlocked(number, blockedNumbers) },
            ),
        )
    }

    /** 是否被内置规则拦截；供只有号码的场景使用（如彩信下载回调）。 */
    fun isBlocked(
        context: Context,
        kind: IncomingKind,
        address: String,
        subscriptionId: Int? = null,
    ): Boolean = evaluateBuiltin(context, kind, address, subscriptionId).decision == RuleDecision.BLOCK

    /**
     * 内置黑名单 + 用户规则的完整拦截判定；供来电通知等无法走入库流程的场景使用。
     * 号码为空时不拦截。
     */
    fun isFullyBlocked(
        context: Context,
        kind: IncomingKind,
        address: String,
        subscriptionId: Int? = null,
    ): Boolean {
        if (address.isBlank()) return false

        val builtinOutcome = evaluateBuiltin(context, kind, address, subscriptionId)
        if (builtinOutcome.decision == RuleDecision.BLOCK) return true

        val resolved = builtinOutcome.resolution
        val isCall = kind == IncomingKind.CALL || kind == IncomingKind.CALL_LOG
        val record = RuleRecord(
            address = resolved?.peerNumber ?: address,
            roleId = resolved?.role?.id,
            body = "",
            kind = if (isCall) RecordKind.CALL else RecordKind.SMS,
            callKind = if (isCall) CallKind.INCOMING else null,
            callType = if (isCall) CallLog.Calls.INCOMING_TYPE else 0,
            timestamp = System.currentTimeMillis(),
        )
        return UserRuleExecutor.evaluate(context, record).blocked
    }
}
