package io.github.kylinlee.chatsim.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ConversationsRoute : NavKey

@Serializable
data object ContactsRoute : NavKey

@Serializable
data class ContactDetailsRoute(
    val contactId: Int,
    val phoneNumber: String = "",
) : NavKey

@Serializable
data class ThreadRoute(
    val roleId: Long,
    val peerNumber: String,
    val title: String = "",
) : NavKey

@Serializable
data object NewConversationRoute : NavKey

@Serializable
data class DialpadRoute(
    val phoneNumber: String = "",
) : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object RolesRoute : NavKey

@Serializable
data object SimImportRoute : NavKey

@Serializable
data object UserRulesRoute : NavKey

@Serializable
data class UserRuleEditRoute(val ruleId: Long = 0) : NavKey

@Serializable
data object UserRuleRecordsRoute : NavKey

@Serializable
data object RecycleBinRoute : NavKey
