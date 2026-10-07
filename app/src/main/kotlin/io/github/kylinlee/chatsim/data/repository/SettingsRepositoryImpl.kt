package io.github.kylinlee.chatsim.data.repository

import android.content.Context
import io.github.kylinlee.chatsim.common.extensions.addBlockedNumber
import io.github.kylinlee.chatsim.common.extensions.deleteBlockedNumber
import io.github.kylinlee.chatsim.common.extensions.getBlockedNumbers
import io.github.kylinlee.chatsim.common.extensions.isNumberBlocked
import io.github.kylinlee.chatsim.data.local.Config
import io.github.kylinlee.chatsim.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private val config = Config(context)

    override var showTabs: Int
        get() = config.showTabs
        set(value) {
            config.showTabs = value
        }

    override var lastUsedViewPagerPage: Int
        get() = config.lastUsedViewPagerPage
        set(value) {
            config.lastUsedViewPagerPage = value
        }

    override var sorting: Int
        get() = config.sorting
        set(value) {
            config.sorting = value
        }

    override var startNameWithSurname: Boolean
        get() = config.startNameWithSurname
        set(value) {
            config.startNameWithSurname = value
        }

    override var nameSeparator: String
        get() = config.nameSeparator
        set(value) {
            config.nameSeparator = value
        }

    override var showOnlyContactsWithNumbers: Boolean
        get() = config.showOnlyContactsWithNumbers
        set(value) {
            config.showOnlyContactsWithNumbers = value
        }

    override var ignoredContactSources: Set<String>
        get() = config.ignoredContactSources
        set(value) {
            config.ignoredContactSources = value.toHashSet()
        }

    override var swipeToAnswer: Boolean
        get() = config.swipeToAnswer
        set(value) {
            config.swipeToAnswer = value
        }

    override var blockUnknownNumbers: Boolean
        get() = config.blockUnknownNumbers
        set(value) {
            config.blockUnknownNumbers = value
        }

    override var blockHiddenNumbers: Boolean
        get() = config.blockHiddenNumbers
        set(value) {
            config.blockHiddenNumbers = value
        }

    override var dialpadMuted: Boolean
        get() = config.dialpadMuted
        set(value) {
            config.dialpadMuted = value
        }

    override var hideDialpadNumbers: Boolean
        get() = config.hideDialpadNumbers
        set(value) {
            config.hideDialpadNumbers = value
        }

    override var callOverlay: Boolean
        get() = config.callOverlay
        set(value) {
            config.callOverlay = value
        }

    override var useSimpleCharacters: Boolean
        get() = config.useSimpleCharacters
        set(value) {
            config.useSimpleCharacters = value
        }

    override var enableDeliveryReports: Boolean
        get() = config.enableDeliveryReports
        set(value) {
            config.enableDeliveryReports = value
        }

    override var lockScreenVisibilitySetting: Int
        get() = config.lockScreenVisibilitySetting
        set(value) {
            config.lockScreenVisibilitySetting = value
        }

    override var useRecycleBin: Boolean
        get() = config.useRecycleBin
        set(value) {
            config.useRecycleBin = value
        }

    override var recycleBinCleanBaseTime: Long
        get() = config.recycleBinCleanBaseTime
        set(value) {
            config.recycleBinCleanBaseTime = value
        }

    override var recycleBinCleanPeriod: Long
        get() = config.recycleBinCleanPeriod
        set(value) {
            config.recycleBinCleanPeriod = value
        }

    override var isAppPasswordProtectionOn: Boolean
        get() = config.isAppPasswordProtectionOn
        set(value) {
            config.isAppPasswordProtectionOn = value
        }

    override var appPasswordHash: String
        get() = config.appPasswordHash
        set(value) {
            config.appPasswordHash = value
        }

    override var appProtectionType: Int
        get() = config.appProtectionType
        set(value) {
            config.appProtectionType = value
        }

    override var isUsingSystemTheme: Boolean
        get() = config.isUsingSystemTheme
        set(value) {
            config.isUsingSystemTheme = value
        }

    override var fontSize: Int
        get() = config.fontSize
        set(value) {
            config.fontSize = value
        }

    override var textColor: Int
        get() = config.textColor
        set(value) {
            config.textColor = value
        }

    override var primaryColor: Int
        get() = config.primaryColor
        set(value) {
            config.primaryColor = value
        }

    override var accentColor: Int
        get() = config.accentColor
        set(value) {
            config.accentColor = value
        }

    override var backgroundColor: Int
        get() = config.backgroundColor
        set(value) {
            config.backgroundColor = value
        }

    override var customTextColor: Int
        get() = config.customTextColor
        set(value) {
            config.customTextColor = value
        }

    override var customBackgroundColor: Int
        get() = config.customBackgroundColor
        set(value) {
            config.customBackgroundColor = value
        }

    override var customPrimaryColor: Int
        get() = config.customPrimaryColor
        set(value) {
            config.customPrimaryColor = value
        }

    override var customAccentColor: Int
        get() = config.customAccentColor
        set(value) {
            config.customAccentColor = value
        }

    override val pinnedConversations: Set<String>
        get() = config.pinnedConversations

    override fun pinConversation(threadId: Long) {
        config.addPinnedConversationByThreadId(threadId)
    }

    override fun unpinConversation(threadId: Long) {
        config.removePinnedConversationByThreadId(threadId)
    }

    override val pinnedContacts: Set<String>
        get() = config.pinnedContacts

    override fun pinContact(id: Int) {
        config.addPinnedContact(id)
    }

    override fun unpinContact(id: Int) {
        config.removePinnedContact(id)
    }

    override fun getBlockedNumbers(): List<String> = context.getBlockedNumbers().map { it.number }

    override fun addBlockedNumber(number: String) {
        context.addBlockedNumber(number)
    }

    override fun isNumberBlocked(number: String): Boolean = context.isNumberBlocked(number)

    override fun removeBlockedNumber(number: String): Boolean = context.deleteBlockedNumber(number)
}
