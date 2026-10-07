package io.github.kylinlee.chatsim.data.local

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle
import io.github.kylinlee.chatsim.common.extensions.getPhoneAccountHandleModel
import io.github.kylinlee.chatsim.common.extensions.putPhoneAccountHandle
import io.github.kylinlee.chatsim.common.*
import io.github.kylinlee.chatsim.data.legacy.*
import io.github.kylinlee.chatsim.data.local.*
import io.github.kylinlee.chatsim.common.extensions.getDefaultKeyboardHeight
import io.github.kylinlee.chatsim.data.model.Conversation
import io.github.kylinlee.chatsim.domain.scheduler.DEFAULT_RECYCLE_BIN_CLEAN_PERIOD_INDEX
import io.github.kylinlee.chatsim.domain.scheduler.RECYCLE_BIN_CLEAN_PERIODS

class Config(context: Context) {
    val context: Context = context.applicationContext

    /** 与 Simple-Commons BaseConfig 使用同一偏好文件，保留旧设置。 */
    val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_NAME_SEPARATOR = ""

        fun newInstance(context: Context) = Config(context)
    }

    // ================ Migrated from Simple-Commons BaseConfig ================

    var lastUsedViewPagerPage: Int
        get() = prefs.getInt(LAST_USED_VIEW_PAGER_PAGE, 0)
        set(lastUsedViewPagerPage) = prefs.edit().putInt(LAST_USED_VIEW_PAGER_PAGE, lastUsedViewPagerPage).apply()

    var sorting: Int
        get() = prefs.getInt(SORT_ORDER, SORT_BY_NAME)
        set(sorting) = prefs.edit().putInt(SORT_ORDER, sorting).apply()

    var startNameWithSurname: Boolean
        get() = prefs.getBoolean(START_NAME_WITH_SURNAME, false)
        set(startNameWithSurname) = prefs.edit().putBoolean(START_NAME_WITH_SURNAME, startNameWithSurname).apply()

    var showOnlyContactsWithNumbers: Boolean
        get() = prefs.getBoolean(SHOW_ONLY_CONTACTS_WITH_NUMBERS, false)
        set(showOnlyContactsWithNumbers) = prefs.edit().putBoolean(SHOW_ONLY_CONTACTS_WITH_NUMBERS, showOnlyContactsWithNumbers).apply()

    var mergeDuplicateContacts: Boolean
        get() = prefs.getBoolean(MERGE_DUPLICATE_CONTACTS, true)
        set(mergeDuplicateContacts) = prefs.edit().putBoolean(MERGE_DUPLICATE_CONTACTS, mergeDuplicateContacts).apply()

    var wasLocalAccountInitialized: Boolean
        get() = prefs.getBoolean(WAS_LOCAL_ACCOUNT_INITIALIZED, false)
        set(wasLocalAccountInitialized) = prefs.edit().putBoolean(WAS_LOCAL_ACCOUNT_INITIALIZED, wasLocalAccountInitialized).apply()

    var ignoredContactSources: Set<String>
        get() = prefs.getStringSet(IGNORED_CONTACT_SOURCES, hashSetOf(".")) ?: hashSetOf(".")
        set(value) = prefs.edit().remove(IGNORED_CONTACT_SOURCES).putStringSet(IGNORED_CONTACT_SOURCES, value.toHashSet()).apply()

    var blockUnknownNumbers: Boolean
        get() = prefs.getBoolean(BLOCK_UNKNOWN_NUMBERS, false)
        set(blockUnknownNumbers) = prefs.edit().putBoolean(BLOCK_UNKNOWN_NUMBERS, blockUnknownNumbers).apply()

    var blockHiddenNumbers: Boolean
        get() = prefs.getBoolean(BLOCK_HIDDEN_NUMBERS, false)
        set(blockHiddenNumbers) = prefs.edit().putBoolean(BLOCK_HIDDEN_NUMBERS, blockHiddenNumbers).apply()

    var isAppPasswordProtectionOn: Boolean
        get() = prefs.getBoolean(APP_PASSWORD_PROTECTION, false)
        set(isAppPasswordProtectionOn) = prefs.edit().putBoolean(APP_PASSWORD_PROTECTION, isAppPasswordProtectionOn).apply()

    var appPasswordHash: String
        get() = prefs.getString(APP_PASSWORD_HASH, "")!!
        set(appPasswordHash) = prefs.edit().putString(APP_PASSWORD_HASH, appPasswordHash).apply()

    var appProtectionType: Int
        get() = prefs.getInt(APP_PROTECTION_TYPE, PROTECTION_PATTERN)
        set(appProtectionType) = prefs.edit().putInt(APP_PROTECTION_TYPE, appProtectionType).apply()

    var isUsingSystemTheme: Boolean
        get() = prefs.getBoolean(IS_USING_SYSTEM_THEME, isSPlus())
        set(isUsingSystemTheme) = prefs.edit().putBoolean(IS_USING_SYSTEM_THEME, isUsingSystemTheme).apply()

    var fontSize: Int
        get() = prefs.getInt(FONT_SIZE, FONT_SIZE_MEDIUM)
        set(fontSize) = prefs.edit().putInt(FONT_SIZE, fontSize).apply()

    var textColor: Int
        get() = prefs.getInt(TEXT_COLOR, DEFAULT_TEXT_COLOR)
        set(textColor) = prefs.edit().putInt(TEXT_COLOR, textColor).apply()

    var backgroundColor: Int
        get() = prefs.getInt(BACKGROUND_COLOR, DEFAULT_BACKGROUND_COLOR)
        set(backgroundColor) = prefs.edit().putInt(BACKGROUND_COLOR, backgroundColor).apply()

    var primaryColor: Int
        get() = prefs.getInt(PRIMARY_COLOR, context.getColor(io.github.kylinlee.chatsim.R.color.color_primary))
        set(primaryColor) = prefs.edit().putInt(PRIMARY_COLOR, primaryColor).apply()

    var accentColor: Int
        get() = prefs.getInt(ACCENT_COLOR, context.getColor(io.github.kylinlee.chatsim.R.color.color_primary))
        set(accentColor) = prefs.edit().putInt(ACCENT_COLOR, accentColor).apply()

    var customTextColor: Int
        get() = prefs.getInt(CUSTOM_TEXT_COLOR, textColor)
        set(customTextColor) = prefs.edit().putInt(CUSTOM_TEXT_COLOR, customTextColor).apply()

    var customBackgroundColor: Int
        get() = prefs.getInt(CUSTOM_BACKGROUND_COLOR, backgroundColor)
        set(customBackgroundColor) = prefs.edit().putInt(CUSTOM_BACKGROUND_COLOR, customBackgroundColor).apply()

    var customPrimaryColor: Int
        get() = prefs.getInt(CUSTOM_PRIMARY_COLOR, primaryColor)
        set(customPrimaryColor) = prefs.edit().putInt(CUSTOM_PRIMARY_COLOR, customPrimaryColor).apply()

    var customAccentColor: Int
        get() = prefs.getInt(CUSTOM_ACCENT_COLOR, accentColor)
        set(customAccentColor) = prefs.edit().putInt(CUSTOM_ACCENT_COLOR, customAccentColor).apply()

    // ================ Dialer-specific properties ================

    fun saveCustomSIM(number: String, handle: PhoneAccountHandle) {
        prefs.edit().putPhoneAccountHandle(REMEMBER_SIM_PREFIX + number, handle).apply()
    }

    fun getCustomSIM(number: String): PhoneAccountHandle? {
        val myPhoneAccountHandle = prefs.getPhoneAccountHandleModel(REMEMBER_SIM_PREFIX + number, null)
        return if (myPhoneAccountHandle != null) {
            val packageName = myPhoneAccountHandle.packageName
            val className = myPhoneAccountHandle.className
            val componentName = ComponentName(packageName, className)
            val id = myPhoneAccountHandle.id
            PhoneAccountHandle(componentName, id)
        } else {
            null
        }
    }

    fun removeCustomSIM(number: String) {
        prefs.edit().remove(REMEMBER_SIM_PREFIX + number).apply()
    }

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    var swipeToAnswer: Boolean
        get() = prefs.getBoolean(SWIPE_TO_ANSWER, false)
        set(swipeToAnswer) = prefs.edit().putBoolean(SWIPE_TO_ANSWER, swipeToAnswer).apply()

    var wasOverlaySnackbarConfirmed: Boolean
        get() = prefs.getBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, false)
        set(wasOverlaySnackbarConfirmed) = prefs.edit().putBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, wasOverlaySnackbarConfirmed).apply()

    var hideDialpadNumbers: Boolean
        get() = prefs.getBoolean(HIDE_DIALPAD_NUMBERS, false)
        set(hideDialpadNumbers) = prefs.edit().putBoolean(HIDE_DIALPAD_NUMBERS, hideDialpadNumbers).apply()

    var dialpadMuted: Boolean
        get() = prefs.getBoolean(DIALPAD_MUTED, false)
        set(dialpadMuted) = prefs.edit().putBoolean(DIALPAD_MUTED, dialpadMuted).apply()

    var callOverlay: Boolean
        get() = prefs.getBoolean(CALL_OVERLAY, true)
        set(callOverlay) = prefs.edit().putBoolean(CALL_OVERLAY, callOverlay).apply()

    var callOverlayX: Int
        get() = prefs.getInt(CALL_OVERLAY_X, -1)
        set(callOverlayX) = prefs.edit().putInt(CALL_OVERLAY_X, callOverlayX).apply()

    var callOverlayY: Int
        get() = prefs.getInt(CALL_OVERLAY_Y, -1)
        set(callOverlayY) = prefs.edit().putInt(CALL_OVERLAY_Y, callOverlayY).apply()

    var nameSeparator: String
        get() = prefs.getString(NAME_SEPARATOR, DEFAULT_NAME_SEPARATOR) ?: DEFAULT_NAME_SEPARATOR
        set(nameSeparator) = prefs.edit().putString(NAME_SEPARATOR, nameSeparator).apply()

    // ================ SMS Messenger-specific properties ================

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit().putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, false)
        set(enableDeliveryReports) = prefs.edit().putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit().putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var pinnedConversations: Set<String>
        get() = prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!
        set(pinnedConversations) = prefs.edit().putStringSet(PINNED_CONVERSATIONS, pinnedConversations).apply()

    fun addPinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.plus(threadId.toString())
    }

    fun addPinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.plus(conversations.map { it.conversationId.toString() })
    }

    fun removePinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.minus(threadId.toString())
    }

    fun removePinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.minus(conversations.map { it.conversationId.toString() })
    }

    var pinnedContacts: Set<String>
        get() = prefs.getStringSet(PINNED_CONTACTS, HashSet<String>())!!
        set(pinnedContacts) = prefs.edit().putStringSet(PINNED_CONTACTS, pinnedContacts).apply()

    fun addPinnedContact(id: Int) {
        pinnedContacts = pinnedContacts.plus(id.toString())
    }

    fun removePinnedContact(id: Int) {
        pinnedContacts = pinnedContacts.minus(id.toString())
    }

    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit().putBoolean(EXPORT_SMS, exportSms).apply()

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit().putBoolean(EXPORT_MMS, exportMms).apply()

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit().putBoolean(IMPORT_SMS, importSms).apply()

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit().putBoolean(IMPORT_MMS, importMms).apply()

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit().putBoolean(WAS_DB_CLEARED, wasDbCleared).apply()

    var keyboardHeight: Int
        get() = prefs.getInt(SOFT_KEYBOARD_HEIGHT, context.getDefaultKeyboardHeight())
        set(keyboardHeight) = prefs.edit().putInt(SOFT_KEYBOARD_HEIGHT, keyboardHeight).apply()

    var useRecycleBin: Boolean
        get() = prefs.getBoolean(USE_RECYCLE_BIN, false)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    /** 自动清空的调度锚点，精确到分；首次使用时初始化为当前时间。 */
    var recycleBinCleanBaseTime: Long
        get() = prefs.getLong(RECYCLE_BIN_CLEAN_BASE_TIME, 0L)
        set(recycleBinCleanBaseTime) = prefs.edit().putLong(RECYCLE_BIN_CLEAN_BASE_TIME, recycleBinCleanBaseTime).apply()

    var recycleBinCleanPeriod: Long
        get() = prefs.getLong(
            RECYCLE_BIN_CLEAN_PERIOD,
            RECYCLE_BIN_CLEAN_PERIODS[DEFAULT_RECYCLE_BIN_CLEAN_PERIOD_INDEX],
        )
        set(recycleBinCleanPeriod) = prefs.edit().putLong(RECYCLE_BIN_CLEAN_PERIOD, recycleBinCleanPeriod).apply()

    init {
        if (!prefs.contains(START_NAME_WITH_SURNAME)) {
            startNameWithSurname = true
        }
        if (!prefs.contains(RECYCLE_BIN_CLEAN_BASE_TIME)) {
            recycleBinCleanBaseTime = System.currentTimeMillis() / MINUTE_MILLIS * MINUTE_MILLIS
        }
    }
}

private val DEFAULT_TEXT_COLOR = 0xFFEEEEEE.toInt()
private val DEFAULT_BACKGROUND_COLOR = 0xFF303030.toInt()
private const val MINUTE_MILLIS = 60_000L
