package io.github.kylinlee.chatsim.viewmodel

import android.telecom.PhoneAccountHandle
import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.legacy.CallLauncher
import io.github.kylinlee.chatsim.data.local.RoleStore
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.data.model.SIMAccount
import io.github.kylinlee.chatsim.data.model.SIMCard
import io.github.kylinlee.chatsim.data.repository.RoleRepositoryImpl
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.repository.ContactRepository
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import io.github.kylinlee.chatsim.repository.SimRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DialpadViewModelTest {
    private val roleRepository = RoleRepositoryImpl(
        FakeRoleStore().apply {
            roles = listOf(Role(id = 1, label = "Work", smsPrefix = "12520", callPrefix = "12520"))
            activeId = 1L
        },
        FakeSimRepository(),
    )

    private val contactRepository = mockk<ContactRepository>(relaxed = true)
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val callLauncher = mockk<CallLauncher>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = DialpadViewModel(
        contactRepository = contactRepository,
        conversationRepository = conversationRepository,
        roleRepository = roleRepository,
        settings = settings,
        callLauncher = callLauncher,
        eventBus = AppEventBus,
    )

    @Test
    fun dialingAppliesActiveRolePrefix() {
        val viewModel = createViewModel()

        assertEquals("1252013800000000", viewModel.getNumberToDial("13800000000"))
        assertEquals("110", viewModel.getNumberToDial("110"))
    }

    @Test
    fun inputEditingUpdatesState() {
        val viewModel = createViewModel()

        viewModel.append("1")
        viewModel.append("2")
        viewModel.append("3")
        assertEquals("123", viewModel.input.value)

        viewModel.backspace()
        assertEquals("12", viewModel.input.value)

        viewModel.clear()
        assertEquals("", viewModel.input.value)

        viewModel.setInput("13800000000")
        assertEquals("13800000000", viewModel.input.value)
    }

    @Test
    fun overrideRoleIsUsedForDialing() {
        val viewModel = createViewModel()
        val override = Role(id = 2, label = "Personal", smsPrefix = "17951", callPrefix = "17951")

        viewModel.setRoleOverride(override)

        assertEquals("1795113800000000", viewModel.getNumberToDial("13800000000"))
    }

    @Test
    fun blankInputIsNotDialable() {
        val viewModel = createViewModel()

        assertEquals(false, viewModel.isDialable())
        viewModel.append("1")
        assertEquals(true, viewModel.isDialable())
    }

    @Test
    fun suggestionsAreSortedByRecentConversationTime() {
        every { contactRepository.searchByName(any()) } returns emptyList()
        coEvery { contactRepository.loadContacts() } returns listOf(
            contact(id = 1, name = "张三", number = "13800000001"),
            contact(id = 2, name = "张三", number = "13800000002"),
        )
        coEvery { conversationRepository.loadConversations() } returns listOf(
            thread("13800000002", date = 2_000L),
            thread("13800000001", date = 1_000L),
        )

        val viewModel = createViewModel()
        viewModel.setInput("138")

        assertEquals(
            listOf("13800000002", "13800000001"),
            viewModel.suggestions.value.map { it.phoneNumber.value },
        )
    }

    @Test
    fun suggestionsWithoutConversationsKeepOriginalOrder() {
        every { contactRepository.searchByName(any()) } returns emptyList()
        coEvery { contactRepository.loadContacts() } returns listOf(
            contact(id = 1, name = "A", number = "13800000001"),
            contact(id = 2, name = "B", number = "13800000002"),
        )
        coEvery { conversationRepository.loadConversations() } returns emptyList()

        val viewModel = createViewModel()
        viewModel.setInput("138")

        assertEquals(
            listOf("13800000001", "13800000002"),
            viewModel.suggestions.value.map { it.phoneNumber.value },
        )
    }

    private fun contact(id: Int, name: String, number: String): Contact {
        val contact = mockk<Contact>(relaxed = true)
        every { contact.id } returns id
        every { contact.contactId } returns id
        every { contact.name } returns name
        every { contact.getNameToDisplay() } returns name
        every { contact.phoneNumbers } returns ArrayList(listOf(PhoneNumber(number, 0, "", number)))
        return contact
    }

    private fun thread(number: String, date: Long) = ConversationThread(
        conversationId = 1L,
        roleId = 0L,
        role = null,
        peerNumber = number,
        title = number,
        photoUri = "",
        snippet = "",
        date = date,
        read = true,
        isGroupConversation = false,
        isScheduled = false,
        isPinned = false,
        isAvailable = true,
    )

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
        override fun getAvailableSimAccounts(): List<SIMAccount> = emptyList()

        override fun getActiveSubscriptions(): List<SIMCard> = emptyList()

        override fun areMultipleSimsAvailable(): Boolean = false

        override fun getDefaultSmsSubscriptionId(): Int = -1

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
