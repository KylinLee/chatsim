package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.legacy.CallLauncher
import io.github.kylinlee.chatsim.data.model.DialpadSuggestion
import io.github.kylinlee.chatsim.domain.RecentContactTimes
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.domain.rule.OutgoingEvent
import io.github.kylinlee.chatsim.domain.rule.OutgoingOutcome
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import io.github.kylinlee.chatsim.domain.rule.RoleRuleContext
import io.github.kylinlee.chatsim.domain.rule.toRoute
import io.github.kylinlee.chatsim.repository.ContactRepository
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.RoleRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DialpadViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val conversationRepository: ConversationRepository,
    private val roleRepository: RoleRepository,
    private val settings: SettingsRepository,
    private val callLauncher: CallLauncher,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _suggestions = MutableStateFlow<List<DialpadSuggestion>>(emptyList())
    val suggestions: StateFlow<List<DialpadSuggestion>> = _suggestions.asStateFlow()

    private val _roleOverride = MutableStateFlow<Role?>(null)
    val roleOverride: StateFlow<Role?> = _roleOverride.asStateFlow()

    val activeRole: StateFlow<Role?> = roleRepository.activeRole

    val roles: StateFlow<List<Role>> = roleRepository.roles

    val dialpadMuted: Boolean
        get() = settings.dialpadMuted

    val hideDialpadNumbers: Boolean
        get() = settings.hideDialpadNumbers

    private var contacts: List<Contact> = emptyList()
    private var recentTimes: Map<String, Long> = emptyMap()

    init {
        viewModelScope.launch {
            contacts = contactRepository.loadContacts()
            refreshRecentTimes()
            updateSuggestions()
        }
        viewModelScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.RefreshConversations, is AppEvent.RefreshMessages,
                    is AppEvent.RefreshCallLog, is AppEvent.ConversationsChanged,
                    -> {
                        refreshRecentTimes()
                        updateSuggestions()
                    }

                    else -> Unit
                }
            }
        }
    }

    private suspend fun refreshRecentTimes() {
        val conversations = conversationRepository.loadConversations()
            .ifEmpty { conversationRepository.getCachedConversations() }
        recentTimes = RecentContactTimes.build(conversations)
    }

    fun append(text: String) {
        _input.value += text
        updateSuggestions()
    }

    fun backspace() {
        if (_input.value.isNotEmpty()) {
            _input.value = _input.value.dropLast(1)
            updateSuggestions()
        }
    }

    fun clear() {
        _input.value = ""
        updateSuggestions()
    }

    fun setInput(text: String) {
        _input.value = text
        updateSuggestions()
    }

    fun setRoleOverride(role: Role?) {
        _roleOverride.value = role
    }

    fun getNumberToDial(number: String = _input.value): String = routeCall(number).number

    fun placeCall(number: String = _input.value) {
        val route = routeCall(number)
        if (route.number.isBlank()) return

        callLauncher.placeCall(route.toRoute())
    }

    private fun routeCall(number: String): OutgoingOutcome {
        val role = _roleOverride.value ?: roleRepository.getActiveRole()
        return RuleEngines.engine.routeOutgoing(
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
    }

    fun isDialable(number: String = _input.value): Boolean = number.isNotBlank()

    fun getContactForNumber(number: String): Contact? = contactRepository.getContactByNumber(number)

    private fun updateSuggestions() {
        _suggestions.value = getSuggestions(_input.value)
    }

    private fun getSuggestions(text: String): List<DialpadSuggestion> {
        if (text.isBlank()) return emptyList()

        val nameMatches = contactRepository.searchByName(text).map { it.id }.toHashSet()

        val suggestions = ArrayList<DialpadSuggestion>()
        contacts.forEach { contact ->
            val matchingNumbers = contact.phoneNumbers.filter { phoneNumber ->
                phoneNumber.normalizedNumber.contains(text, true) || phoneNumber.value.contains(text, true)
            }
            val numbers = matchingNumbers.ifEmpty {
                if (contact.id in nameMatches) {
                    contact.phoneNumbers.sortedByDescending { it.isPrimary }
                } else {
                    emptyList()
                }
            }
            numbers.forEach { suggestions.add(DialpadSuggestion(contact, it)) }
        }

        return suggestions.sortedByDescending { suggestion ->
            val number = suggestion.phoneNumber.normalizedNumber.ifBlank { suggestion.phoneNumber.value }
            RecentContactTimes.latestForNumber(recentTimes, number)
        }
    }
}
