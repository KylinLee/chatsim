package io.github.kylinlee.chatsim.domain.rule.user

/** 规则求值用的统一记录视图：短信/彩信与通话都映射到这里。 */
data class RuleRecord(
    val address: String,
    val roleId: Long?,
    val body: String,
    val kind: RecordKind,
    val smsDirection: SmsDirection? = null,
    val callKind: CallKind? = null,
    val callType: Int = 0,
    val callDuration: Int = 0,
    val timestamp: Long,
)

enum class RecordKind {
    SMS,
    CALL,
}

enum class SmsDirection {
    INBOX,
    SENT,
}

enum class CallKind {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    VOICEMAIL,
    ANSWERED_EXTERNALLY,
    NOT_CONNECTED,
}
