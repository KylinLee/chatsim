package io.github.kylinlee.chatsim.domain.rule.user

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 用户规则的表达式：条件列表以 JSON 存储（值由序列化器转义，用户输入无法破坏结构），
 * 求值按从左到右折叠并短路。
 */
object UserRuleExpression {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(conditions: List<RuleCondition>): String = json.encodeToString(conditions)

    fun decode(expression: String): List<RuleCondition>? =
        runCatching { json.decodeFromString<List<RuleCondition>>(expression) }.getOrNull()

    fun evaluate(
        conditions: List<RuleCondition>,
        record: RuleRecord,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (conditions.isEmpty()) return false

        var result = matches(conditions.first(), record, now)
        for (index in 1 until conditions.size) {
            val connector = conditions[index - 1].connector ?: RuleConnector.AND
            if (connector == RuleConnector.AND && !result) continue
            if (connector == RuleConnector.OR && result) continue

            val next = matches(conditions[index], record, now)
            result = if (connector == RuleConnector.AND) result && next else result || next
        }
        return result
    }

    fun matches(
        condition: RuleCondition,
        record: RuleRecord,
        now: Long = System.currentTimeMillis(),
    ): Boolean = when (condition.field) {
        RuleObject.PHONE -> textMatches(record.address, condition)

        RuleObject.ROLE -> condition.operator == RuleOperator.EQUALS &&
            record.roleId != null &&
            record.roleId == condition.value.toLongOrNull()

        RuleObject.CONTENT -> textMatches(record.body, condition)

        RuleObject.SMS_RECORD -> when (condition.operator) {
            RuleOperator.EQUALS -> record.kind == RecordKind.SMS &&
                (condition.value == RULE_RECORD_ANY || record.smsDirection?.name == condition.value)

            RuleOperator.BEFORE, RuleOperator.BEFORE_DAYS ->
                record.kind == RecordKind.SMS && timeBefore(condition, record.timestamp, now)

            RuleOperator.AFTER, RuleOperator.WITHIN_DAYS ->
                record.kind == RecordKind.SMS && timeAfter(condition, record.timestamp, now)

            else -> false
        }

        RuleObject.CALL_RECORD -> when (condition.operator) {
            RuleOperator.EQUALS -> record.kind == RecordKind.CALL &&
                (condition.value == RULE_RECORD_ANY || record.callKind?.name == condition.value)

            RuleOperator.BEFORE, RuleOperator.BEFORE_DAYS ->
                record.kind == RecordKind.CALL && timeBefore(condition, record.timestamp, now)

            RuleOperator.AFTER, RuleOperator.WITHIN_DAYS ->
                record.kind == RecordKind.CALL && timeAfter(condition, record.timestamp, now)

            else -> false
        }
    }

    private fun textMatches(text: String, condition: RuleCondition): Boolean = when (condition.operator) {
        RuleOperator.CONTAINS -> text.contains(condition.value, ignoreCase = true)
        RuleOperator.STARTS_WITH -> text.startsWith(condition.value, ignoreCase = true)
        RuleOperator.ENDS_WITH -> text.endsWith(condition.value, ignoreCase = true)
        RuleOperator.EQUALS -> text.equals(condition.value, ignoreCase = true)
        else -> false
    }

    private fun timeBefore(condition: RuleCondition, timestamp: Long, now: Long): Boolean {
        val target = condition.timeValue(now) ?: return false
        return timestamp < target
    }

    private fun timeAfter(condition: RuleCondition, timestamp: Long, now: Long): Boolean {
        val target = condition.timeValue(now) ?: return false
        return timestamp > target
    }

    /** 时间值：负数表示相对求值时刻的偏移（如 -604800000 = 7 天前），非负为绝对毫秒时间戳。 */
    private fun RuleCondition.timeValue(now: Long): Long? {
        val millis = value.toLongOrNull() ?: return null
        return if (millis < 0) now + millis else millis
    }
}
