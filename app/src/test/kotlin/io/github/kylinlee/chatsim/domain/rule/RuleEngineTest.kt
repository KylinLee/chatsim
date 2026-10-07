package io.github.kylinlee.chatsim.domain.rule

import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.domain.model.RoleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEngineTest {
    private val virtual = Role(id = 1, label = "Work", smsPrefix = "12520", callPrefix = "17951", subscriptionId = 7)
    private val sim = Role(id = 2, label = "SIM 1", kind = RoleKind.SIM, subscriptionId = 7)

    @Test
    fun incomingResolutionUsesHighestPriorityRule() {
        val other = Role(id = 3, label = "Other", smsPrefix = "12520")
        val source = FakeRuleSource(
            listOf(
                rule("low", priority = 10, actions = listOf(RuleAction.AssignRole(ValueSource.Literal("3")))),
                rule(
                    "high",
                    priority = 100,
                    actions = listOf(RuleAction.AssignRole(ValueSource.Literal("1"), stripPrefix = true)),
                ),
            )
        )
        val engine = RuleEngine(source)

        val outcome = engine.evaluateIncoming(
            IncomingEvent(IncomingKind.SMS, "1252013800000000", null),
            RoleRuleContext(listOf(virtual, other)),
        )

        assertEquals(virtual.id, outcome.resolution?.role?.id)
        assertEquals("13800000000", outcome.resolution?.peerNumber)
    }

    @Test
    fun patternMismatchSkipsRule() {
        val source = FakeRuleSource(
            listOf(
                rule(
                    "patterned",
                    priority = 10,
                    pattern = "^999",
                    actions = listOf(RuleAction.AssignRole(ValueSource.Literal("1"))),
                )
            )
        )
        val engine = RuleEngine(source)

        val outcome = engine.evaluateIncoming(
            IncomingEvent(IncomingKind.SMS, "1252013800000000", null),
            RoleRuleContext(listOf(virtual)),
        )

        assertNull(outcome.resolution)
    }

    @Test
    fun patternMatchAppliesRule() {
        val source = FakeRuleSource(
            listOf(
                rule(
                    "patterned",
                    priority = 10,
                    pattern = "^12520",
                    actions = listOf(RuleAction.AssignRole(ValueSource.Literal("1"), stripPrefix = true)),
                )
            )
        )
        val engine = RuleEngine(source)

        val outcome = engine.evaluateIncoming(
            IncomingEvent(IncomingKind.SMS, "1252013800000000", null),
            RoleRuleContext(listOf(virtual)),
        )

        assertEquals(virtual.id, outcome.resolution?.role?.id)
        assertEquals("13800000000", outcome.resolution?.peerNumber)
    }

    @Test
    fun builtinRoleRulesResolveIncoming() {
        val outcome = RuleEngines.engine.evaluateIncoming(
            IncomingEvent(IncomingKind.SMS, "1252013800000000", 7),
            RoleRuleContext(listOf(virtual, sim), activeRoleId = sim.id, fallbackRoleId = sim.id),
        )

        assertEquals(virtual.id, outcome.resolution?.role?.id)
        assertEquals("13800000000", outcome.resolution?.peerNumber)
    }

    @Test
    fun callLogDoesNotUseActiveFallback() {
        val outcome = RuleEngines.engine.evaluateIncoming(
            IncomingEvent(IncomingKind.CALL_LOG, "13800000000", 9),
            RoleRuleContext(listOf(virtual, sim), activeRoleId = virtual.id, fallbackRoleId = null),
        )

        assertNull(outcome.resolution)
    }

    @Test
    fun builtinOutgoingRouteAppliesPrefixAndSubscription() {
        val outcome = RuleEngines.engine.routeOutgoing(
            OutgoingEvent("13800000000", RoleChannel.SMS, requestedRoleId = virtual.id, subscriptionId = null),
            RoleRuleContext(listOf(virtual, sim), activeRoleId = sim.id),
        )

        assertEquals("1252013800000000", outcome.number)
        assertEquals(7, outcome.subscriptionId)
        assertEquals(virtual.id, outcome.roleId)
    }

    @Test
    fun outgoingWithoutRequestedRoleKeepsNumberAndSubscription() {
        val outcome = RuleEngines.engine.routeOutgoing(
            OutgoingEvent("13800000000", RoleChannel.CALL, requestedRoleId = null, subscriptionId = 5),
            RoleRuleContext(listOf(virtual)),
        )

        assertEquals("13800000000", outcome.number)
        assertEquals(5, outcome.subscriptionId)
        assertNull(outcome.roleId)
    }

    @Test
    fun builtinBlacklistBlocksIncomingAndKeepsResolution() {
        val outcome = RuleEngines.engine.evaluateIncoming(
            IncomingEvent(IncomingKind.SMS, "1252013800000000", 7),
            RoleRuleContext(
                listOf(virtual, sim),
                activeRoleId = sim.id,
                fallbackRoleId = sim.id,
                isBlocked = { it == "1252013800000000" },
            ),
        )

        assertEquals(RuleDecision.BLOCK, outcome.decision)
        assertEquals("builtin.blacklist", outcome.hits.firstOrNull()?.ruleId)
        assertEquals(virtual.id, outcome.resolution?.role?.id)
        assertEquals("13800000000", outcome.resolution?.peerNumber)
    }

    @Test
    fun builtinBlacklistAllowsUnblockedIncoming() {
        val outcome = RuleEngines.engine.evaluateIncoming(
            IncomingEvent(IncomingKind.SMS, "13800000000", 7),
            RoleRuleContext(listOf(virtual, sim), activeRoleId = sim.id, fallbackRoleId = sim.id),
        )

        assertEquals(RuleDecision.ALLOW, outcome.decision)
        assertNull(outcome.hits.firstOrNull())
    }

    private fun rule(
        id: String,
        priority: Int,
        pattern: String? = null,
        triggers: Set<RuleTrigger> = setOf(RuleTrigger.SMS_IN),
        actions: List<RuleAction> = emptyList(),
    ): CompiledRule = CompiledRule(
        id = id,
        name = id,
        priority = priority,
        triggers = triggers,
        pattern = pattern?.let { Regex(it) },
        actions = actions,
    )

    private class FakeRuleSource(private val rules: List<CompiledRule>) : RuleSource {
        override fun rules(): List<CompiledRule> = rules
    }
}
