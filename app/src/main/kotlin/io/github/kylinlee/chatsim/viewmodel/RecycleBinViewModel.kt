package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.MessageRepository
import io.github.kylinlee.chatsim.repository.RoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecycleBinRecord(
    val message: Message,
    val conversationId: Long,
    val title: String,
)

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val roleRepository: RoleRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _records = MutableStateFlow<List<RecycleBinRecord>>(emptyList())
    val records: StateFlow<List<RecycleBinRecord>> = _records.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val roles: StateFlow<List<Role>> = roleRepository.roles

    /** 本页临时选择角色：初始跟随会话列表的激活角色，切换只影响本页。 */
    private val _selectedRoleId = MutableStateFlow(roleRepository.getActiveRole()?.id)
    val selectedRoleId: StateFlow<Long?> = _selectedRoleId.asStateFlow()

    init {
        refresh()
    }

    fun selectRole(roleId: Long) {
        if (_selectedRoleId.value == roleId) return
        _selectedRoleId.value = roleId
        cancelSelection()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val roleId = _selectedRoleId.value
            val records = if (roleId == null) {
                emptyList()
            } else {
                val threads = conversationRepository.getRecycleBinConversations()
                    .filter { it.roleId == roleId }
                    .associateBy { it.conversationId }
                messageRepository.getRecycleBinMessages()
                    .mapNotNull { message ->
                        val thread = threads[message.conversationId] ?: return@mapNotNull null
                        RecycleBinRecord(
                            message = message,
                            conversationId = thread.conversationId,
                            title = thread.title,
                        )
                    }
                    .sortedByDescending { it.message.date }
            }

            _records.value = records
            _selectedIds.value = _selectedIds.value.intersect(records.map { it.message.id }.toSet())
            if (_selectedIds.value.isEmpty()) {
                _selectionMode.value = false
            }
        }
    }

    fun startSelection() {
        _selectionMode.value = true
    }

    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.let { if (id in it) it - id else it + id }
    }

    fun cancelSelection() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun selectAll() {
        _selectedIds.value = _records.value.map { it.message.id }.toSet()
    }

    fun restoreSelected() {
        val selected = selectedRecords()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            selected.forEach { messageRepository.restoreMessage(it.message.id) }
            selected.map { it.conversationId }.distinct().forEach { conversationRepository.refreshConversation(it) }
            eventBus.tryEmit(AppEvent.RefreshMessages)
            if (selected.any { it.message.isCall }) {
                eventBus.tryEmit(AppEvent.RefreshCallLog)
            }
            cancelSelection()
            refresh()
        }
    }

    private fun selectedRecords(): List<RecycleBinRecord> {
        val ids = _selectedIds.value
        return if (ids.isEmpty()) emptyList() else _records.value.filter { it.message.id in ids }
    }
}
