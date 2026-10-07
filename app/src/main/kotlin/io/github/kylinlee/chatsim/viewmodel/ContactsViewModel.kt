package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.model.contacts.ContactSource
import io.github.kylinlee.chatsim.domain.model.contacts.Group
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.domain.ContactFilter
import io.github.kylinlee.chatsim.domain.ContactSection
import io.github.kylinlee.chatsim.domain.RecentContactTimes
import io.github.kylinlee.chatsim.domain.buildContactSections
import io.github.kylinlee.chatsim.domain.displayName
import io.github.kylinlee.chatsim.domain.model.AccountGroup
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.model.ContactItem
import io.github.kylinlee.chatsim.repository.CallLogRepository
import io.github.kylinlee.chatsim.repository.ContactRepository
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val conversationRepository: ConversationRepository,
    private val callLogRepository: CallLogRepository,
    private val settings: SettingsRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    private val _sections = MutableStateFlow<List<ContactSection>>(emptyList())
    val sections: StateFlow<List<ContactSection>> = _sections.asStateFlow()

    private val _contactSources = MutableStateFlow<List<ContactSource>>(emptyList())
    val contactSources: StateFlow<List<ContactSource>> = _contactSources.asStateFlow()

    private val _selectedSourceName = MutableStateFlow<String?>(null)
    val selectedSourceName: StateFlow<String?> = _selectedSourceName.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    private val _accountGroups = MutableStateFlow<List<AccountGroup>>(emptyList())

    /** Groups of the currently selected account source. */
    val visibleGroups: StateFlow<List<Group>> = combine(_accountGroups, _selectedSourceName) { groups, sourceName ->
        groups.filter { sourceName == null || it.accountName == sourceName }.map { it.group }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var allContacts: List<Contact> = emptyList()
    private var recentTimes: Map<String, Long> = emptyMap()

    init {
        viewModelScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.RefreshContacts, is AppEvent.RefreshMessages, is AppEvent.RefreshConversations,
                    is AppEvent.RefreshCallLog,
                    -> refresh(showLoading = false)

                    else -> Unit
                }
            }
        }
        refresh()
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _isLoading.value = true
            }
            allContacts = contactRepository.loadContacts()
            _accountGroups.value = contactRepository.loadAccountGroups()
            _contactSources.value = contactRepository.loadContactSources()
            recentTimes = buildRecentTimes()
            applyFilter()
            if (showLoading) {
                _isLoading.value = false
            }
        }
    }

    fun selectSource(sourceName: String?) {
        _selectedSourceName.value = sourceName
        val selectedGroupId = _selectedGroupId.value
        if (selectedGroupId != null && sourceName != null) {
            val group = _accountGroups.value.firstOrNull { it.group.id == selectedGroupId }
            if (group == null || group.accountName != sourceName) {
                _selectedGroupId.value = null
            }
        }
        applyFilter()
    }

    fun selectGroup(groupId: Long?) {
        _selectedGroupId.value = groupId
        applyFilter()
    }

    fun search(query: String) {
        _query.value = query
        applyFilter()
    }

    fun pinContact(id: Int) {
        settings.pinContact(id)
        applyFilter()
    }

    fun unpinContact(id: Int) {
        settings.unpinContact(id)
        applyFilter()
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
            eventBus.tryEmit(AppEvent.RefreshContacts)
        }
    }

    private suspend fun buildRecentTimes(): Map<String, Long> {
        val conversations = conversationRepository.loadConversations()
            .ifEmpty { conversationRepository.getCachedConversations() }
        val calls = callLogRepository.getCachedCalls().ifEmpty { callLogRepository.loadRecentCalls() }

        return RecentContactTimes.build(conversations, calls)
    }

    private fun latestContactTime(contact: Contact): Long {
        var latest = 0L
        contact.phoneNumbers.forEach { phone ->
            val number = phone.normalizedNumber.ifBlank { phone.value }
            latest = maxOf(latest, RecentContactTimes.latestForNumber(recentTimes, number))
        }
        return latest
    }

    private fun applyFilter() {
        val pinned = settings.pinnedContacts
        val sourceName = _selectedSourceName.value
        val groupId = _selectedGroupId.value
        val query = _query.value

        var visible = allContacts
        if (sourceName != null) {
            visible = visible.filter { it.source == sourceName }
        }
        if (groupId != null) {
            visible = visible.filter { contact -> contact.groups.any { it.id == groupId } }
        }
        if (query.isNotBlank()) {
            visible = ContactFilter.filter(visible, query)
        }

        val items = visible
            .map { contact ->
                ContactItem(
                    contact = contact,
                    isPinned = pinned.contains(contact.id.toString()),
                    lastContactTime = latestContactTime(contact),
                )
            }
            .sortedByDescending { it.isPinned }

        val sections = buildContactSections(items.filterNot { it.isPinned }) { it.displayName() }

        if (items != _contacts.value) {
            _contacts.value = items
        }
        if (sections != _sections.value) {
            _sections.value = sections
        }
    }}
