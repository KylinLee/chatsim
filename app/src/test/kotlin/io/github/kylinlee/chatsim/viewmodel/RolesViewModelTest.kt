package io.github.kylinlee.chatsim.viewmodel

import android.telecom.PhoneAccountHandle
import io.github.kylinlee.chatsim.data.local.RoleStore
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.data.model.SIMAccount
import io.github.kylinlee.chatsim.data.model.SIMCard
import io.github.kylinlee.chatsim.data.repository.RoleRepositoryImpl
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.repository.SimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RolesViewModelTest {
    private val store = FakeRoleStore()
    private val simRepository = FakeSimRepository()
    private lateinit var repository: RoleRepositoryImpl
    private lateinit var viewModel: RolesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = RoleRepositoryImpl(store, simRepository)
        viewModel = RolesViewModel(repository, simRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addingAndSelectingRolesUpdatesState() {
        viewModel.addVirtualRole("Work", "12520", "17951")
        viewModel.addVirtualRole("Personal", "17952", "17953")

        assertEquals(2, viewModel.roles.value.size)
        assertEquals(1L, viewModel.activeRole.value?.id)

        viewModel.setActiveRole(2L)
        assertEquals(2L, viewModel.activeRole.value?.id)
    }

    @Test
    fun updatingRolePersistsChanges() {
        viewModel.addVirtualRole("Work", "12520", "17951")
        val work = viewModel.roles.value.first()
        viewModel.updateRole(work.copy(label = "Office"))

        assertEquals("Office", viewModel.roles.value.first().label)
    }

    @Test
    fun deletingRoleRemovesIt() {
        viewModel.addVirtualRole("Work", "12520", "17951")
        viewModel.deleteRole(1L)

        assertEquals(0, viewModel.roles.value.size)
    }

    @Test
    fun simRoleAvailabilityFollowsSubscriptions() {
        simRepository.subscriptions = listOf(SIMCard(1, 7, "SIM 1"))
        viewModel.refresh()
        val simRole = viewModel.roles.value.first { !it.isVirtual }

        assertTrue(viewModel.isAvailable(simRole))
        assertEquals("SIM 1", viewModel.simLabelFor(7))

        simRepository.subscriptions = emptyList()
        viewModel.refresh()
        assertFalse(viewModel.isAvailable(simRole))
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
