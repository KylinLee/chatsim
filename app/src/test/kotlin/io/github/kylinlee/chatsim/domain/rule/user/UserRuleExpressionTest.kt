package io.github.kylinlee.chatsim.domain.rule.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRuleExpressionTest {
    @Test
    fun jsonRoundTripsSpecialCharacters() {
        val conditions = listOf(
            RuleCondition(RuleObject.PHONE, RuleOperator.CONTAINS, "138\"&|:{}", RuleConnector.AND),
            RuleCondition(RuleObject.CONTENT, RuleOperator.STARTS_WITH, "验证码\" \\ 😀", RuleConnector.OR),
            RuleCondition(RuleObject.CALL_RECORD, RuleOperator.AFTER, "1735689600000"),
        )

        assertEquals(conditions, UserRuleExpression.decode(UserRuleExpression.encode(conditions)))
    }

    @Test
    fun invalidJsonDecodesToNull() {
        assertNull(UserRuleExpression.decode("not json"))
        assertNull(UserRuleExpression.decode("[{\"field\":\"NOPE\"}]"))
    }

    @Test
    fun emptyConditionsNeverMatch() {
        assertFalse(UserRuleExpression.evaluate(emptyList(), record()))
    }

    @Test
    fun foldsLeftToRight() {
        // phone contains 138 AND content startsWith 验证码 OR call AFTER 1000
        val conditions = listOf(
            RuleCondition(RuleObject.PHONE, RuleOperator.CONTAINS, "138", RuleConnector.AND),
            RuleCondition(RuleObject.CONTENT, RuleOperator.STARTS_WITH, "验证码", RuleConnector.OR),
            RuleCondition(RuleObject.CALL_RECORD, RuleOperator.AFTER, "1000"),
        )

        val sms = record(address = "13800000000", body = "你好", timestamp = 500L)
        assertFalse(UserRuleExpression.evaluate(conditions, sms))

        val call = record(kind = RecordKind.CALL, timestamp = 2_000L)
        assertTrue(UserRuleExpression.evaluate(conditions, call))
    }

    @Test
    fun textOperatorsIgnoreCase() {
        val address = "13800000000"
        val body = "Verification code 1234"

        assertTrue(UserRuleExpression.evaluate(listOf(condition(RuleObject.PHONE, RuleOperator.CONTAINS, "8000")), record(address = address)))
        assertTrue(UserRuleExpression.evaluate(listOf(condition(RuleObject.PHONE, RuleOperator.STARTS_WITH, "138")), record(address = address)))
        assertTrue(UserRuleExpression.evaluate(listOf(condition(RuleObject.PHONE, RuleOperator.ENDS_WITH, "0000")), record(address = address)))
        assertTrue(UserRuleExpression.evaluate(listOf(condition(RuleObject.PHONE, RuleOperator.EQUALS, "13800000000")), record(address = address)))
        assertTrue(UserRuleExpression.evaluate(listOf(condition(RuleObject.CONTENT, RuleOperator.STARTS_WITH, "verification")), record(body = body)))
        assertFalse(UserRuleExpression.evaluate(listOf(condition(RuleObject.PHONE, RuleOperator.STARTS_WITH, "139")), record(address = address)))
    }

    @Test
    fun recordConditionsMatchDirectionKindAndTime() {
        val sms = record(kind = RecordKind.SMS, smsDirection = SmsDirection.INBOX, timestamp = 1_000L)
        val call = record(kind = RecordKind.CALL, callKind = CallKind.MISSED, timestamp = 5_000L)

        assertTrue(UserRuleExpression.evaluate(listOf(condition(RuleObject.SMS_RECORD, RuleOperator.EQUALS, "INBOX")), sms))
        assertFalse(UserRuleExpression.evaluate(listOf(condition(RuleObject.SMS_RECORD, RuleOperator.EQUALS, "SENT")), sms))
        assertTrue(UserRuleExpression.evaluate(listOf(condition(RuleObject.SMS_RECORD, RuleOperator.BEFORE, "2000")), sms))
        assertFalse(UserRuleExpression.evaluate(listOf(condition(RuleObject.SMS_RECORD, RuleOperator.AFTER, "2000")), sms))

        assertTrue(UserRuleExpression.evaluate(listOf(condition(RuleObject.CALL_RECORD, RuleOperator.EQUALS, "MISSED")), call))
        assertTrue(UserRuleExpression.evaluate(listOf(condition(RuleObject.CALL_RECORD, RuleOperator.AFTER, "4000")), call))
        assertFalse(UserRuleExpression.evaluate(listOf(condition(RuleObject.CALL_RECORD, RuleOperator.BEFORE, "4000")), call))

        // record kind mismatch never matches
        assertFalse(UserRuleExpression.evaluate(listOf(condition(RuleObject.CALL_RECORD, RuleOperator.EQUALS, "MISSED")), sms))
        assertFalse(UserRuleExpression.evaluate(listOf(condition(RuleObject.SMS_RECORD, RuleOperator.EQUALS, "INBOX")), call))
    }

    @Test
    fun anyRecordStatusMatchesAllDirectionsAndKinds() {
        val allSms = condition(RuleObject.SMS_RECORD, RuleOperator.EQUALS, RULE_RECORD_ANY)
        assertTrue(UserRuleExpression.evaluate(listOf(allSms), record(kind = RecordKind.SMS, smsDirection = SmsDirection.INBOX)))
        assertTrue(UserRuleExpression.evaluate(listOf(allSms), record(kind = RecordKind.SMS, smsDirection = SmsDirection.SENT)))
        assertFalse(UserRuleExpression.evaluate(listOf(allSms), record(kind = RecordKind.CALL, callKind = CallKind.MISSED)))

        val allCalls = condition(RuleObject.CALL_RECORD, RuleOperator.EQUALS, RULE_RECORD_ANY)
        assertTrue(UserRuleExpression.evaluate(listOf(allCalls), record(kind = RecordKind.CALL, callKind = CallKind.MISSED)))
        assertTrue(UserRuleExpression.evaluate(listOf(allCalls), record(kind = RecordKind.CALL, callKind = CallKind.OUTGOING)))
        assertFalse(UserRuleExpression.evaluate(listOf(allCalls), record(kind = RecordKind.SMS, smsDirection = SmsDirection.INBOX)))
    }

    @Test
    fun negativeTimeValueIsRelativeToNow() {
        val now = 2_000_000_000_000L
        val day = 86_400_000L
        val within7Days = condition(RuleObject.SMS_RECORD, RuleOperator.AFTER, (-(7 * day)).toString())
        val before30Days = condition(RuleObject.SMS_RECORD, RuleOperator.BEFORE, (-(30 * day)).toString())

        val recent = record(kind = RecordKind.SMS, timestamp = now - 3 * day)
        val old = record(kind = RecordKind.SMS, timestamp = now - 40 * day)

        assertTrue(UserRuleExpression.evaluate(listOf(within7Days), recent, now))
        assertFalse(UserRuleExpression.evaluate(listOf(within7Days), old, now))
        assertTrue(UserRuleExpression.evaluate(listOf(before30Days), old, now))
        assertFalse(UserRuleExpression.evaluate(listOf(before30Days), recent, now))

        // record kind mismatch never matches
        assertFalse(
            UserRuleExpression.evaluate(
                listOf(within7Days),
                record(kind = RecordKind.CALL, timestamp = now - day),
                now,
            )
        )

        // invalid time value never matches
        assertFalse(
            UserRuleExpression.evaluate(
                listOf(condition(RuleObject.SMS_RECORD, RuleOperator.AFTER, "not-a-number")),
                recent,
                now,
            )
        )
    }

    @Test
    fun relativeDaysOperatorsUseNow() {
        val now = 2_000_000_000_000L
        val day = 86_400_000L
        val within7Days = condition(RuleObject.SMS_RECORD, RuleOperator.WITHIN_DAYS, (-(7 * day)).toString())
        val before30Days = condition(RuleObject.CALL_RECORD, RuleOperator.BEFORE_DAYS, (-(30 * day)).toString())

        assertTrue(
            UserRuleExpression.evaluate(
                listOf(within7Days),
                record(kind = RecordKind.SMS, timestamp = now - 3 * day),
                now,
            )
        )
        assertFalse(
            UserRuleExpression.evaluate(
                listOf(within7Days),
                record(kind = RecordKind.SMS, timestamp = now - 10 * day),
                now,
            )
        )
        assertTrue(
            UserRuleExpression.evaluate(
                listOf(before30Days),
                record(kind = RecordKind.CALL, callKind = CallKind.MISSED, timestamp = now - 40 * day),
                now,
            )
        )
        assertFalse(
            UserRuleExpression.evaluate(
                listOf(before30Days),
                record(kind = RecordKind.CALL, callKind = CallKind.MISSED, timestamp = now - 10 * day),
                now,
            )
        )
    }

    @Test
    fun nullConnectorFoldsAsAnd() {
        val conditions = listOf(
            condition(RuleObject.PHONE, RuleOperator.CONTAINS, "138"),
            condition(RuleObject.PHONE, RuleOperator.CONTAINS, "999"),
        )

        assertFalse(UserRuleExpression.evaluate(conditions, record(address = "13800000000")))
    }

    @Test
    fun roleConditionRequiresResolvedRole() {
        val condition = condition(RuleObject.ROLE, RuleOperator.EQUALS, "7")

        assertTrue(UserRuleExpression.evaluate(listOf(condition), record(roleId = 7L)))
        assertFalse(UserRuleExpression.evaluate(listOf(condition), record(roleId = 8L)))
        assertFalse(UserRuleExpression.evaluate(listOf(condition), record(roleId = null)))
    }

    private fun condition(
        field: RuleObject,
        operator: RuleOperator,
        value: String,
    ) = RuleCondition(field, operator, value)

    private fun record(
        address: String = "13800000000",
        roleId: Long? = 1L,
        body: String = "",
        kind: RecordKind = RecordKind.SMS,
        smsDirection: SmsDirection? = SmsDirection.INBOX,
        callKind: CallKind? = null,
        timestamp: Long = 1_000L,
    ) = RuleRecord(
        address = address,
        roleId = roleId,
        body = body,
        kind = kind,
        smsDirection = smsDirection,
        callKind = callKind,
        timestamp = timestamp,
    )
}
