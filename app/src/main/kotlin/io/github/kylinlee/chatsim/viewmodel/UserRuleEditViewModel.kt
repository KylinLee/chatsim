package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.data.model.UserTagEntity
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.rule.user.RULE_RECORD_ANY
import io.github.kylinlee.chatsim.domain.rule.user.RuleCondition
import io.github.kylinlee.chatsim.domain.rule.user.RuleConnector
import io.github.kylinlee.chatsim.domain.rule.user.RuleObject
import io.github.kylinlee.chatsim.domain.rule.user.RuleOperator
import io.github.kylinlee.chatsim.domain.rule.user.UserRule
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import io.github.kylinlee.chatsim.domain.rule.user.valueType
import io.github.kylinlee.chatsim.repository.RoleRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import io.github.kylinlee.chatsim.repository.UserRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserRuleEditState(
    val ruleId: Long = 0,
    val name: String = "",
    val action: UserRuleAction = UserRuleAction.ADD_TAG,
    val selectedTags: List<String> = emptyList(),
    val conditions: List<RuleCondition> = listOf(RuleCondition(RuleObject.PHONE, RuleOperator.CONTAINS, "")),
    val availableTags: List<UserTagEntity> = emptyList(),
    val roles: List<Role> = emptyList(),
    val trashEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = name.isNotBlank() &&
            conditions.isNotEmpty() &&
            conditions.all { it.value.isNotBlank() } &&
            conditions.dropLast(1).all { it.connector != null } &&
            (action != UserRuleAction.ADD_TAG || selectedTags.isNotEmpty())
}

@HiltViewModel
class UserRuleEditViewModel @Inject constructor(
    private val repository: UserRuleRepository,
    private val roleRepository: RoleRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(UserRuleEditState())
    val state: StateFlow<UserRuleEditState> = _state.asStateFlow()

    private var loadedRuleId: Long? = null

    fun load(ruleId: Long) {
        if (loadedRuleId == ruleId) return
        loadedRuleId = ruleId

        viewModelScope.launch {
            repository.refresh()
            val rule = repository.rules.value.firstOrNull { it.id == ruleId }
            _state.value = UserRuleEditState(
                ruleId = ruleId,
                name = rule?.name.orEmpty(),
                action = rule?.action ?: UserRuleAction.ADD_TAG,
                selectedTags = rule?.tags.orEmpty(),
                conditions = rule?.conditions?.takeIf { it.isNotEmpty() }
                    ?: listOf(RuleCondition(RuleObject.PHONE, RuleOperator.CONTAINS, "")),
                availableTags = repository.tags.value,
                roles = roleRepository.getRoles(),
                trashEnabled = settings.useRecycleBin,
            )
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun setAction(action: UserRuleAction) = _state.update { it.copy(action = action) }

    fun toggleTag(tag: String) = _state.update { state ->
        val tags = if (tag in state.selectedTags) state.selectedTags - tag else state.selectedTags + tag
        state.copy(selectedTags = tags)
    }

    fun addTag(name: String) {
        val tag = name.trim()
        if (tag.isEmpty()) return

        viewModelScope.launch {
            repository.addTag(tag)
            _state.update { state ->
                state.copy(
                    availableTags = repository.tags.value,
                    selectedTags = (state.selectedTags + tag).distinct(),
                )
            }
        }
    }

    fun setObject(index: Int, ruleObject: RuleObject) = updateCondition(index) {
        val operator = ruleObject.allowedOperators().first()
        it.copy(field = ruleObject, operator = operator, value = defaultConditionValue(ruleObject, operator))
    }

    fun setOperator(index: Int, ruleOperator: RuleOperator) = updateCondition(index) { condition ->
        val resetValue = condition.field.valueType(condition.operator) != condition.field.valueType(ruleOperator)
        condition.copy(
            operator = ruleOperator,
            value = if (resetValue) defaultConditionValue(condition.field, ruleOperator) else condition.value,
        )
    }

    /** 记录类对象在「等于」下默认匹配全部状态（全部短信 / 全部通话）。 */
    private fun defaultConditionValue(field: RuleObject, operator: RuleOperator): String = when {
        field == RuleObject.SMS_RECORD || field == RuleObject.CALL_RECORD ->
            if (operator == RuleOperator.EQUALS) RULE_RECORD_ANY else ""

        else -> ""
    }

    /** 仅切换当前条件的连接符，不追加新条件。 */
    fun setConnector(index: Int, connector: RuleConnector) = updateCondition(index) {
        it.copy(connector = connector)
    }

    fun setValue(index: Int, value: String) = updateCondition(index) { it.copy(value = value) }

    /** 点击「与/或」：设置当前行的连接符并在其后插入新的一行。 */
    fun appendCondition(index: Int, connector: RuleConnector) {
        _state.update { state ->
            val conditions = state.conditions.toMutableList()
            if (index !in conditions.indices) return@update state

            conditions[index] = conditions[index].copy(connector = connector)
            conditions.add(
                index + 1,
                RuleCondition(RuleObject.PHONE, RuleOperator.CONTAINS, ""),
            )
            state.copy(conditions = conditions)
        }
    }

    fun removeCondition(index: Int) {
        _state.update { state ->
            if (state.conditions.size <= 1 || index !in state.conditions.indices) return@update state
            state.copy(conditions = state.conditions.toMutableList().apply { removeAt(index) })
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave || current.isSaving) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            repository.saveRule(
                UserRule(
                    id = current.ruleId,
                    name = current.name.trim(),
                    action = current.action,
                    tags = current.selectedTags,
                    conditions = current.conditions,
                )
            )
            _state.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    private fun updateCondition(index: Int, transform: (RuleCondition) -> RuleCondition) {
        _state.update { state ->
            val conditions = state.conditions.toMutableList()
            if (index !in conditions.indices) return@update state

            conditions[index] = transform(conditions[index])
            state.copy(conditions = conditions)
        }
    }
}
