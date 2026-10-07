package io.github.kylinlee.chatsim.domain.rule

import io.github.kylinlee.chatsim.domain.model.Role

interface RuleContext {
    val roles: List<Role>
    val activeRoleId: Long?
    val fallbackRoleId: Long?
    val now: Long
    val isBlocked: (String) -> Boolean

    fun roleById(id: Long?): Role?

    fun simRoleForSubscription(subscriptionId: Int?): Role?
}

data class RoleRuleContext(
    override val roles: List<Role>,
    override val activeRoleId: Long? = null,
    override val fallbackRoleId: Long? = null,
    val requestedRole: Role? = null,
    override val now: Long = System.currentTimeMillis(),
    override val isBlocked: (String) -> Boolean = { false },
) : RuleContext {
    override fun roleById(id: Long?): Role? {
        if (id == null) return null
        return roles.firstOrNull { it.id == id } ?: requestedRole?.takeIf { it.id == id }
    }

    override fun simRoleForSubscription(subscriptionId: Int?): Role? {
        if (subscriptionId == null) return null
        return roles.firstOrNull { !it.isVirtual && it.subscriptionId == subscriptionId }
    }
}
