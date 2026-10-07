package io.github.kylinlee.chatsim.domain.rule.user

import kotlinx.serialization.Serializable

/** 规则条件的作用对象。 */
@Serializable
enum class RuleObject {
    PHONE,
    ROLE,
    CONTENT,
    SMS_RECORD,
    CALL_RECORD;

    fun allowedOperators(): List<RuleOperator> = when (this) {
        PHONE, CONTENT -> listOf(
            RuleOperator.CONTAINS,
            RuleOperator.STARTS_WITH,
            RuleOperator.ENDS_WITH,
            RuleOperator.EQUALS,
        )

        ROLE -> listOf(RuleOperator.EQUALS)

        SMS_RECORD, CALL_RECORD -> listOf(
            RuleOperator.EQUALS,
            RuleOperator.BEFORE,
            RuleOperator.AFTER,
            RuleOperator.WITHIN_DAYS,
            RuleOperator.BEFORE_DAYS,
        )
    }
}

/** 规则条件操作符。 */
@Serializable
enum class RuleOperator {
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    EQUALS,
    BEFORE,
    AFTER,
    WITHIN_DAYS,
    BEFORE_DAYS;

    val isTimeOperator: Boolean
        get() = this == BEFORE || this == AFTER || isRelativeDaysOperator

    /** N 天以内的相对时间条件，值为负毫秒偏移。 */
    val isRelativeDaysOperator: Boolean
        get() = this == WITHIN_DAYS || this == BEFORE_DAYS
}

/** 条件之间的连接符。 */
@Serializable
enum class RuleConnector {
    AND,
    OR,
}

/** 记录类对象在「等于」下匹配全部状态的哨兵值，例如「全部短信」「全部通话」。 */
const val RULE_RECORD_ANY = "ANY"

/** 值输入类型，界面据此切换输入控件。 */
enum class RuleValueType {
    TEXT,
    ROLE,
    SMS_DIRECTION,
    CALL_KIND,
    TIMESTAMP,
    RELATIVE_DAYS,
}

fun RuleObject.valueType(op: RuleOperator): RuleValueType = when (this) {
    RuleObject.PHONE, RuleObject.CONTENT -> RuleValueType.TEXT
    RuleObject.ROLE -> RuleValueType.ROLE
    RuleObject.SMS_RECORD -> recordValueType(op) { RuleValueType.SMS_DIRECTION }
    RuleObject.CALL_RECORD -> recordValueType(op) { RuleValueType.CALL_KIND }
}

private inline fun RuleObject.recordValueType(
    op: RuleOperator,
    fallback: () -> RuleValueType,
): RuleValueType = when {
    op.isRelativeDaysOperator -> RuleValueType.RELATIVE_DAYS
    op.isTimeOperator -> RuleValueType.TIMESTAMP
    else -> fallback()
}

/** 单条 [对象-操作符-值] 条件，[connector] 连接下一条条件（最后一条忽略，未选择为 null）。 */
@Serializable
data class RuleCondition(
    val field: RuleObject,
    val operator: RuleOperator,
    val value: String,
    val connector: RuleConnector? = null,
)
