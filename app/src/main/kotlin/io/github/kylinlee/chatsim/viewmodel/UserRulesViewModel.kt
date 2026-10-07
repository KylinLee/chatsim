package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.model.UserTagEntity
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.rule.user.UserRule
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.RoleRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import io.github.kylinlee.chatsim.repository.UserRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class UserRulesState(
    val rules: List<UserRule> = emptyList(),
    val tags: List<UserTagEntity> = emptyList(),
    val roles: Map<Long, String> = emptyMap(),
    val trashEnabled: Boolean = true,
)

@HiltViewModel
class UserRulesViewModel @Inject constructor(
    private val repository: UserRuleRepository,
    private val roleRepository: RoleRepository,
    private val settings: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    val state: StateFlow<UserRulesState> = combine(
        repository.rules,
        repository.tags,
        roleRepository.roles,
    ) { rules, tags, roles ->
        UserRulesState(
            rules = rules,
            tags = tags,
            roles = roles.associate { it.id to it.label.ifBlank { it.displayPrefix } },
            trashEnabled = settings.useRecycleBin,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UserRulesState())

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _runResult = MutableStateFlow<Int?>(null)
    val runResult: StateFlow<Int?> = _runResult.asStateFlow()

    private val _blockedNumbers = MutableStateFlow<List<String>>(emptyList())
    val blockedNumbers: StateFlow<List<String>> = _blockedNumbers.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refresh()
        }
        refreshBlockedNumbers()
    }

    fun refreshBlockedNumbers() {
        viewModelScope.launch {
            _blockedNumbers.value = withContext(Dispatchers.IO) { settings.getBlockedNumbers() }
        }
    }

    fun removeBlockedNumber(number: String) {
        viewModelScope.launch {
            if (settings.removeBlockedNumber(number)) {
                conversationRepository.restoreConversations(number)
                _blockedNumbers.value = withContext(Dispatchers.IO) { settings.getBlockedNumbers() }
                eventBus.tryEmit(AppEvent.RefreshConversations)
                eventBus.tryEmit(AppEvent.RefreshCallLog)
            }
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setRuleEnabled(id, enabled)
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            repository.deleteRule(id)
        }
    }

    fun addTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addTag(name.trim())
        }
    }

    fun deleteTag(name: String) {
        viewModelScope.launch {
            repository.deleteTag(name)
        }
    }

    fun runRules() {
        if (_isRunning.value) return

        viewModelScope.launch {
            _isRunning.value = true
            _runResult.value = repository.runRules()
            _isRunning.value = false
        }
    }

    fun runRule(id: Long) {
        if (_isRunning.value) return

        viewModelScope.launch {
            _isRunning.value = true
            _runResult.value = repository.runRule(id)
            _isRunning.value = false
        }
    }

    fun consumeRunResult() {
        _runResult.value = null
    }
}
