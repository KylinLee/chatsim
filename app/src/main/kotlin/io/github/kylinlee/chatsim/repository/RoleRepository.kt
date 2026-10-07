package io.github.kylinlee.chatsim.repository

import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import kotlinx.coroutines.flow.StateFlow

interface RoleRepository {
    val roles: StateFlow<List<Role>>
    val activeRole: StateFlow<Role?>

    fun getRoles(): List<Role>

    fun getRole(id: Long): Role?

    fun getActiveRole(): Role?

    fun getSimRoleForSubscription(subscriptionId: Int?): Role?

    fun setActiveRole(id: Long)

    /** Registers a persisted role for every active subscription and refreshes availability. */
    fun ensureSimRoles(): List<Role>

    fun isAvailable(role: Role?): Boolean

    fun addVirtualRole(
        label: String,
        smsPrefix: String,
        callPrefix: String,
        subscriptionId: Int? = null,
        isDefault: Boolean = false,
    ): Role

    fun updateRole(role: Role)

    fun deleteRole(id: Long)

    fun applyPrefix(number: String, role: Role?, channel: RoleChannel): String

    fun stripPrefix(number: String, channel: RoleChannel): String

    fun matchRole(number: String, channel: RoleChannel): Role?
}
