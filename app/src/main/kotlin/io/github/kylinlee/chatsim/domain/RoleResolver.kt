package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel

data class ResolvedNumber(
    val peerNumber: String,
    val role: Role,
)

data class OutgoingRoute(
    val number: String,
    val subscriptionId: Int?,
    val roleId: Long?,
)

/**
 * 角色处理管线（线性、无嵌套分支）：
 *
 * 入站：虚拟前缀（最长）→ 订阅 SIM 角色 → 兜底角色（仅 SIM；通话记录无兜底）；
 * 出站：指定角色 → 按通道加前缀 → 角色订阅。
 */
object RoleResolver {
    fun resolveIncoming(
        channel: RoleChannel,
        address: String,
        subscriptionId: Int?,
        roles: List<Role>,
        fallbackRoleId: Long?,
    ): ResolvedNumber? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return null

        val matched = RolePrefixer.matchRole(trimmed, roles, channel)
        val role = matched
            ?: roles.firstOrNull { !it.isVirtual && it.subscriptionId == subscriptionId }
            ?: fallbackSimRole(roles, fallbackRoleId)
            ?: return null

        val peerNumber = matched?.let { RolePrefixer.stripPrefix(trimmed, it, channel) } ?: trimmed
        return ResolvedNumber(peerNumber, role)
    }

    /**
     * 兜底角色只能是 SIM 角色：虚拟角色必须由前缀识别，否则无前缀的记录会被错误归到小号名下。
     * [fallbackRoleId] 为 null 表示不兜底（通话记录）。
     */
    private fun fallbackSimRole(roles: List<Role>, fallbackRoleId: Long?): Role? {
        if (fallbackRoleId == null) return null

        return roles.firstOrNull { it.id == fallbackRoleId && !it.isVirtual }
            ?: roles.firstOrNull { !it.isVirtual && it.isDefault }
            ?: roles.firstOrNull { !it.isVirtual && it.enabled }
    }

    fun resolveOutgoing(
        channel: RoleChannel,
        address: String,
        role: Role?,
    ): OutgoingRoute = OutgoingRoute(
        number = RolePrefixer.applyPrefix(address, role, channel),
        subscriptionId = role?.subscriptionId,
        roleId = role?.id,
    )
}
