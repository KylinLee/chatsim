package io.github.kylinlee.chatsim.repository

interface SettingsRepository {
    var showTabs: Int
    var lastUsedViewPagerPage: Int

    var sorting: Int
    var startNameWithSurname: Boolean
    var nameSeparator: String
    var showOnlyContactsWithNumbers: Boolean
    var ignoredContactSources: Set<String>

    var swipeToAnswer: Boolean
    var blockUnknownNumbers: Boolean
    var blockHiddenNumbers: Boolean

    var dialpadMuted: Boolean
    var hideDialpadNumbers: Boolean
    var callOverlay: Boolean

    var useSimpleCharacters: Boolean
    var enableDeliveryReports: Boolean
    var lockScreenVisibilitySetting: Int
    var useRecycleBin: Boolean
    var recycleBinCleanBaseTime: Long
    var recycleBinCleanPeriod: Long

    var isAppPasswordProtectionOn: Boolean
    var appPasswordHash: String
    var appProtectionType: Int

    var isUsingSystemTheme: Boolean
    var fontSize: Int
    var textColor: Int
    var primaryColor: Int
    var accentColor: Int
    var backgroundColor: Int
    var customTextColor: Int
    var customBackgroundColor: Int
    var customPrimaryColor: Int
    var customAccentColor: Int

    val pinnedConversations: Set<String>

    fun pinConversation(threadId: Long)

    fun unpinConversation(threadId: Long)

    val pinnedContacts: Set<String>

    fun pinContact(id: Int)

    fun unpinContact(id: Int)

    fun getBlockedNumbers(): List<String>

    fun addBlockedNumber(number: String)

    fun isNumberBlocked(number: String): Boolean

    fun removeBlockedNumber(number: String): Boolean
}
