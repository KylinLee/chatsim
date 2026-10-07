package io.github.kylinlee.chatsim.data.repository

import io.github.kylinlee.chatsim.data.local.RoleStore
import io.github.kylinlee.chatsim.domain.RolePrefixer
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.domain.model.RoleKind
import io.github.kylinlee.chatsim.repository.RoleRepository
import io.github.kylinlee.chatsim.repository.SimRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoleRepositoryImpl @Inject constructor(
    private val store: RoleStore,
    private val simRepository: SimRepository,
) : RoleRepository {
    private val _roles = MutableStateFlow(store.loadRoles())
    private val _activeRole = MutableStateFlow(resolveActive(store.loadActiveId(), _roles.value))
    private var activeSubscriptionIds: Set<Int> = emptySet()

    override val roles: StateFlow<List<Role>> = _roles.asStateFlow()
    override val activeRole: StateFlow<Role?> = _activeRole.asStateFlow()

    init {
        ensureSimRoles()
    }

    override fun getRoles(): List<Role> = _roles.value

    override fun getRole(id: Long): Role? = _roles.value.firstOrNull { it.id == id }

    override fun getActiveRole(): Role? = _activeRole.value

    override fun getSimRoleForSubscription(subscriptionId: Int?): Role? {
        if (subscriptionId == null) return null
        return _roles.value.firstOrNull { !it.isVirtual && it.subscriptionId == subscriptionId }
    }

    override fun setActiveRole(id: Long) {
        val role = _roles.value.firstOrNull { it.id == id } ?: return
        store.saveActiveId(id)
        _activeRole.value = role
    }

    override fun ensureSimRoles(): List<Role> {
        val subscriptions = simRepository.getActiveSubscriptions()
        activeSubscriptionIds = subscriptions.map { it.subscriptionId }.toSet()

        val current = _roles.value
        val missing = subscriptions.mapNotNull { sim ->
            if (current.any { !it.isVirtual && it.subscriptionId == sim.subscriptionId }) {
                null
            } else {
                Role(
                    id = nextId(current + emptyList()),
                    label = sim.label,
                    kind = RoleKind.SIM,
                    subscriptionId = sim.subscriptionId,
                )
            }
        }

        if (missing.isNotEmpty()) {
            persist(current + missing)
        }

        val resolvedActive = resolveActive(store.loadActiveId(), _roles.value)
        if (resolvedActive?.id != _activeRole.value?.id) {
            _activeRole.value = resolvedActive
        }

        return _roles.value
    }

    override fun isAvailable(role: Role?): Boolean {
        if (role == null) return false
        if (!role.enabled) return false
        val subscriptionId = role.subscriptionId ?: return false
        return subscriptionId in activeSubscriptionIds
    }

    override fun addVirtualRole(
        label: String,
        smsPrefix: String,
        callPrefix: String,
        subscriptionId: Int?,
        isDefault: Boolean,
    ): Role {
        val role = Role(
            id = nextId(_roles.value),
            label = label.trim(),
            kind = RoleKind.VIRTUAL,
            smsPrefix = smsPrefix.trim(),
            callPrefix = callPrefix.trim(),
            subscriptionId = subscriptionId,
            enabled = true,
            isDefault = isDefault,
        )

        persist(_roles.value + role)

        if (isDefault || _activeRole.value == null) {
            setActiveRole(role.id)
        }

        return role
    }

    override fun updateRole(role: Role) {
        persist(_roles.value.map { if (it.id == role.id) role else it })

        if (_activeRole.value?.id == role.id) {
            _activeRole.value = resolveActive(role.id, _roles.value)
        }
    }

    override fun deleteRole(id: Long) {
        val role = getRole(id) ?: return
        if (!role.isVirtual) return

        persist(_roles.value.filterNot { it.id == id })

        if (_activeRole.value?.id == id) {
            _activeRole.value = resolveActive(null, _roles.value)
        }
    }

    override fun applyPrefix(number: String, role: Role?, channel: RoleChannel): String {
        if (role == null) return number
        if (RolePrefixer.isAlreadyPrefixed(number, _roles.value, channel)) return number
        return RolePrefixer.applyPrefix(number, role, channel)
    }

    override fun stripPrefix(number: String, channel: RoleChannel): String =
        RolePrefixer.stripPrefix(number, _roles.value, channel)

    override fun matchRole(number: String, channel: RoleChannel): Role? =
        RolePrefixer.matchRole(number, _roles.value, channel)

    private fun persist(roles: List<Role>) {
        store.saveRoles(roles)
        _roles.value = roles
    }

    private fun nextId(roles: List<Role>): Long = (roles.maxOfOrNull { it.id } ?: 0L) + 1

    private fun resolveActive(id: Long?, roles: List<Role>): Role? {
        if (id != null) {
            roles.firstOrNull { it.id == id && it.enabled }?.let { return it }
        }

        return roles.firstOrNull { it.isDefault && it.enabled }
            ?: roles.firstOrNull { !it.isVirtual && it.enabled }
            ?: roles.firstOrNull { it.enabled }
    }
}
