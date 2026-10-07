package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.domain.model.RoleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoleResolverTest {
    private val virtual = Role(id = 1, label = "Work", smsPrefix = "12520", callPrefix = "17951", subscriptionId = 7)
    private val sim = Role(id = 2, label = "SIM 1", kind = RoleKind.SIM, subscriptionId = 7)
    private val roles = listOf(virtual, sim)

    @Test
    fun virtualPrefixWinsAndIsStrippedPerChannel() {
        val sms = RoleResolver.resolveIncoming(RoleChannel.SMS, "1252013800000000", 7, roles, fallbackRoleId = sim.id)
        assertEquals("13800000000", sms?.peerNumber)
        assertEquals(virtual.id, sms?.role?.id)

        val call = RoleResolver.resolveIncoming(RoleChannel.CALL, "1795113800000000", 7, roles, fallbackRoleId = sim.id)
        assertEquals("13800000000", call?.peerNumber)
        assertEquals(virtual.id, call?.role?.id)
    }

    @Test
    fun subscriptionRoleIsUsedWithoutPrefix() {
        val resolved = RoleResolver.resolveIncoming(RoleChannel.SMS, "13800000000", 7, roles, fallbackRoleId = null)

        assertEquals(sim.id, resolved?.role?.id)
        assertEquals("13800000000", resolved?.peerNumber)
    }

    @Test
    fun virtualFallbackIsIgnoredAndSimRoleIsUsed() {
        val resolved = RoleResolver.resolveIncoming(RoleChannel.SMS, "13800000000", 9, roles, fallbackRoleId = virtual.id)

        assertEquals(sim.id, resolved?.role?.id)
    }

    @Test
    fun simFallbackIsUsedWhenNothingMatches() {
        val resolved = RoleResolver.resolveIncoming(RoleChannel.SMS, "13800000000", 9, roles, fallbackRoleId = sim.id)

        assertEquals(sim.id, resolved?.role?.id)
    }

    @Test
    fun noRoleReturnsNull() {
        assertNull(RoleResolver.resolveIncoming(RoleChannel.SMS, "13800000000", 9, roles, fallbackRoleId = null))
        assertNull(RoleResolver.resolveIncoming(RoleChannel.SMS, "   ", 7, roles, fallbackRoleId = sim.id))
    }

    @Test
    fun outgoingAppliesChannelPrefixAndSubscription() {
        val sms = RoleResolver.resolveOutgoing(RoleChannel.SMS, "13800000000", virtual)
        assertEquals("1252013800000000", sms.number)
        assertEquals(7, sms.subscriptionId)
        assertEquals(virtual.id, sms.roleId)

        val call = RoleResolver.resolveOutgoing(RoleChannel.CALL, "13800000000", virtual)
        assertEquals("1795113800000000", call.number)
    }

    @Test
    fun outgoingWithoutRoleKeepsNumber() {
        val route = RoleResolver.resolveOutgoing(RoleChannel.SMS, "13800000000", null)

        assertEquals("13800000000", route.number)
        assertNull(route.subscriptionId)
        assertNull(route.roleId)
    }
}
