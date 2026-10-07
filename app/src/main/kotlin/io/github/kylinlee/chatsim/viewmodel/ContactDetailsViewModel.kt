package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.provider.ContactsContract.CommonDataKinds.Phone
import io.github.kylinlee.chatsim.common.extensions.normalizePhoneNumber
import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.model.contacts.ContactSource
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.domain.model.AccountGroup
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.model.ContactAccount
import io.github.kylinlee.chatsim.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The account the edited contact should belong to, together with the groups to keep in it. */
data class ContactAccountSelection(
    val source: ContactSource,
    val rawContactId: Int,
    val isSelected: Boolean,
    val groupIds: Set<Long>,
)

@HiltViewModel
class ContactDetailsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact.asStateFlow()

    private val _accountGroups = MutableStateFlow<List<AccountGroup>>(emptyList())
    val accountGroups: StateFlow<List<AccountGroup>> = _accountGroups.asStateFlow()

    private val _accounts = MutableStateFlow<List<ContactAccount>>(emptyList())
    val accounts: StateFlow<List<ContactAccount>> = _accounts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    fun load(contactId: Int, phoneNumber: String = "") {
        if (contactId != 0 && _contact.value?.id == contactId) return

        viewModelScope.launch {
            _isLoading.value = true
            _accountGroups.value = contactRepository.loadAccountGroups()
            if (contactId == 0) {
                val numbers = ArrayList<PhoneNumber>()
                val number = phoneNumber.trim()
                if (number.isNotEmpty()) {
                    numbers.add(
                        PhoneNumber(
                            value = number,
                            type = Phone.TYPE_MOBILE,
                            label = "",
                            normalizedNumber = number.normalizePhoneNumber(),
                        )
                    )
                }
                _contact.value = Contact(id = 0, contactId = 0, phoneNumbers = numbers)
                _accounts.value = contactRepository.loadSaveableContactSources().map { source ->
                    ContactAccount(source = source, rawContactId = 0, groupIds = emptySet())
                }
            } else {
                val contact = contactRepository.getContactById(contactId)
                _contact.value = contact
                _accounts.value = if (contact != null) {
                    contactRepository.loadContactAccounts(contact.contactId)
                } else {
                    emptyList()
                }
            }
            _isLoading.value = false
        }
    }

    fun save(contact: Contact, selections: List<ContactAccountSelection>) {
        viewModelScope.launch {
            _isSaving.value = true
            val groupsByAccount = _accountGroups.value.groupBy { it.accountName }
            var success = false

            selections.forEach { selection ->
                val groups = groupsByAccount[selection.source.name]
                    .orEmpty()
                    .filter { it.group.id in selection.groupIds }
                    .map { it.group }

                when {
                    selection.isSelected && selection.rawContactId != 0 -> {
                        val updated = contact.copy(
                            id = selection.rawContactId,
                            source = selection.source.name,
                            groups = ArrayList(groups),
                        )
                        success = contactRepository.saveContact(updated) || success
                    }

                    selection.isSelected -> {
                        val inserted = contact.copy(
                            id = 0,
                            contactId = 0,
                            source = selection.source.name,
                            groups = ArrayList(groups),
                        )
                        success = contactRepository.insertContact(inserted, selection.source) || success
                    }

                    selection.rawContactId != 0 -> {
                        val removed = contact.copy(
                            id = selection.rawContactId,
                            source = selection.source.name,
                        )
                        success = contactRepository.deleteContact(removed) || success
                    }
                }
            }

            if (success) {
                _contact.value = contact
                eventBus.tryEmit(AppEvent.RefreshContacts)
                _isSaved.value = true
            }
            _isSaving.value = false
        }
    }

    fun consumeSaved() {
        _isSaved.value = false
    }
}
