package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.SimpleContact
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.legacy.CallLauncher
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.domain.model.TimelineItem
import io.github.kylinlee.chatsim.domain.rule.OutgoingEvent
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import io.github.kylinlee.chatsim.domain.rule.RoleRuleContext
import io.github.kylinlee.chatsim.domain.rule.toRoute
import io.github.kylinlee.chatsim.common.extensions.getAddresses
import io.github.kylinlee.chatsim.data.messaging.MessagingUtils.Companion.ADDRESS_SEPARATOR
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.navigation.ThreadRoute
import io.github.kylinlee.chatsim.repository.CallLogRepository
import io.github.kylinlee.chatsim.repository.ContactRepository
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.MessageRepository
import io.github.kylinlee.chatsim.repository.MessagingRepository
import io.github.kylinlee.chatsim.repository.RoleRepository
import io.github.kylinlee.chatsim.repository.ScheduledTaskRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import io.github.kylinlee.chatsim.repository.TimelineRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted private val route: ThreadRoute,
    private val timelineRepository: TimelineRepository,
    private val messageRepository: MessageRepository,
    private val messagingRepository: MessagingRepository,
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val conversationRepository: ConversationRepository,
    private val contactRepository: ContactRepository,
    private val roleRepository: RoleRepository,
    private val callLauncher: CallLauncher,
    private val callLogRepository: CallLogRepository,
    private val settings: SettingsRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    val roleId: Long = route.roleId
    val peerNumber: String = route.peerNumber

    private val initialTitle: String = route.title

    @AssistedFactory
    interface Factory {
        fun create(route: ThreadRoute): ThreadViewModel
    }

    private var conversationId: Long = 0
    private var systemThreadId: Long = 0

    private val _title = MutableStateFlow(initialTitle)
    val title: StateFlow<String> = _title.asStateFlow()

    private val _timeline = MutableStateFlow<List<TimelineItem>>(emptyList())
    val timeline: StateFlow<List<TimelineItem>> = _timeline.asStateFlow()

    private val _participants = MutableStateFlow<List<SimpleContact>>(emptyList())
    val participants: StateFlow<List<SimpleContact>> = _participants.asStateFlow()

    private val _role = MutableStateFlow<Role?>(null)
    val role: StateFlow<Role?> = _role.asStateFlow()

    private val _isRoleAvailable = MutableStateFlow(true)
    val isRoleAvailable: StateFlow<Boolean> = _isRoleAvailable.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isContactSaved = MutableStateFlow(true)
    val isContactSaved: StateFlow<Boolean> = _isContactSaved.asStateFlow()

    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    private var allMessagesFetched = false

    val deliveryReportsEnabled: Boolean
        get() = settings.enableDeliveryReports

    init {
        viewModelScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.RefreshMessages, is AppEvent.MessageStatusChanged -> refresh()
                    is AppEvent.RefreshCallLog -> refresh()
                    is AppEvent.RefreshContacts -> updateContactSavedState()
                    else -> Unit
                }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true

            conversationId = conversationRepository.getOrCreateConversation(roleId, peerNumber)
            systemThreadId = conversationRepository.getSystemThreadId(roleId, peerNumber)

            val role = roleRepository.getRole(roleId)
            _role.value = role
            _isRoleAvailable.value = roleRepository.isAvailable(role)

            _participants.value = buildParticipants(peerNumber, initialTitle)
            _title.value = contactRepository.getNameByNumber(peerNumber)?.takeIf { it.isNotBlank() }
                ?: initialTitle.ifBlank { peerNumber }
            updateContactSavedState()
            _isBlocked.value = settings.isNumberBlocked(peerNumber)

            refreshTimeline()
            _isLoading.value = false
        }
    }

    private suspend fun updateContactSavedState() {
        contactRepository.loadContacts()
        _isContactSaved.value = contactRepository.getContactByNumber(peerNumber) != null
    }

    fun refresh() {
        viewModelScope.launch {
            conversationId = conversationRepository.getOrCreateConversation(roleId, peerNumber)
            systemThreadId = conversationRepository.getSystemThreadId(roleId, peerNumber)
            refreshTimeline()
        }
    }

    private suspend fun refreshTimeline() {
        syncMessagesFromSystem()
        val refreshed = timelineRepository.getTimeline(conversationId, roleId, peerNumber)
        if (refreshed != _timeline.value) {
            _timeline.value = refreshed
        }
    }

    private suspend fun syncMessagesFromSystem() {
        if (conversationId <= 0 || systemThreadId <= 0) return

        runCatching {
            val messages = messageRepository.loadSystemMessages(
                threadId = systemThreadId,
                getImageResolutions = false,
                includeScheduledMessages = false,
            )
            if (messages.isNotEmpty()) {
                conversationRepository.persistSystemMessages(conversationId, peerNumber, messages)
            }
        }
    }

    private fun buildParticipants(number: String, name: String): List<SimpleContact> {
        val numbers = number.split(ADDRESS_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        if (numbers.isEmpty()) return emptyList()

        return numbers.map { currentNumber ->
            val contactName = contactRepository.getNameByNumber(currentNumber)?.takeIf { it.isNotBlank() }
                ?: name.ifBlank { currentNumber }
            val photo = contactRepository.getPhotoUriByNumber(currentNumber)?.takeIf { it.isNotBlank() }.orEmpty()
            val phoneNumber = PhoneNumber(currentNumber, 0, "", currentNumber)
            SimpleContact(0, 0, contactName, photo, arrayListOf(phoneNumber), ArrayList(), ArrayList())
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val role = _role.value ?: return
        if (!roleRepository.isAvailable(role)) return

        viewModelScope.launch {
            messagingRepository.sendMessage(
                text = text,
                addresses = listOf(peerNumber),
                subscriptionId = role.subscriptionId,
                role = role,
            )
            refresh()
        }
    }

    fun scheduleMessage(text: String, triggerAtMillis: Long) {
        if (text.isBlank()) return

        val role = _role.value ?: return

        viewModelScope.launch {
            scheduledTaskRepository.scheduleMessage(
                text = text,
                conversationId = conversationId,
                subscriptionId = role.subscriptionId ?: 0,
                triggerAtMillis = triggerAtMillis,
            )
            refresh()
        }
    }

    fun cancelScheduledMessage(messageId: Long) {
        viewModelScope.launch {
            scheduledTaskRepository.cancel(messageId)
            refresh()
        }
    }

    fun deleteMessages(messages: List<Message>, toRecycleBin: Boolean = false) {
        viewModelScope.launch {
            if (toRecycleBin) {
                messageRepository.moveMessagesToRecycleBin(messages)
            } else {
                messageRepository.deleteMessages(messages)
            }
            refresh()
            eventBus.tryEmit(AppEvent.ConversationsChanged(conversationId))
        }
    }

    fun restoreAllMessages() {
        viewModelScope.launch {
            messageRepository.restoreAllMessages(conversationId)
            refresh()
        }
    }

    fun markThreadRead() {
        viewModelScope.launch {
            conversationRepository.markRead(conversationId)
            eventBus.tryEmit(AppEvent.ConversationRead(conversationId))
        }
    }

    fun markUnread() {
        viewModelScope.launch {
            conversationRepository.markUnread(conversationId)
            eventBus.tryEmit(AppEvent.ConversationRead(conversationId, read = false))
        }
    }

    fun deleteConversation() {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
            callLogRepository.removeCallsForConversation(roleId, peerNumber)
            eventBus.tryEmit(AppEvent.RefreshConversations)
        }
    }

    fun blockNumber() {
        viewModelScope.launch {
            settings.addBlockedNumber(peerNumber)
            _isBlocked.value = true
            eventBus.tryEmit(AppEvent.RefreshMessages)
        }
    }

    fun unblockNumber() {
        viewModelScope.launch {
            if (!settings.removeBlockedNumber(peerNumber)) return@launch

            _isBlocked.value = false
            conversationRepository.restoreConversations(peerNumber)
            conversationId = conversationRepository.getOrCreateConversation(roleId, peerNumber)
            systemThreadId = conversationRepository.getSystemThreadId(roleId, peerNumber)
            refresh()
            eventBus.tryEmit(AppEvent.RefreshConversations)
            eventBus.tryEmit(AppEvent.RefreshCallLog)
        }
    }

    fun callNumber(number: String) {
        if (!_isRoleAvailable.value) return

        val role = _role.value
        val route = RuleEngines.engine.routeOutgoing(
            OutgoingEvent(
                address = number,
                channel = RoleChannel.CALL,
                requestedRoleId = role?.id,
                subscriptionId = role?.subscriptionId,
            ),
            RoleRuleContext(
                roleRepository.getRoles(),
                activeRoleId = roleRepository.getActiveRole()?.id,
                requestedRole = role,
            ),
        )
        if (route.number.isBlank()) return

        callLauncher.placeCall(route.toRoute())
    }

    fun loadOlderMessages() {
        if (allMessagesFetched) return
        if (systemThreadId <= 0) return

        val firstMessage = _timeline.value.filterIsInstance<TimelineItem.MessageItem>().firstOrNull()?.message ?: return
        viewModelScope.launch {
            val olderMessages = timelineRepository.loadOlderMessages(systemThreadId, firstMessage.date)
            if (olderMessages.isEmpty()) {
                allMessagesFetched = true
            }
            refreshTimeline()
        }
    }
}
