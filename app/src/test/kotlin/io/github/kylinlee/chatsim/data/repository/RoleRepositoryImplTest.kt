package io.github.kylinlee.chatsim.data.repository

import android.telecom.PhoneAccountHandle
import io.github.kylinlee.chatsim.data.local.RoleStore
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.data.model.SIMAccount
import io.github.kylinlee.chatsim.data.model.SIMCard
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.repository.SimRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleRepositoryImplTest {
    private val store = FakeRoleStore()
    private val simRepository = FakeSimRepository()
    private val repository = RoleRepositoryImpl(store, simRepository)

    @Test
    fun firstAddedRoleBecomesActive() {
        val role = repository.addVirtualRole("Work", "12520", "17951")

        assertEquals(1, repository.getRoles().size)
        assertEquals(role.id, repository.getActiveRole()?.id)
    }

    @Test
    fun defaultRoleBecomesActiveOverFirst() {
        repository.addVirtualRole("Work", "12520", "17951")
        val personal = repository.addVirtualRole("Personal", "17952", "17953", isDefault = true)

        assertEquals(personal.id, repository.getActiveRole()?.id)
    }

    @Test
    fun deletingActiveFallsBackToDefault() {
        val work = repository.addVirtualRole("Work", "12520", "17951")
        val personal = repository.addVirtualRole("Personal", "17952", "17953", isDefault = true)
        assertEquals(personal.id, repository.getActiveRole()?.id)

        repository.deleteRole(personal.id)
        assertEquals(work.id, repository.getActiveRole()?.id)
    }

    @Test
    fun deletingLastRoleClearsActive() {
        val work = repository.addVirtualRole("Work", "12520", "17951")
        repository.deleteRole(work.id)

        assertNull(repository.getActiveRole())
    }

    @Test
    fun setActiveRoleSwitchesSelection() {
        repository.addVirtualRole("Work", "12520", "17951")
        val personal = repository.addVirtualRole("Personal", "17952", "17953")

        repository.setActiveRole(personal.id)
        assertEquals(personal.id, repository.getActiveRole()?.id)
    }

    @Test
    fun applyPrefixUsesChannelAndSkipsAlreadyPrefixed() {
        val work = repository.addVirtualRole("Work", "12520", "17951")

        assertEquals("1252013800000000", repository.applyPrefix("13800000000", work, RoleChannel.SMS))
        assertEquals("1795113800000000", repository.applyPrefix("13800000000", work, RoleChannel.CALL))
        assertEquals("1252013800000000", repository.applyPrefix("1252013800000000", work, RoleChannel.SMS))
        assertEquals("110", repository.applyPrefix("110", work, RoleChannel.SMS))
    }

    @Test
    fun matchAndStripUseConfiguredRoles() {
        val work = repository.addVirtualRole("Work", "12520", "17951")

        assertEquals(work.id, repository.matchRole("1252013800000000", RoleChannel.SMS)?.id)
        assertNull(repository.matchRole("1252013800000000", RoleChannel.CALL))
        assertEquals("13800000000", repository.stripPrefix("1252013800000000", RoleChannel.SMS))
        assertEquals("13800000000", repository.stripPrefix("1795113800000000", RoleChannel.CALL))
    }

    @Test
    fun updateRolePersistsChanges() {
        val work = repository.addVirtualRole("Work", "12520", "17951")
        repository.updateRole(work.copy(label = "Office", smsPrefix = "17952"))

        assertEquals("Office", repository.getRoles().first().label)
        assertEquals("17952", repository.getRoles().first().smsPrefix)
        assertTrue(store.roles.first().label == "Office")
    }

    @Test
    fun simRolesAreRegisteredForActiveSubscriptions() {
        simRepository.subscriptions = listOf(SIMCard(1, 7, "SIM 1"))
        val simRole = repository.ensureSimRoles().first { !it.isVirtual }

        assertEquals(7, simRole.subscriptionId)
        assertTrue(repository.isAvailable(simRole))
    }

    @Test
    fun missingSubscriptionMakesRoleUnavailable() {
        simRepository.subscriptions = listOf(SIMCard(1, 7, "SIM 1"))
        val simRole = repository.ensureSimRoles().first { !it.isVirtual }

        simRepository.subscriptions = emptyList()
        repository.ensureSimRoles()

        assertFalse(repository.isAvailable(simRole))
    }

    private class FakeRoleStore : RoleStore {
        var roles: List<Role> = emptyList()
        var activeId: Long? = null

        override fun loadRoles(): List<Role> = roles

        override fun saveRoles(roles: List<Role>) {
            this.roles = roles
        }

        override fun loadActiveId(): Long? = activeId

        override fun saveActiveId(id: Long?) {
            activeId = id
        }
    }

    private class FakeSimRepository : SimRepository {
        var subscriptions: List<SIMCard> = emptyList()

        override fun getAvailableSimAccounts(): List<SIMAccount> = emptyList()

        override fun getActiveSubscriptions(): List<SIMCard> = subscriptions

        override fun areMultipleSimsAvailable(): Boolean = subscriptions.size > 1

        override fun getDefaultSmsSubscriptionId(): Int = subscriptions.firstOrNull()?.subscriptionId ?: -1

        override fun getPreferredSimSubscriptionId(number: String): Int = -1

        override fun savePreferredSimForNumber(number: String, subscriptionId: Int) {}

        override fun getProperSimIndex(
            availableSubscriptions: List<SIMCard>,
            numbers: List<String>,
            lastMessage: Message?,
        ): Int = 0

        override fun getPhoneAccountHandleForSubscription(subscriptionId: Int?): PhoneAccountHandle? = null

        override fun getCustomCallSim(number: String): PhoneAccountHandle? = null

        override fun saveCustomCallSim(number: String, handle: PhoneAccountHandle) {}

        override fun removeCustomCallSim(number: String) {}
    }
}
