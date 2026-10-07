package io.github.kylinlee.chatsim.viewmodel

import androidx.lifecycle.ViewModel
import io.github.kylinlee.chatsim.data.model.SIMCard
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.repository.RoleRepository
import io.github.kylinlee.chatsim.repository.SimRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class RolesViewModel @Inject constructor(
    private val roleRepository: RoleRepository,
    private val simRepository: SimRepository,
) : ViewModel() {
    private val _simCards = MutableStateFlow<List<SIMCard>>(emptyList())
    val simCards: StateFlow<List<SIMCard>> = _simCards.asStateFlow()

    val roles: StateFlow<List<Role>> = roleRepository.roles

    val activeRole: StateFlow<Role?> = roleRepository.activeRole

    init {
        refresh()
    }

    fun refresh() {
        _simCards.value = simRepository.getActiveSubscriptions()
        roleRepository.ensureSimRoles()
    }

    fun addVirtualRole(
        label: String,
        smsPrefix: String,
        callPrefix: String,
        subscriptionId: Int? = null,
        isDefault: Boolean = false,
    ) {
        roleRepository.addVirtualRole(label, smsPrefix, callPrefix, subscriptionId, isDefault)
    }

    fun updateRole(role: Role) {
        roleRepository.updateRole(role)
    }

    fun deleteRole(id: Long) {
        roleRepository.deleteRole(id)
    }

    fun setActiveRole(id: Long) {
        roleRepository.setActiveRole(id)
    }

    fun isAvailable(role: Role): Boolean = roleRepository.isAvailable(role)

    fun simLabelFor(subscriptionId: Int?): String =
        _simCards.value.firstOrNull { it.subscriptionId == subscriptionId }?.label.orEmpty()
}
