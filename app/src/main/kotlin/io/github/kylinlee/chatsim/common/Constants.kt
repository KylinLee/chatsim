package io.github.kylinlee.chatsim.common

import android.os.Build
import android.os.Looper
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.domain.model.AppEvent
import kotlin.math.abs
import kotlin.random.Random

// shared prefs
const val REMEMBER_SIM_PREFIX = "remember_sim_"
const val SWIPE_TO_ANSWER = "swipe_to_answer"
const val SHOW_TABS = "show_tabs"
const val FAVORITES_CONTACTS_ORDER = "favorites_contacts_order"
const val FAVORITES_CUSTOM_ORDER_SELECTED = "favorites_custom_order_selected"
const val WAS_OVERLAY_SNACKBAR_CONFIRMED = "was_overlay_snackbar_confirmed"
const val DIALPAD_MUTED = "dialpad_muted"
const val HIDE_DIALPAD_NUMBERS = "hide_dialpad_numbers"
const val CALL_OVERLAY = "call_overlay"
const val CALL_OVERLAY_X = "call_overlay_x"
const val CALL_OVERLAY_Y = "call_overlay_y"
const val NAME_SEPARATOR = "name_separator"

const val TAB_CONTACTS = 1
const val TAB_CALL_HISTORY = 4
const val TAB_MESSAGES = 128

const val ALL_TABS_MASK = TAB_CONTACTS or TAB_CALL_HISTORY or TAB_MESSAGES

val tabsList = arrayListOf(TAB_CONTACTS, TAB_CALL_HISTORY, TAB_MESSAGES)

private const val ACTION_PATH = "io.github.kylinlee.chatsim.action."
const val ACCEPT_CALL = ACTION_PATH + "accept_call"
const val DECLINE_CALL = ACTION_PATH + "decline_call"

const val DIALPAD_TONE_LENGTH_MS = 150L // The length of DTMF tones in milliseconds

const val MIN_RECENTS_THRESHOLD = 30

const val THREAD_ID = "thread_id"
const val CONVERSATION_ID = "conversation_id"
const val ROLE_ID = "role_id"
const val THREAD_TITLE = "thread_title"
const val THREAD_TEXT = "thread_text"
const val THREAD_NUMBER = "thread_number"
const val THREAD_ATTACHMENT_URI = "thread_attachment_uri"
const val THREAD_ATTACHMENT_URIS = "thread_attachment_uris"
const val SEARCHED_MESSAGE_ID = "searched_message_id"
const val USE_SIM_ID_PREFIX = "use_sim_id_"
const val NOTIFICATION_CHANNEL = "simple_sms_messenger"
const val USE_SIMPLE_CHARACTERS = "use_simple_characters"
const val LOCK_SCREEN_VISIBILITY = "lock_screen_visibility"
const val ENABLE_DELIVERY_REPORTS = "enable_delivery_reports"
const val PINNED_CONVERSATIONS = "pinned_conversations"
const val PINNED_CONTACTS = "pinned_contacts"
const val EXPORT_SMS = "export_sms"
const val EXPORT_MMS = "export_mms"
const val JSON_FILE_EXTENSION = ".json"
const val JSON_MIME_TYPE = "application/json"
const val XML_MIME_TYPE = "text/xml"
const val TXT_MIME_TYPE = "text/plain"
const val IMPORT_SMS = "import_sms"
const val IMPORT_MMS = "import_mms"
const val WAS_DB_CLEARED = "was_db_cleared_4"
const val EXTRA_VCARD_URI = "vcard"
const val TASK_ID = "scheduled_task_id"
const val SOFT_KEYBOARD_HEIGHT = "soft_keyboard_height"
const val IS_MMS = "is_mms"
const val MESSAGE_ID = "message_id"
const val USE_RECYCLE_BIN = "use_recycle_bin"
const val RECYCLE_BIN_CLEAN_BASE_TIME = "recycle_bin_clean_base_time"
const val RECYCLE_BIN_CLEAN_PERIOD = "recycle_bin_clean_period"
const val IS_RECYCLE_BIN = "is_recycle_bin"

const val MARK_AS_READ = ACTION_PATH + "mark_as_read"
const val REPLY = ACTION_PATH + "reply"

// view types for the thread list view
const val THREAD_DATE_TIME = 1
const val THREAD_RECEIVED_MESSAGE = 2
const val THREAD_SENT_MESSAGE = 3
const val THREAD_SENT_MESSAGE_ERROR = 4
const val THREAD_SENT_MESSAGE_SENT = 5
const val THREAD_SENT_MESSAGE_SENDING = 6
const val THREAD_LOADING = 7

// view types for attachment list
const val ATTACHMENT_DOCUMENT = 7
const val ATTACHMENT_MEDIA = 8
const val ATTACHMENT_VCARD = 9

// lock screen visibility constants
const val LOCK_SCREEN_SENDER_MESSAGE = 1
const val LOCK_SCREEN_SENDER = 2
const val LOCK_SCREEN_NOTHING = 3

const val FILE_SIZE_NONE = -1L

const val MESSAGES_LIMIT = 30

// intent launch request codes
const val PICK_PHOTO_INTENT = 42
const val PICK_VIDEO_INTENT = 49
const val PICK_SAVE_FILE_INTENT = 43
const val CAPTURE_PHOTO_INTENT = 44
const val CAPTURE_VIDEO_INTENT = 45
const val CAPTURE_AUDIO_INTENT = 46
const val PICK_DOCUMENT_INTENT = 47
const val PICK_CONTACT_INTENT = 48

fun refreshMessages() {
    AppEventBus.tryEmit(AppEvent.RefreshMessages)
}

