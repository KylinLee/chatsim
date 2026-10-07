package io.github.kylinlee.chatsim.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.legacy.SimContactReader
import io.github.kylinlee.chatsim.domain.SimContactImporter
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SimImportState(
    val isImporting: Boolean = false,
    val isDone: Boolean = false,
    val newContacts: Int = 0,
    val mergedContacts: Int = 0,
    val addedNumbers: Int = 0,
    val ignoredRecords: Int = 0,
)

@HiltViewModel
class SimImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _state = MutableStateFlow(SimImportState())
    val state: StateFlow<SimImportState> = _state.asStateFlow()

    fun import() {
        if (_state.value.isImporting) return

        viewModelScope.launch {
            _state.value = SimImportState(isImporting = true)

            val records = withContext(Dispatchers.IO) {
                SimContactReader(context).readContacts()
            }
            val deviceContacts = contactRepository.loadContacts(showOnlyContactsWithNumbers = false)
            val plan = SimContactImporter.plan(records, deviceContacts)

            var newContacts = 0
            plan.newContacts.forEach { person ->
                val contact = Contact(
                    id = 0,
                    firstName = person.name,
                    phoneNumbers = ArrayList(person.numbers),
                    contactId = 0,
                )
                if (contactRepository.saveContact(contact)) {
                    newContacts++
                }
            }

            var mergedContacts = 0
            var addedNumbers = 0
            plan.numbersToMerge.forEach { (contactId, numbers) ->
                val existing = deviceContacts.firstOrNull { it.id == contactId } ?: return@forEach
                val updated = existing.copy(phoneNumbers = ArrayList(existing.phoneNumbers + numbers))
                if (contactRepository.saveContact(updated)) {
                    mergedContacts++
                    addedNumbers += numbers.size
                }
            }

            eventBus.tryEmit(AppEvent.RefreshContacts)

            _state.value = SimImportState(
                isDone = true,
                newContacts = newContacts,
                mergedContacts = mergedContacts,
                addedNumbers = addedNumbers,
                ignoredRecords = plan.ignoredRecords,
            )
        }
    }
}
