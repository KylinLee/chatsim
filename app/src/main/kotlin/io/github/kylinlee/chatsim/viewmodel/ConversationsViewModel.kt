package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.SystemClock
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.domain.ContactFilter
import io.github.kylinlee.chatsim.domain.ConversationMerger
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.repository.CallLogRepository
import io.github.kylinlee.chatsim.repository.ContactRepository
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.RoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val callLogRepository: CallLogRepository,
    private val contactRepository: ContactRepository,
    private val roleRepository: RoleRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationThread>>(emptyList())
    val conversations: StateFlow<List<ConversationThread>> = _conversations.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val roles: StateFlow<List<Role>> = roleRepository.roles

    val selectedRoleId: StateFlow<Long?> = roleRepository.activeRole
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, roleRepository.getActiveRole()?.id)

    private var smsThreads: List<ConversationThread> = emptyList()
    private var calls: List<CallRecord> = emptyList()

    init {
        viewModelScope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.ConversationRead -> updateReadState(event.conversationId, read = event.read)
                    is AppEvent.ConversationsChanged -> reloadConversations()
                    is AppEvent.RefreshConversations, is AppEvent.RefreshMessages -> refresh()
                    is AppEvent.RefreshContacts -> refreshContacts()
                    is AppEvent.RefreshCallLog -> refreshCalls()
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            // roles changed -> call log entries must be re-resolved with the new roles
            roleRepository.roles.drop(1).collect {
                callLogRepository.invalidateCache()
                refresh()
            }
        }
        viewModelScope.launch {
            roleRepository.activeRole.drop(1).collect {
                applyFilters()
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // render the locally cached conversations and calls right away, then refresh from the system
            if (_conversations.value.isEmpty()) {
                val cached = conversationRepository.loadConversations()
                val cachedCalls = callLogRepository.loadRecentCalls()
                if (cached.isNotEmpty() || cachedCalls.isNotEmpty()) {
                    smsThreads = cached
                    calls = cachedCalls
                    applyFilters()
                }
            }

            _isLoading.value = true
            val startedAt = SystemClock.elapsedRealtime()
            callLogRepository.syncCalls()
            smsThreads = conversationRepository.syncWithSystem()
            calls = callLogRepository.loadRecentCalls()
            applyFilters()

            // keep the loading indicator visible long enough for its animation to be seen
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (elapsed < MIN_LOADING_DURATION_MS) {
                delay(MIN_LOADING_DURATION_MS - elapsed)
            }
            _isLoading.value = false
        }
    }

    /** Reloads the call log without flashing the loading indicator, only when the calls actually changed. */
    private fun refreshCalls() {
        viewModelScope.launch {
            val freshCalls = callLogRepository.loadRecentCalls()
            if (freshCalls != calls) {
                calls = freshCalls
                applyFilters()
            }
        }
    }

    /** Reloads the contacts first so changed names and photos are re-resolved. */
    private fun refreshContacts() {
        viewModelScope.launch {
            contactRepository.loadContacts()
            refresh()
        }
    }

    /** Reloads the locally stored conversations without the full system sync or the loading indicator. */
    fun reloadConversations() {
        viewModelScope.launch {
            smsThreads = conversationRepository.loadConversations()
            applyFilters()
        }
    }

    fun selectRole(roleId: Long) {
        roleRepository.setActiveRole(roleId)
    }

    fun search(query: String) {
        _query.value = query
        applyFilters()
    }

    fun getThread(conversationId: Long): ConversationThread? =
        _conversations.value.firstOrNull { it.conversationId == conversationId }

    fun pinConversation(conversationId: Long) {
        conversationRepository.pinConversation(conversationId)
        updatePinnedState(conversationId, isPinned = true)
    }

    fun unpinConversation(conversationId: Long) {
        conversationRepository.unpinConversation(conversationId)
        updatePinnedState(conversationId, isPinned = false)
    }

    fun markRead(conversationId: Long) {
        viewModelScope.launch {
            conversationRepository.markRead(conversationId)
            updateReadState(conversationId, read = true)
        }
    }

    fun markUnread(conversationId: Long) {
        viewModelScope.launch {
            conversationRepository.markUnread(conversationId)
            updateReadState(conversationId, read = false)
        }
    }

    fun deleteConversation(conversationId: Long) {
        val thread = _conversations.value.firstOrNull { it.conversationId == conversationId }
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
            if (thread != null) {
                callLogRepository.removeCallsForConversation(thread.roleId, thread.peerNumber)
            }
            smsThreads = smsThreads.filterNot { it.conversationId == conversationId }
            if (thread != null) {
                val peerNumber = ContactFilter.normalizePhoneNumber(thread.peerNumber)
                calls = calls.filterNot {
                    (it.role?.id ?: 0L) == thread.roleId &&
                        ContactFilter.normalizePhoneNumber(it.displayNumber) == peerNumber
                }
            }
            applyFilters()
        }
    }

    private fun updatePinnedState(conversationId: Long, isPinned: Boolean) {
        smsThreads = smsThreads.map { if (it.conversationId == conversationId) it.copy(isPinned = isPinned) else it }
        applyFilters()
    }

    private fun updateReadState(conversationId: Long, read: Boolean) {
        smsThreads = smsThreads.map { if (it.conversationId == conversationId) it.copy(read = read) else it }
        applyFilters()
    }

    private fun applyFilters() {
        val selectedRoleId = roleRepository.getActiveRole()?.id
        val merged = ConversationMerger.merge(smsThreads, calls)
            .filter { selectedRoleId == null || it.roleId == selectedRoleId }
        _unreadCount.value = merged.count { !it.read }
        val filtered = if (_query.value.isBlank()) {
            merged
        } else {
            val normalizedQuery = ContactFilter.normalizeText(_query.value)
            val queryDigits = ContactFilter.normalizePhoneNumber(_query.value)
            merged.filter { thread ->
                ContactFilter.normalizeText(thread.title).contains(normalizedQuery) ||
                    ContactFilter.normalizeText(thread.snippet).contains(normalizedQuery) ||
                    (thread.lastCall?.name?.let { ContactFilter.normalizeText(it).contains(normalizedQuery) } == true) ||
                    (queryDigits.isNotEmpty() && ContactFilter.phoneMatches(thread.peerNumber, queryDigits))
            }
        }

        _conversations.value = filtered.sortedWith(
            compareByDescending<ConversationThread> { it.isPinned }.thenByDescending { it.date }
        )
    }

    companion object {
        private const val MIN_LOADING_DURATION_MS = 700L
    }
}
