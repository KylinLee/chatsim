package io.github.kylinlee.chatsim.domain.rule.user

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRuleEngineTest {
    @Test
    fun blockWinsAndTagsAreStillCollected() {
        val rules = listOf(
            rule(id = 1, action = UserRuleAction.TRASH, tags = listOf("验证码")),
            rule(id = 2, action = UserRuleAction.BLOCK, tags = listOf("垃圾")),
            rule(id = 3, action = UserRuleAction.ADD_TAG, tags = listOf("重要")),
        )

        val outcome = UserRuleEngine.evaluate(record(), rules)

        assertTrue(outcome.blocked)
        assertFalse(outcome.trash)
        assertEquals(listOf("验证码", "垃圾", "重要"), outcome.tags)
        assertEquals(listOf(1L, 2L, 3L), outcome.hits.map { it.id })
    }

    @Test
    fun tagsAccumulateAcrossRules() {
        val rules = listOf(
            rule(id = 1, action = UserRuleAction.ADD_TAG, tags = listOf("A")),
            rule(id = 2, action = UserRuleAction.ADD_TAG, tags = listOf("B", "A")),
        )

        val outcome = UserRuleEngine.evaluate(record(), rules)

        assertFalse(outcome.blocked)
        assertFalse(outcome.trash)
        assertEquals(listOf("A", "B"), outcome.tags)
    }

    @Test
    fun trashAppliesWithoutBlock() {
        val outcome = UserRuleEngine.evaluate(record(), listOf(rule(id = 1, action = UserRuleAction.TRASH)))

        assertFalse(outcome.blocked)
        assertTrue(outcome.trash)
        assertTrue(outcome.hasActions)
    }

    @Test
    fun disabledAndUnmatchedRulesAreIgnored() {
        val rules = listOf(
            rule(id = 1, action = UserRuleAction.BLOCK, enabled = false),
            rule(id = 2, action = UserRuleAction.BLOCK, conditions = listOf(condition(RuleObject.PHONE, RuleOperator.STARTS_WITH, "139"))),
        )

        val outcome = UserRuleEngine.evaluate(record(), rules)

        assertFalse(outcome.blocked)
        assertFalse(outcome.hasActions)
        assertTrue(outcome.hits.isEmpty())
    }

    private fun rule(
        id: Long,
        action: UserRuleAction,
        tags: List<String> = emptyList(),
        conditions: List<RuleCondition> = listOf(condition(RuleObject.PHONE, RuleOperator.CONTAINS, "138")),
        enabled: Boolean = true,
    ) = UserRule(
        id = id,
        name = "rule$id",
        action = action,
        tags = tags,
        conditions = conditions,
        enabled = enabled,
    )

    private fun condition(
        field: RuleObject,
        operator: RuleOperator,
        value: String,
    ) = RuleCondition(field, operator, value)

    private fun record() = RuleRecord(
        address = "13800000000",
        roleId = 1L,
        body = "验证码 1234",
        kind = RecordKind.SMS,
        smsDirection = SmsDirection.INBOX,
        timestamp = 1_000L,
    )
}
