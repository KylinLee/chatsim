package io.github.kylinlee.chatsim.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class RoleKind {
    SIM,
    VIRTUAL,
}

/** 通信通道：小号前缀按通道区分，信息前缀与拨号前缀可以不同。 */
enum class RoleChannel {
    SMS,
    CALL,
}

@Serializable
data class Role(
    val id: Long,
    val label: String,
    val kind: RoleKind = RoleKind.VIRTUAL,
    val smsPrefix: String = "",
    val callPrefix: String = "",
    val subscriptionId: Int? = null,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
) {
    val isVirtual: Boolean
        get() = kind == RoleKind.VIRTUAL

    /** 通道对应的前缀；为空表示该通道不生效（不回退到另一通道）。 */
    fun prefixFor(channel: RoleChannel): String = when (channel) {
        RoleChannel.SMS -> smsPrefix
        RoleChannel.CALL -> callPrefix
    }

    /** 仅用于界面展示的简称。 */
    val displayPrefix: String
        get() = smsPrefix.ifBlank { callPrefix }
}
