package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.data.model.RuleHitEntity
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import io.github.kylinlee.chatsim.repository.SettingsRepository
import io.github.kylinlee.chatsim.repository.UserRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleHitGroup(
    val ruleId: Long,
    val ruleName: String,
    val hits: List<RuleHitEntity>,
)

@HiltViewModel
class UserRuleRecordsViewModel @Inject constructor(
    private val repository: UserRuleRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _tab = MutableStateFlow(UserRuleAction.ADD_TAG)
    val tab: StateFlow<UserRuleAction> = _tab.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _groups = MutableStateFlow<List<RuleHitGroup>>(emptyList())
    val groups: StateFlow<List<RuleHitGroup>> = _groups.asStateFlow()

    private val _collapsedRuleIds = MutableStateFlow<Set<Long>>(emptySet())
    val collapsedRuleIds: StateFlow<Set<Long>> = _collapsedRuleIds.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedHitIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedHitIds: StateFlow<Set<Long>> = _selectedHitIds.asStateFlow()

    private val _recycleBinEnabled = MutableStateFlow(settings.useRecycleBin)
    val recycleBinEnabled: StateFlow<Boolean> = _recycleBinEnabled.asStateFlow()

    init {
        load()
    }

    fun refreshRecycleBinEnabled() {
        _recycleBinEnabled.value = settings.useRecycleBin
    }

    fun selectTab(action: UserRuleAction) {
        if (_tab.value == action) return
        _tab.value = action
        _selectedTag.value = null
        cancelSelection()
        load()
    }

    fun selectTag(tag: String) {
        _selectedTag.value = if (_selectedTag.value == tag) null else tag
        cancelSelection()
        load()
    }

    fun toggleCollapsed(ruleId: Long) {
        _collapsedRuleIds.value = _collapsedRuleIds.value.toggle(ruleId)
    }

    fun startSelection() {
        _selectionMode.value = true
    }

    fun toggleHit(hitId: Long) {
        _selectedHitIds.value = _selectedHitIds.value.toggle(hitId)
    }

    fun toggleHits(hitIds: List<Long>) {
        val selected = _selectedHitIds.value
        _selectedHitIds.value = if (hitIds.all { it in selected }) {
            selected - hitIds.toSet()
        } else {
            selected + hitIds
        }
    }

    fun cancelSelection() {
        _selectionMode.value = false
        _selectedHitIds.value = emptySet()
    }

    fun deleteSelected(toRecycleBin: Boolean) {
        val hitIds = _selectedHitIds.value
        if (hitIds.isEmpty()) return

        viewModelScope.launch {
            repository.deleteRecords(hitIds, toRecycleBin)
            cancelSelection()
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            repository.refresh()
            val rules = repository.rules.value
            val rulesById = rules.associateBy { it.id }
            _tags.value = rules
                .filter { it.action == UserRuleAction.ADD_TAG }
                .flatMap { it.tags }
                .distinct()

            val tag = _selectedTag.value
            _groups.value = repository.hits(_tab.value)
                .groupBy { it.ruleId }
                .filter { (ruleId, _) -> tag == null || rulesById[ruleId]?.tags?.contains(tag) == true }
                .map { (ruleId, hits) -> RuleHitGroup(ruleId, rulesById[ruleId]?.name.orEmpty(), hits) }
        }
    }

    private fun Set<Long>.toggle(id: Long): Set<Long> = if (contains(id)) this - id else this + id
}