/** Not to be used with real messages persisted in the telephony db. This is for internal use only (e.g. scheduled messages, notification ids etc). */
fun generateRandomId(length: Int = 9): Long {
    val millis = System.currentTimeMillis()
    val random = abs(Random(millis).nextLong())
    return random.toString().takeLast(length).toLong()
}

// ===== Migrated from Simple-Commons =====

const val PREFS_KEY = "Prefs"
const val LAST_USED_VIEW_PAGER_PAGE = "last_used_view_pager_page"
const val SORT_ORDER = "sort_order"
const val SHOW_ONLY_CONTACTS_WITH_NUMBERS = "show_only_contacts_with_numbers"
const val IGNORED_CONTACT_SOURCES = "ignored_contact_sources_2"
const val BLOCK_UNKNOWN_NUMBERS = "block_unknown_numbers"
const val BLOCK_HIDDEN_NUMBERS = "block_hidden_numbers"
const val APP_PASSWORD_PROTECTION = "app_password_protection"
const val APP_PASSWORD_HASH = "app_password_hash"
const val APP_PROTECTION_TYPE = "app_protection_type"
const val IS_USING_SYSTEM_THEME = "is_using_system_theme"
const val FONT_SIZE = "font_size"
const val TEXT_COLOR = "text_color"
const val BACKGROUND_COLOR = "background_color"
const val PRIMARY_COLOR = "primary_color_2"
const val ACCENT_COLOR = "accent_color"
const val CUSTOM_TEXT_COLOR = "custom_text_color"
const val CUSTOM_BACKGROUND_COLOR = "custom_background_color"
const val CUSTOM_PRIMARY_COLOR = "custom_primary_color"
const val CUSTOM_ACCENT_COLOR = "custom_accent_color"

const val AUTHORITY = android.provider.ContactsContract.AUTHORITY
const val DEFAULT_ORGANIZATION_TYPE = android.provider.ContactsContract.CommonDataKinds.Organization.TYPE_WORK
const val DEFAULT_WEBSITE_TYPE = android.provider.ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE

const val PHOTO_ADDED = 1
const val PHOTO_REMOVED = 2
const val PHOTO_CHANGED = 3

const val TELEGRAM_PACKAGE = "org.telegram.messenger"
const val SIGNAL_PACKAGE = "org.thoughtcrime.securesms"
const val WHATSAPP_PACKAGE = "com.whatsapp"
const val VIBER_PACKAGE = "com.viber.voip"
const val THREEMA_PACKAGE = "ch.threema.app"

const val WAS_LOCAL_ACCOUNT_INITIALIZED = "was_local_account_initialized"
const val MERGE_DUPLICATE_CONTACTS = "merge_duplicate_contacts"

const val DARK_GREY = 0xFF333333.toInt()

val letterBackgroundColors = arrayListOf(
    0xCCD32F2F,
    0xCCC2185B,
    0xCC1976D2,
    0xCC0288D1,
    0xCC0097A7,
    0xCC00796B,
    0xCC388E3C,
    0xCC689F38,
    0xCCF57C00,
    0xCCE64A19,
)

const val SORT_BY_NAME = 1
const val SORT_BY_FIRST_NAME = 128
const val SORT_BY_MIDDLE_NAME = 256
const val SORT_BY_SURNAME = 512
const val SORT_DESCENDING = 1024
const val SORT_BY_FULL_NAME = 65536
const val FONT_SIZE_MEDIUM = 1
const val PROTECTION_PATTERN = 0
const val PROTECTION_PIN = 1
const val PROTECTION_PASSWORD = 2

const val WEEK_SECONDS = 604800

const val PHOTO_UNCHANGED = 4

const val SMT_PRIVATE = "smt_private"

const val START_NAME_WITH_SURNAME = "start_name_with_surname"

const val PERMISSION_READ_STORAGE = 1
const val PERMISSION_WRITE_STORAGE = 2
const val PERMISSION_CAMERA = 3
const val PERMISSION_RECORD_AUDIO = 4
const val PERMISSION_READ_CONTACTS = 5
const val PERMISSION_WRITE_CONTACTS = 6
const val PERMISSION_READ_CALENDAR = 7
const val PERMISSION_WRITE_CALENDAR = 8
const val PERMISSION_CALL_PHONE = 9
const val PERMISSION_READ_CALL_LOG = 10
const val PERMISSION_WRITE_CALL_LOG = 11
const val PERMISSION_GET_ACCOUNTS = 12
const val PERMISSION_READ_SMS = 13
const val PERMISSION_SEND_SMS = 14
const val PERMISSION_READ_PHONE_STATE = 15
const val PERMISSION_MEDIA_LOCATION = 16
const val PERMISSION_POST_NOTIFICATIONS = 17
const val PERMISSION_READ_MEDIA_IMAGES = 18
const val PERMISSION_READ_MEDIA_VIDEO = 19
const val PERMISSION_READ_MEDIA_AUDIO = 20
const val PERMISSION_ACCESS_COARSE_LOCATION = 21
const val PERMISSION_ACCESS_FINE_LOCATION = 22
const val PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED = 23
const val PERMISSION_READ_SYNC_SETTINGS = 24

fun isOnMainThread() = Looper.myLooper() == Looper.getMainLooper()

fun isNougatPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

fun isOreoPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

fun isQPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

fun isRPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

fun isSPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun ensureBackgroundThread(callback: () -> Unit) {
    if (isOnMainThread()) {
        Thread {
            callback()
        }.start()
    } else {
        callback()
    }
}

fun getQuestionMarks(size: Int) = List(size) { "?" }.joinToString(",")
