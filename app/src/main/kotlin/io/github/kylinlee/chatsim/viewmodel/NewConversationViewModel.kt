package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.domain.model.SimpleContact
import io.github.kylinlee.chatsim.domain.ContactFilter
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.repository.ContactRepository
import io.github.kylinlee.chatsim.repository.RoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewConversationViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val roleRepository: RoleRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _contacts = MutableStateFlow<List<SimpleContact>>(emptyList())
    val contacts: StateFlow<List<SimpleContact>> = _contacts.asStateFlow()

    private val _filteredContacts = MutableStateFlow<List<SimpleContact>>(emptyList())
    val filteredContacts: StateFlow<List<SimpleContact>> = _filteredContacts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedRoleId = MutableStateFlow<Long?>(null)
    val selectedRoleId: StateFlow<Long?> = _selectedRoleId.asStateFlow()

    val roles: StateFlow<List<Role>> = roleRepository.roles
    val activeRole: StateFlow<Role?> = roleRepository.activeRole

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _contacts.value = contactRepository.loadAvailableSimpleContacts().sorted()
            applyFilter()
            _isLoading.value = false
        }
    }

    fun search(query: String) {
        _query.value = query
        applyFilter()
    }

    fun selectRole(roleId: Long) {
        _selectedRoleId.value = roleId
    }

    fun effectiveRoleId(): Long = _selectedRoleId.value ?: roleRepository.getActiveRole()?.id ?: 0L

    fun primaryNumberFor(contact: SimpleContact): String? {
        val primary = contact.phoneNumbers.firstOrNull { it.isPrimary }
        return (primary ?: contact.phoneNumbers.firstOrNull())?.value
    }

    fun numbersFor(contact: SimpleContact): List<String> = contact.phoneNumbers.map { it.normalizedNumber }

    private fun applyFilter() {
        val query = _query.value
        _filteredContacts.value = if (query.isBlank()) {
            _contacts.value
        } else {
            val normalizedQuery = ContactFilter.normalizeText(query)
            _contacts.value
                .filter { contact ->
                    contact.phoneNumbers.any { it.normalizedNumber.contains(query, true) } ||
                        ContactFilter.normalizeText(contact.name).contains(normalizedQuery)
                }
                .sortedBy { !ContactFilter.normalizeText(it.name).startsWith(normalizedQuery) }
        }
    }
}
