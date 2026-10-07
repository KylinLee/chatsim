package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePrefixerTest {
    private val role = Role(id = 1, label = "Work", smsPrefix = "12520", callPrefix = "17951")

    @Test
    fun normalizeKeepsPlusAndDigits() {
        assertEquals("+8613800000000", RolePrefixer.normalize("+86 138-0000 0000"))
        assertEquals("13800000000", RolePrefixer.normalize("138 0000 0000"))
    }

    @Test
    fun isPrefixableRejectsEmptyShortAndServiceNumbers() {
        assertFalse(RolePrefixer.isPrefixable(""))
        assertFalse(RolePrefixer.isPrefixable("110"))
        assertFalse(RolePrefixer.isPrefixable("10086"))
        assertFalse(RolePrefixer.isPrefixable("*123#"))
        assertFalse(RolePrefixer.isPrefixable("#100#"))
        assertTrue(RolePrefixer.isPrefixable("13800000000"))
        assertTrue(RolePrefixer.isPrefixable("01012345678"))
    }

    @Test
    fun applyPrefixUsesChannelPrefix() {
        assertEquals("1252013800000000", RolePrefixer.applyPrefix("13800000000", role, RoleChannel.SMS))
        assertEquals("1795113800000000", RolePrefixer.applyPrefix("13800000000", role, RoleChannel.CALL))
    }

    @Test
    fun applyPrefixHandlesNullDisabledAndBlank() {
        assertEquals("13800000000", RolePrefixer.applyPrefix("13800000000", null, RoleChannel.SMS))
        assertEquals("13800000000", RolePrefixer.applyPrefix("13800000000", role.copy(enabled = false), RoleChannel.SMS))
        assertEquals("13800000000", RolePrefixer.applyPrefix("13800000000", role.copy(smsPrefix = " "), RoleChannel.SMS))
        assertEquals("13800000000", RolePrefixer.applyPrefix("13800000000", role.copy(smsPrefix = ""), RoleChannel.SMS))
    }

    @Test
    fun applyPrefixSkipsAlreadyPrefixed() {
        assertEquals("1252013800000000", RolePrefixer.applyPrefix("1252013800000000", role, RoleChannel.SMS))
    }

    @Test
    fun applyPrefixSkipsServiceNumbers() {
        assertEquals("110", RolePrefixer.applyPrefix("110", role, RoleChannel.SMS))
        assertEquals("*100#", RolePrefixer.applyPrefix("*100#", role, RoleChannel.SMS))
    }

    @Test
    fun matchRoleUsesLongestPrefixPerChannel() {
        val roles = listOf(
            Role(id = 1, label = "Short", smsPrefix = "125"),
            Role(id = 2, label = "Long", smsPrefix = "12520"),
        )

        assertEquals(2L, RolePrefixer.matchRole("1252013800000000", roles, RoleChannel.SMS)?.id)
        assertEquals(1L, RolePrefixer.matchRole("12513800000000", roles, RoleChannel.SMS)?.id)
        assertNull(RolePrefixer.matchRole("13800000000", roles, RoleChannel.SMS))
    }

    @Test
    fun matchRoleIsChannelIsolated() {
        val roles = listOf(role)

        assertEquals(role.id, RolePrefixer.matchRole("1252013800000000", roles, RoleChannel.SMS)?.id)
        assertNull(RolePrefixer.matchRole("1252013800000000", roles, RoleChannel.CALL))
        assertEquals(role.id, RolePrefixer.matchRole("1795113800000000", roles, RoleChannel.CALL)?.id)
        assertNull(RolePrefixer.matchRole("1795113800000000", roles, RoleChannel.SMS))
    }

    @Test
    fun matchRoleIgnoresDisabledAndBlankChannelPrefix() {
        assertNull(RolePrefixer.matchRole("1252013800000000", listOf(role.copy(enabled = false)), RoleChannel.SMS))
        assertNull(RolePrefixer.matchRole("1252013800000000", listOf(role.copy(smsPrefix = "")), RoleChannel.SMS))
    }

    @Test
    fun stripPrefixRestoresOriginalNumber() {
        val roles = listOf(role)
        assertEquals("13800000000", RolePrefixer.stripPrefix("1252013800000000", roles, RoleChannel.SMS))
        assertEquals("+8613800000000", RolePrefixer.stripPrefix("12520+8613800000000", roles, RoleChannel.SMS))
        assertEquals("13800000000", RolePrefixer.stripPrefix("1795113800000000", roles, RoleChannel.CALL))
        assertEquals("13800000000", RolePrefixer.stripPrefix("13800000000", roles, RoleChannel.SMS))
    }

    @Test
    fun isAlreadyPrefixedDetectsChannelPrefix() {
        val roles = listOf(role)
        assertTrue(RolePrefixer.isAlreadyPrefixed("1252013800000000", roles, RoleChannel.SMS))
        assertFalse(RolePrefixer.isAlreadyPrefixed("1252013800000000", roles, RoleChannel.CALL))
        assertFalse(RolePrefixer.isAlreadyPrefixed("13800000000", roles, RoleChannel.SMS))
    }
}
