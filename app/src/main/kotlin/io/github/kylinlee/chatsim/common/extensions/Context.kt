package io.github.kylinlee.chatsim.common.extensions

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.CallLog.Calls
import android.provider.ContactsContract.CommonDataKinds.BaseTypes
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.provider.OpenableColumns
import android.provider.Telephony.*
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import io.github.kylinlee.chatsim.common.ensureBackgroundThread
import io.github.kylinlee.chatsim.data.contacts.SimpleContactsHelper
import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.SimpleContact
import kotlinx.coroutines.runBlocking
import java.io.File
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.data.local.MessagesDatabase
import io.github.kylinlee.chatsim.common.*
import io.github.kylinlee.chatsim.data.legacy.*
import io.github.kylinlee.chatsim.data.local.*
import io.github.kylinlee.chatsim.background.*
import io.github.kylinlee.chatsim.data.legacy.AttachmentUtils.parseAttachmentNames
import io.github.kylinlee.chatsim.data.local.dao.AttachmentsDao
import io.github.kylinlee.chatsim.data.local.dao.ConversationsDao
import io.github.kylinlee.chatsim.data.local.dao.MessageAttachmentsDao
import io.github.kylinlee.chatsim.data.local.dao.MessageTagsDao
import io.github.kylinlee.chatsim.data.local.dao.MessagesDao
import io.github.kylinlee.chatsim.data.local.dao.RuleHitsDao
import io.github.kylinlee.chatsim.data.local.dao.ScheduledTasksDao
import io.github.kylinlee.chatsim.data.local.dao.UserRulesDao
import io.github.kylinlee.chatsim.data.local.dao.UserTagsDao
import io.github.kylinlee.chatsim.data.messaging.MessagingUtils
import io.github.kylinlee.chatsim.data.messaging.MessagingUtils.Companion.ADDRESS_SEPARATOR
import io.github.kylinlee.chatsim.data.messaging.SmsSender
import io.github.kylinlee.chatsim.data.model.*
import me.leolin.shortcutbadger.ShortcutBadger
import java.io.FileNotFoundException

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getMessagesDB() = MessagesDatabase.getInstance(this)

val Context.conversationsDB: ConversationsDao get() = getMessagesDB().ConversationsDao()

val Context.attachmentsDB: AttachmentsDao get() = getMessagesDB().AttachmentsDao()

val Context.messageAttachmentsDB: MessageAttachmentsDao get() = getMessagesDB().MessageAttachmentsDao()

val Context.messagesDB: MessagesDao get() = getMessagesDB().MessagesDao()

val Context.userRulesDB: UserRulesDao get() = getMessagesDB().UserRulesDao()

val Context.userTagsDB: UserTagsDao get() = getMessagesDB().UserTagsDao()

val Context.messageTagsDB: MessageTagsDao get() = getMessagesDB().MessageTagsDao()

val Context.ruleHitsDB: RuleHitsDao get() = getMessagesDB().RuleHitsDao()

val Context.scheduledTasksDB: ScheduledTasksDao get() = getMessagesDB().ScheduledTasksDao()

val Context.notificationHelper get() = NotificationHelper(this)

val Context.messagingUtils get() = MessagingUtils(this)

val Context.smsSender get() = SmsSender.getInstance(applicationContext as Application)

fun Context.getMessages(
    threadId: Long,
    getImageResolutions: Boolean,
    dateFrom: Int = -1,
    includeScheduledMessages: Boolean = true,
    limit: Int = MESSAGES_LIMIT
): ArrayList<Message> {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms._ID,
        Sms.BODY,
        Sms.TYPE,
        Sms.ADDRESS,
        Sms.DATE,
        Sms.READ,
        Sms.THREAD_ID,
        Sms.SUBSCRIPTION_ID,
        Sms.STATUS
    )

    val rangeQuery = if (dateFrom == -1) "" else "AND ${Sms.DATE} < ${dateFrom.toLong() * 1000}"
    val selection = "${Sms.THREAD_ID} = ? $rangeQuery"
    val selectionArgs = arrayOf(threadId.toString())
    val sortOrder = "${Sms.DATE} DESC LIMIT $limit"

    val blockStatus = HashMap<String, Boolean>()
    val blockedNumbers = getBlockedNumbers()
    var messages = ArrayList<Message>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor

        val isNumberBlocked = if (blockStatus.containsKey(senderNumber)) {
            blockStatus[senderNumber]!!
        } else {
            val isBlocked = isNumberBlocked(senderNumber, blockedNumbers)
            blockStatus[senderNumber] = isBlocked
            isBlocked
        }

        if (isNumberBlocked) {
            return@queryCursor
        }

        val id = cursor.getLongValue(Sms._ID)
        val body = cursor.getStringValue(Sms.BODY)
        val type = cursor.getIntValue(Sms.TYPE)
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        val senderName = namePhoto.name
        val photoUri = namePhoto.photoUri ?: ""
        val date = (cursor.getLongValue(Sms.DATE) / 1000).toInt()
        val read = cursor.getIntValue(Sms.READ) == 1
        val thread = cursor.getLongValue(Sms.THREAD_ID)
        val subscriptionId = cursor.getIntValue(Sms.SUBSCRIPTION_ID)
        val status = cursor.getIntValue(Sms.STATUS)
        val participants = senderNumber.split(ADDRESS_SEPARATOR).map { number ->
            val phoneNumber = PhoneNumber(number, 0, "", number)
            val participantPhoto = getNameAndPhotoFromPhoneNumber(number)
            SimpleContact(0, 0, participantPhoto.name, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
        }
        val isMMS = false
        val message =
            Message(
                id,
                body,
                type,
                status,
                ArrayList(participants),
                date,
                read,
                thread,
                isMMS,
                null,
                senderNumber,
                senderName,
                photoUri,
                subscriptionId
            )
        messages.add(message)
    }

    messages.addAll(getMMS(threadId, getImageResolutions, sortOrder, dateFrom))

    messages = messages
        .filter { it.participants.isNotEmpty() }
        .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
        .sortedWith(compareBy<Message> { it.date }.thenBy { it.id })
        .takeLast(limit)
        .toMutableList() as ArrayList<Message>

    return messages
}

// as soon as a message contains multiple recipients it counts as an MMS instead of SMS
fun Context.getMMS(threadId: Long? = null, getImageResolutions: Boolean = false, sortOrder: String? = null, dateFrom: Int = -1): ArrayList<Message> {
    val uri = Mms.CONTENT_URI
    val projection = arrayOf(
        Mms._ID,
        Mms.DATE,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.THREAD_ID,
        Mms.SUBSCRIPTION_ID,
        Mms.STATUS
    )

    var selection: String? = null
    var selectionArgs: Array<String>? = null

    if (threadId == null && dateFrom != -1) {
        selection = "${Sms.DATE} < ${dateFrom.toLong()}" //Should not multiply 1000 here, because date in mms's database is different from sms's.
    } else if (threadId != null && dateFrom == -1) {
        selection = "${Sms.THREAD_ID} = ?"
        selectionArgs = arrayOf(threadId.toString())
    } else if (threadId != null) {
        selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} < ${dateFrom.toLong()}"
        selectionArgs = arrayOf(threadId.toString())
    }

    val messages = ArrayList<Message>()
    val contactsMap = HashMap<Int, SimpleContact>()
    val threadParticipants = HashMap<Long, ArrayList<SimpleContact>>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val mmsId = cursor.getLongValue(Mms._ID)
        val type = cursor.getIntValue(Mms.MESSAGE_BOX)
        val date = cursor.getLongValue(Mms.DATE).toInt()
        val read = cursor.getIntValue(Mms.READ) == 1
        val threadId = cursor.getLongValue(Mms.THREAD_ID)
        val subscriptionId = cursor.getIntValue(Mms.SUBSCRIPTION_ID)
        val status = cursor.getIntValue(Mms.STATUS)
        val participants = if (threadParticipants.containsKey(threadId)) {
            threadParticipants[threadId]!!
        } else {
            val parts = getThreadParticipants(threadId, contactsMap)
            threadParticipants[threadId] = parts
            parts
        }

        val isMMS = true
        val attachment = getMmsAttachment(mmsId, getImageResolutions)
        val body = attachment.text
        var senderNumber = ""
        var senderName = ""
        var senderPhotoUri = ""

        if (type != Mms.MESSAGE_BOX_SENT && type != Mms.MESSAGE_BOX_FAILED) {
            senderNumber = getMMSSender(mmsId)
            val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
            senderName = namePhoto.name
            senderPhotoUri = namePhoto.photoUri ?: ""
        }

        val message =
            Message(
                mmsId,
                body,
                type,
                status,
                participants,
                date,
                read,
                threadId,
                isMMS,
                attachment,
                senderNumber,
                senderName,
                senderPhotoUri,
                subscriptionId
            )
        messages.add(message)

        participants.forEach {
            contactsMap[it.rawId] = it
        }
    }

    return messages
}

fun Context.getMMSSender(msgId: Long): String {
    val uri = Uri.parse("${Mms.CONTENT_URI}/$msgId/addr")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (ignored: Exception) {
    }
    return ""
}

fun Context.getCanonicalAddressMap(): Map<Int, String> {
    val addresses = HashMap<Int, String>()
    val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
    val projection = arrayOf(Mms._ID, Mms.Addr.ADDRESS)

    try {
        queryCursor(uri, projection, null, null, null, showErrors = false) { cursor ->
            val address = cursor.getStringValue(Mms.Addr.ADDRESS)
            if (!address.isNullOrEmpty()) {
                addresses[cursor.getIntValue(Mms._ID)] = address
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return addresses
}

fun Context.getConversations(threadId: Long? = null): ArrayList<SystemConversation> {
    val uri = Uri.parse("${Threads.CONTENT_URI}?simple=true")
    val projection = mutableListOf(
        Threads._ID,
        Threads.SNIPPET,
        Threads.DATE,
        Threads.READ,
        Threads.RECIPIENT_IDS,
    )

    var selection = "${Threads.MESSAGE_COUNT} > ?"
    var selectionArgs = arrayOf("0")
    if (threadId != null) {
        selection += " AND ${Threads._ID} = ?"
        selectionArgs = arrayOf("0", threadId.toString())
    }

    val sortOrder = "${Threads.DATE} DESC"

    val conversations = ArrayList<SystemConversation>()
    val addressMap = getCanonicalAddressMap()
    val blockedNumbers = getBlockedNumbers()
    try {
        queryCursorUnsafe(uri, projection.toTypedArray(), selection, selectionArgs, sortOrder) { cursor ->
            val id = cursor.getLongValue(Threads._ID)
            var snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""
            if (snippet.isEmpty()) {
                snippet = getThreadSnippet(id)
            }

            var date = cursor.getLongValue(Threads.DATE)
            if (date.toString().length > 10) {
                date /= 1000
            }

            val rawIds = cursor.getStringValue(Threads.RECIPIENT_IDS)
            val recipientIds = rawIds.split(" ").filter { it.areDigitsOnly() }.map { it.toInt() }
            val phoneNumbers = recipientIds.mapNotNull { addressMap[it] }.filter { it.isNotEmpty() }
            if (phoneNumbers.isEmpty() || phoneNumbers.any { isNumberBlocked(it, blockedNumbers) }) {
                return@queryCursorUnsafe
            }

            val read = cursor.getIntValue(Threads.READ) == 1
            val conversation = SystemConversation(
                threadId = id,
                snippet = snippet,
                date = date.toInt(),
                read = read,
                title = "",
                photoUri = "",
                isGroupConversation = phoneNumbers.size > 1,
                phoneNumber = phoneNumbers.first(),
            )
            conversations.add(conversation)
        }
    } catch (sqliteException: SQLiteException) {
        showErrorToast(sqliteException)
    } catch (e: Exception) {
        showErrorToast(e)
    }

    conversations.sortByDescending { it.date }
    return conversations
}

private fun Context.queryCursorUnsafe(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    callback: (cursor: Cursor) -> Unit
) {
    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
    cursor?.use {
        if (cursor.moveToFirst()) {
            do {
                callback(cursor)
            } while (cursor.moveToNext())
        }
    }
}

fun Context.getConversationIds(): List<Long> {
    val uri = Uri.parse("${Threads.CONTENT_URI}?simple=true")
    val projection = arrayOf(Threads._ID)
    val selection = "${Threads.MESSAGE_COUNT} > ?"
    val selectionArgs = arrayOf("0")
    val sortOrder = "${Threads.DATE} ASC"
    val conversationIds = mutableListOf<Long>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, true) { cursor ->
        val id = cursor.getLongValue(Threads._ID)
        conversationIds.add(id)
    }
    return conversationIds
}

// based on https://stackoverflow.com/a/6446831/1967672
@SuppressLint("NewApi")
fun Context.getMmsAttachment(id: Long, getImageResolutions: Boolean): MessageAttachment {
    val uri = if (isQPlus()) {
        Mms.Part.CONTENT_URI
    } else {
        Uri.parse("content://mms/part")
    }

    val projection = arrayOf(
        Mms._ID,
        Mms.Part.CONTENT_TYPE,
        Mms.Part.TEXT
    )
    val selection = "${Mms.Part.MSG_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    val messageAttachment = MessageAttachment(id, "", arrayListOf())

    var attachmentNames: List<String>? = null
    var attachmentCount = 0
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val partId = cursor.getLongValue(Mms._ID)
        val mimetype = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
        if (mimetype == "text/plain") {
            messageAttachment.text = cursor.getStringValue(Mms.Part.TEXT) ?: ""
        } else if (mimetype.startsWith("image/") || mimetype.startsWith("video/")) {
            val fileUri = Uri.withAppendedPath(uri, partId.toString())
            var width = 0
            var height = 0

            if (getImageResolutions) {
                try {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeStream(contentResolver.openInputStream(fileUri), null, options)
                    width = options.outWidth
                    height = options.outHeight
                } catch (e: Exception) {
                }
            }

            val attachment = Attachment(partId, id, fileUri.toString(), mimetype, width, height, "")
            messageAttachment.attachments.add(attachment)
        } else if (mimetype != "application/smil") {
            val attachmentName = attachmentNames?.getOrNull(attachmentCount) ?: ""
            val attachment = Attachment(partId, id, Uri.withAppendedPath(uri, partId.toString()).toString(), mimetype, 0, 0, attachmentName)
            messageAttachment.attachments.add(attachment)
            attachmentCount++
        } else {
            val text = cursor.getStringValue(Mms.Part.TEXT)
            attachmentNames = parseAttachmentNames(text)
        }
    }

    return messageAttachment
}

fun Context.getLatestMMS(): Message? {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    return getMMS(sortOrder = sortOrder).firstOrNull()
}

fun Context.getThreadSubscriptionIds(): Map<Long, Int> {
    val subscriptions = HashMap<Long, Int>()
    val projection = arrayOf(Sms.THREAD_ID, Sms.SUBSCRIPTION_ID, Sms.DATE)
    val sortOrder = "${Sms.DATE} DESC"

    try {
        queryCursor(Sms.CONTENT_URI, projection, null, null, sortOrder, showErrors = false) { cursor ->
            val threadId = cursor.getLongValue(Sms.THREAD_ID)
            if (!subscriptions.containsKey(threadId)) {
                subscriptions[threadId] = cursor.getIntValue(Sms.SUBSCRIPTION_ID)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return subscriptions
}

fun Context.getThreadSnippet(threadId: Long): String {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    val latestMms = getMMS(threadId, false, sortOrder).firstOrNull()
    var snippet = latestMms?.body ?: ""

    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.BODY
    )

    val selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} > ?"
    val selectionArgs = arrayOf(
        threadId.toString(),
        latestMms?.date?.toString() ?: "0"
    )
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                snippet = cursor.getStringValue(Sms.BODY)
            }
        }
    } catch (ignored: Exception) {
    }
    return snippet
}

fun Context.getMessageRecipientAddress(messageId: Long): String {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(messageId.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Sms.ADDRESS)
            }
        }
    } catch (e: Exception) {
    }

    return ""
}

fun Context.getThreadParticipants(threadId: Long, contactsMap: HashMap<Int, SimpleContact>?): ArrayList<SimpleContact> {
    val uri = Uri.parse("${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true")
    val projection = arrayOf(
        ThreadsColumns.RECIPIENT_IDS
    )
    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    val participants = ArrayList<SimpleContact>()
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                val address = cursor.getStringValue(ThreadsColumns.RECIPIENT_IDS)
                address.split(" ").filter { it.areDigitsOnly() }.forEach {
                    val addressId = it.toInt()
                    if (contactsMap?.containsKey(addressId) == true) {
                        participants.add(contactsMap[addressId]!!)
                        return@forEach
                    }

                    val number = getPhoneNumberFromAddressId(addressId)
                    val namePhoto = getNameAndPhotoFromPhoneNumber(number)
                    val name = namePhoto.name
                    val photoUri = namePhoto.photoUri ?: ""
                    val phoneNumber = PhoneNumber(number, 0, "", number)
                    val contact = SimpleContact(addressId, addressId, name, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
                    participants.add(contact)
                }
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return participants
}

fun Context.getThreadPhoneNumbers(recipientIds: List<Int>): ArrayList<String> {
    val numbers = ArrayList<String>()
    recipientIds.forEach {
        numbers.add(getPhoneNumberFromAddressId(it))
    }
    return numbers
}

fun Context.getThreadContactNames(phoneNumbers: List<String>, privateContacts: ArrayList<SimpleContact>): ArrayList<String> {
    val names = ArrayList<String>()
    phoneNumbers.forEach { number ->
        val name = SimpleContactsHelper(this).getNameFromPhoneNumber(number)
        if (name != number) {
            names.add(name)
        } else {
            val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(number) }
            if (privateContact == null) {
                names.add(name)
            } else {
                names.add(privateContact.name)
            }
        }
    }
    return names
}

fun Context.getPhoneNumberFromAddressId(canonicalAddressId: Int): String {
    val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(canonicalAddressId.toString())
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return ""
}

fun Context.getSuggestedContacts(privateContacts: ArrayList<SimpleContact>): ArrayList<SimpleContact> {
    val contacts = ArrayList<SimpleContact>()
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val sortOrder = "${Sms.DATE} DESC LIMIT 50"
    val blockedNumbers = getBlockedNumbers()

    queryCursor(uri, projection, null, null, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        var senderName = namePhoto.name
        var photoUri = namePhoto.photoUri ?: ""
        if (isNumberBlocked(senderNumber, blockedNumbers)) {
            return@queryCursor
        } else if (namePhoto.name == senderNumber) {
            if (privateContacts.isNotEmpty()) {
                val privateContact = privateContacts.firstOrNull { it.phoneNumbers.first().normalizedNumber == senderNumber }
                if (privateContact != null) {
                    senderName = privateContact.name
                    photoUri = privateContact.photoUri
                } else {
                    return@queryCursor
                }
            } else {
                return@queryCursor
            }
        }

        val phoneNumber = PhoneNumber(senderNumber, 0, "", senderNumber)
        val contact = SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
        if (!contacts.map { it.phoneNumbers.first().normalizedNumber.trimToComparableNumber() }.contains(senderNumber.trimToComparableNumber())) {
            contacts.add(contact)
        }
    }

    return contacts
}

fun Context.getNameAndPhotoFromPhoneNumber(number: String): NamePhoto {
    if (!hasPermission(PERMISSION_READ_CONTACTS)) {
        return NamePhoto(number, null)
    }

    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.PHOTO_URI
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                val name = cursor.getStringValue(PhoneLookup.DISPLAY_NAME)
                val photoUri = cursor.getStringValue(PhoneLookup.PHOTO_URI)
                return NamePhoto(name, photoUri)
            }
        }
    } catch (e: Exception) {
    }

    return NamePhoto(number, null)
}

fun Context.insertNewSMS(
    address: String,
    subject: String,
    body: String,
    date: Long,
    read: Int,
    threadId: Long,
    type: Int,
    subscriptionId: Int
): Long {
    val uri = Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.ADDRESS, address)
        put(Sms.SUBJECT, subject)
        put(Sms.BODY, body)
        put(Sms.DATE, date)
        put(Sms.READ, read)
        put(Sms.THREAD_ID, threadId)
        put(Sms.TYPE, type)
        put(Sms.SUBSCRIPTION_ID, subscriptionId)
    }

    return try {
        val newUri = contentResolver.insert(uri, contentValues)
        newUri?.lastPathSegment?.toLong() ?: 0L
    } catch (e: Exception) {
        0L
    }
}

fun Context.deleteConversation(conversationId: Long) {
    val threadIds = messagesDB.getConversationThreadIds(conversationId)
    threadIds.forEach { threadId ->
        var uri = Sms.CONTENT_URI
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        try {
            contentResolver.delete(uri, selection, selectionArgs)
        } catch (e: Exception) {
            showErrorToast(e)
        }

        uri = Mms.CONTENT_URI
        try {
            contentResolver.delete(uri, selection, selectionArgs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    conversationsDB.deleteConversation(conversationId)
    messagesDB.deleteConversationMessages(conversationId)
}

/** 永久删除回收站中的记录：通话删除系统通话记录，短信/彩信删除系统库记录；返回受影响的会话 id。 */
fun Context.purgeRecycleBinMessages(messages: List<Message>): Set<Long> {
    if (messages.isEmpty()) return emptySet()

    val calls = messages.filter { it.isCall }
    if (calls.isNotEmpty()) {
        removeCallLogEntries(calls.map { (-it.id).toInt() })
        calls.forEach { messagesDB.delete(it.id) }
    }
    messages.filterNot { it.isCall }.forEach { deleteMessage(it.id, it.isMMS) }

    return messages.map { it.conversationId }.toSet()
}

/** 从系统通话记录中删除指定条目（无权限时跳过系统删除）。 */
fun Context.removeCallLogEntries(ids: List<Int>) {
    if (ids.isEmpty() || !hasPermission(PERMISSION_WRITE_CALL_LOG)) return

    ids.chunked(30).forEach { chunk ->
        val selection = "${Calls._ID} IN (${getQuestionMarks(chunk.size)})"
        contentResolver.delete(Calls.CONTENT_URI, selection, chunk.map { it.toString() }.toTypedArray())
    }
}

fun Context.restoreAllMessagesFromRecycleBinForConversation(conversationId: Long) {
    messagesDB.deleteConversationMessagesFromRecycleBin(conversationId)
}

fun Context.moveMessageToRecycleBin(id: Long) {
    try {
        messagesDB.insertRecycleBinEntry(RecycleBinMessage(id, System.currentTimeMillis()))
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.restoreMessageFromRecycleBin(id: Long) {
    try {
        messagesDB.deleteFromRecycleBin(id)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.deleteMessage(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    try {
        contentResolver.delete(uri, selection, selectionArgs)
        messagesDB.delete(id)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.markMessageRead(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.update(uri, contentValues, selection, selectionArgs)
    messagesDB.markRead(id)
}

fun Context.markMessagesRead(ids: List<Long>) {
    if (ids.isEmpty()) return

    val contentValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }

    ids.chunked(100).forEach { chunk ->
        val selection = "${Sms._ID} IN (${getQuestionMarks(chunk.size)})"
        val selectionArgs = chunk.map { it.toString() }.toTypedArray()
        try {
            contentResolver.update(Sms.CONTENT_URI, contentValues, selection, selectionArgs)
        } catch (ignored: Exception) {
        }
    }
    ids.forEach { messagesDB.markRead(it) }
}

fun Context.markThreadMessagesRead(threadId: Long) {
    arrayOf(Sms.CONTENT_URI, Mms.CONTENT_URI).forEach { uri ->
        val contentValues = ContentValues().apply {
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        contentResolver.update(uri, contentValues, selection, selectionArgs)
    }
}

fun Context.markThreadMessagesUnread(threadId: Long) {
    arrayOf(Sms.CONTENT_URI, Mms.CONTENT_URI).forEach { uri ->
        val contentValues = ContentValues().apply {
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
        }
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        contentResolver.update(uri, contentValues, selection, selectionArgs)
    }
}

fun Context.updateUnreadCountBadge(conversations: List<Conversation>) {
    try {
        val unreadCount = conversations.count { !it.read }
        if (unreadCount == 0) {
            ShortcutBadger.removeCount(this)
        } else {
            ShortcutBadger.applyCount(this, unreadCount)
        }
    } catch (ignored: Exception) {
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(address: String): Long {
    return try {
        Threads.getOrCreateThreadId(this, address)
    } catch (e: Exception) {
        0L
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(addresses: Set<String>): Long {
    return try {
        Threads.getOrCreateThreadId(this, addresses)
    } catch (e: Exception) {
        0L
    }
}

/** 同步展示新消息通知；调用方需在后台线程调用并保证在广播持有期内完成。 */
fun Context.showReceivedMessageNotification(
    messageId: Long,
    address: String,
    body: String,
    conversationId: Long,
    roleId: Long,
    bitmap: Bitmap?,
    roleLabel: String = "",
) {
    val senderName = getNameFromAddress(address)
    notificationHelper.showMessageNotification(messageId, address, body, conversationId, roleId, bitmap, senderName, roleLabel)
}

fun Context.getNameFromAddress(address: String): String {
    return getNameAndPhotoFromPhoneNumber(address).name
}

fun Context.getContactFromAddress(address: String, callback: ((contact: SimpleContact?) -> Unit)) {
    SimpleContactsHelper(this).getAvailableContacts(false) {
        callback(it.firstOrNull { it.doesHavePhoneNumber(address) })
    }
}

fun Context.getNotificationBitmap(photoUri: String): Bitmap? {
    val size = resources.getDimension(R.dimen.notification_large_icon_size).toInt()
    if (photoUri.isEmpty()) {
        return null
    }

    val request = ImageRequest.Builder(this)
        .data(photoUri)
        .size(size)
        .scale(Scale.FILL)
        .allowHardware(false)
        .build()

    return try {
        runBlocking { imageLoader.execute(request) }.image?.toBitmap()?.toCircleBitmap(size)
    } catch (e: Exception) {
        null
    }
}

fun Context.removeDiacriticsIfNeeded(text: String): String {
    return if (config.useSimpleCharacters) text.normalizeString() else text
}

fun Context.getSmsDraft(threadId: Long): String? {
    val uri = Sms.Draft.CONTENT_URI
    val projection = arrayOf(Sms.BODY)
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                return cursor.getString(0)
            }
        }
    } catch (e: Exception) {
    }

    return null
}

fun Context.getAllDrafts(): HashMap<Long, String?> {
    val drafts = HashMap<Long, String?>()
    val uri = Sms.Draft.CONTENT_URI
    val projection = arrayOf(Sms.BODY, Sms.THREAD_ID)

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val threadId = it.getLongValue(Sms.THREAD_ID)
                val draft = it.getStringValue(Sms.BODY)
                if (draft != null) {
                    drafts[threadId] = draft
                }
            }
        }
    } catch (e: Exception) {
    }

    return drafts
}

fun Context.saveSmsDraft(body: String, threadId: Long) {
    val uri = Sms.Draft.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.BODY, body)
        put(Sms.DATE, System.currentTimeMillis().toString())
        put(Sms.TYPE, Sms.MESSAGE_TYPE_DRAFT)
        put(Sms.THREAD_ID, threadId)
    }

    try {
        contentResolver.insert(uri, contentValues)
    } catch (e: Exception) {
    }
}

fun Context.deleteSmsDraft(threadId: Long) {
    val uri = Sms.Draft.CONTENT_URI
    val projection = arrayOf(Sms._ID)
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                val draftId = cursor.getLong(0)
                val draftUri = Uri.withAppendedPath(Sms.CONTENT_URI, "/${draftId}")
                contentResolver.delete(draftUri, null, null)
            }
        }
    } catch (e: Exception) {
    }
}

fun Context.updateLastConversationMessage(conversationId: Long) {
    val conversation = conversationsDB.getConversation(conversationId) ?: return
    val date = messagesDB.getLatestConversationDate(conversationId) ?: 0
    if (date == 0) {
        conversationsDB.deleteConversation(conversationId)
        return
    }

    val latest = messagesDB.getLatestConversationMessage(conversationId)
    conversationsDB.insertOrUpdate(conversation.copy(snippet = conversationSnippet(latest), date = date))
}

/** 会话摘要：优先消息正文；通话记录无正文，回退到方向/状态文案。 */
fun Context.conversationSnippet(message: Message?): String = when {
    message == null -> ""
    message.body.isNotBlank() -> message.body
    message.isCall -> getString(
        when (message.callType) {
            Calls.OUTGOING_TYPE -> R.string.outgoing_call
            Calls.MISSED_TYPE -> R.string.missed_call
            Calls.REJECTED_TYPE -> R.string.rejected_call
            Calls.BLOCKED_TYPE -> R.string.blocked_call
            Calls.VOICEMAIL_TYPE -> R.string.voicemail_call
            Calls.ANSWERED_EXTERNALLY_TYPE -> R.string.answered_externally_call
            Calls.INCOMING_TYPE -> R.string.incoming_call
            else -> R.string.call_not_connected
        }
    )

    message.isMMS -> getString(R.string.attachment_indicator)
    else -> ""
}

fun Context.getFileSizeFromUri(uri: Uri): Long {
    val assetFileDescriptor = try {
        contentResolver.openAssetFileDescriptor(uri, "r")
    } catch (e: FileNotFoundException) {
        null
    }

    // uses ParcelFileDescriptor#getStatSize underneath if failed
    val length = assetFileDescriptor?.use { it.length } ?: FILE_SIZE_NONE
    if (length != -1L) {
        return length
    }

    // if "content://" uri scheme, try contentResolver table
    if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT)) {
        return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            // maybe shouldn't trust ContentResolver for size:
            // https://stackoverflow.com/questions/48302972/content-resolver-returns-wrong-size
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex == -1) {
                return@use FILE_SIZE_NONE
            }
            cursor.moveToFirst()
            return try {
                cursor.getLong(sizeIndex)
            } catch (_: Throwable) {
                FILE_SIZE_NONE
            }
        } ?: FILE_SIZE_NONE
    } else {
        return FILE_SIZE_NONE
    }
}

// fix a glitch at enabling Release version minifying from 5.12.3
// reset messages in 5.14.3 again, as PhoneNumber is no longer minified
// reset messages in 5.19.1 again, as SimpleContact is no longer minified
fun Context.clearAllMessagesIfNeeded(callback: () -> Unit) {
    if (!config.wasDbCleared) {
        ensureBackgroundThread {
            messagesDB.deleteAll()
            config.wasDbCleared = true
            Handler(Looper.getMainLooper()).post(callback)
        }
    } else {
        callback()
    }
}

fun Context.subscriptionManagerCompat(): SubscriptionManager {
    return getSystemService(SubscriptionManager::class.java)
}

fun Context.insertOrUpdateConversation(
    conversation: Conversation,
    cachedConv: Conversation? = conversationsDB.getConversationByKey(conversation.roleId, conversation.peerNumber)
) {
    val updatedConv = if (cachedConv != null) {
        val usesCustomTitle = cachedConv.usesCustomTitle
        val title = if (usesCustomTitle) {
            cachedConv.title
        } else {
            conversation.title
        }
        conversation.copy(conversationId = cachedConv.conversationId, title = title, usesCustomTitle = usesCustomTitle)
    } else {
        conversation
    }
    conversationsDB.insertOrUpdate(updatedConv)
}

fun Context.upsertIncomingConversation(
    roleId: Long,
    peerNumber: String,
    body: String,
    date: Int,
    title: String,
    photoUri: String,
): Long {
    val existing = conversationsDB.getConversationByKey(roleId, peerNumber)
    return if (existing != null) {
        conversationsDB.insertOrUpdate(
            existing.copy(
                snippet = body,
                date = maxOf(existing.date, date),
                read = false,
                title = title.ifBlank { existing.title },
                photoUri = photoUri.ifBlank { existing.photoUri },
            )
        )
        existing.conversationId
    } else {
        conversationsDB.insertOrUpdate(
            Conversation(
                roleId = roleId,
                peerNumber = peerNumber,
                snippet = body,
                date = date,
                read = false,
                title = title.ifBlank { peerNumber },
                photoUri = photoUri,
                isGroupConversation = false,
            )
        )
    }
}

fun Context.renameConversation(conversation: Conversation, newTitle: String): Conversation {
    val updatedConv = conversation.copy(title = newTitle, usesCustomTitle = true)
    try {
        conversationsDB.insertOrUpdate(updatedConv)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return updatedConv
}

fun Context.getDefaultKeyboardHeight() = resources.getDimensionPixelSize(R.dimen.default_keyboard_height)

val Context.audioManager: AudioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

val Context.powerManager: PowerManager get() = getSystemService(Context.POWER_SERVICE) as PowerManager

@SuppressLint("MissingPermission")
fun Context.getAvailableSIMCardLabels(): List<SIMAccount> {
    val SIMAccounts = mutableListOf<SIMAccount>()
    try {
        telecomManager.callCapablePhoneAccounts.forEachIndexed { index, account ->
            val phoneAccount = telecomManager.getPhoneAccount(account)
            var label = phoneAccount.label.toString()
            var address = phoneAccount.address.toString()
            if (address.startsWith("tel:") && address.substringAfter("tel:").isNotEmpty()) {
                address = Uri.decode(address.substringAfter("tel:"))
                label += " ($address)"
            }

            val SIM = SIMAccount(index + 1, phoneAccount.accountHandle, label, address.substringAfter("tel:"))
            SIMAccounts.add(SIM)
        }
    } catch (ignored: Exception) {
    }
    return SIMAccounts
}

@SuppressLint("MissingPermission")
fun Context.areMultipleSIMsAvailable(): Boolean {
    return try {
        telecomManager.callCapablePhoneAccounts.size > 1
    } catch (ignored: Exception) {
        false
    }
}

// ===== Migrated from Simple-Commons =====

fun Context.hasPermission(permId: Int) = ContextCompat.checkSelfPermission(this, getPermissionString(permId)) == PackageManager.PERMISSION_GRANTED

fun Context.getPermissionString(id: Int) = when (id) {
    PERMISSION_READ_STORAGE -> android.Manifest.permission.READ_EXTERNAL_STORAGE
    PERMISSION_WRITE_STORAGE -> android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    PERMISSION_CAMERA -> android.Manifest.permission.CAMERA
    PERMISSION_RECORD_AUDIO -> android.Manifest.permission.RECORD_AUDIO
    PERMISSION_READ_CONTACTS -> android.Manifest.permission.READ_CONTACTS
    PERMISSION_WRITE_CONTACTS -> android.Manifest.permission.WRITE_CONTACTS
    PERMISSION_READ_CALENDAR -> android.Manifest.permission.READ_CALENDAR
    PERMISSION_WRITE_CALENDAR -> android.Manifest.permission.WRITE_CALENDAR
    PERMISSION_CALL_PHONE -> android.Manifest.permission.CALL_PHONE
    PERMISSION_READ_CALL_LOG -> android.Manifest.permission.READ_CALL_LOG
    PERMISSION_WRITE_CALL_LOG -> android.Manifest.permission.WRITE_CALL_LOG
    PERMISSION_GET_ACCOUNTS -> android.Manifest.permission.GET_ACCOUNTS
    PERMISSION_READ_SMS -> android.Manifest.permission.READ_SMS
    PERMISSION_SEND_SMS -> android.Manifest.permission.SEND_SMS
    PERMISSION_READ_PHONE_STATE -> android.Manifest.permission.READ_PHONE_STATE
    PERMISSION_MEDIA_LOCATION -> if (isQPlus()) android.Manifest.permission.ACCESS_MEDIA_LOCATION else ""
    PERMISSION_POST_NOTIFICATIONS -> android.Manifest.permission.POST_NOTIFICATIONS
    PERMISSION_READ_MEDIA_IMAGES -> android.Manifest.permission.READ_MEDIA_IMAGES
    PERMISSION_READ_MEDIA_VIDEO -> android.Manifest.permission.READ_MEDIA_VIDEO
    PERMISSION_READ_MEDIA_AUDIO -> android.Manifest.permission.READ_MEDIA_AUDIO
    PERMISSION_ACCESS_COARSE_LOCATION -> android.Manifest.permission.ACCESS_COARSE_LOCATION
    PERMISSION_ACCESS_FINE_LOCATION -> android.Manifest.permission.ACCESS_FINE_LOCATION
    PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED -> android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    PERMISSION_READ_SYNC_SETTINGS -> android.Manifest.permission.READ_SYNC_SETTINGS
    else -> ""
}

fun Context.queryCursor(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    showErrors: Boolean = false,
    callback: (cursor: Cursor) -> Unit,
) {
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    callback(cursor)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        if (showErrors) {
            showErrorToast(e)
        }
    }
}

val Context.notificationManager: NotificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

val Context.telecomManager: TelecomManager get() = getSystemService(Context.TELECOM_SERVICE) as TelecomManager

fun Context.toast(id: Int, length: Int = Toast.LENGTH_SHORT) {
    toast(getString(id), length)
}

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    try {
        if (isOnMainThread()) {
            doToast(this, msg, length)
        } else {
            Handler(Looper.getMainLooper()).post {
                doToast(this, msg, length)
            }
        }
    } catch (e: Exception) {
    }
}

private fun doToast(context: Context, msg: String, length: Int) {
    if (msg.isNotEmpty()) {
        Toast.makeText(context, msg, length).show()
    }
}

fun Context.showErrorToast(msg: String, length: Int = Toast.LENGTH_LONG) {
    toast(getString(R.string.error, msg), length)
}

fun Context.showErrorToast(exception: Exception, length: Int = Toast.LENGTH_LONG) {
    showErrorToast(exception.toString(), length)
}

fun Context.isDefaultDialer(): Boolean {
    return if (isNougatPlus()) {
        packageName == (getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
    } else {
        true
    }
}

fun Context.getFilenameFromUri(uri: Uri): String {
    return if (uri.scheme == "file") {
        File(uri.toString()).name
    } else {
        getFilenameFromContentUri(uri) ?: uri.lastPathSegment ?: ""
    }
}

private fun Context.getFilenameFromContentUri(uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(OpenableColumns.DISPLAY_NAME)
            }
        }
    } catch (e: Exception) {
    }
    return null
}

fun Context.getProperPrimaryColor(): Int {
    val primaryColor = config.primaryColor
    return if (primaryColor != 0) primaryColor else ContextCompat.getColor(this, R.color.color_primary)
}

fun Context.getBlockedNumbers(): ArrayList<BlockedNumber> {
    val blockedNumbers = ArrayList<BlockedNumber>()
    if (!isNougatPlus() || !isDefaultDialer()) {
        return blockedNumbers
    }

    val uri = BlockedNumbers.CONTENT_URI
    val projection = arrayOf(
        BlockedNumbers.COLUMN_ID,
        BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
        BlockedNumbers.COLUMN_E164_NUMBER,
    )

    queryCursor(uri, projection) { cursor ->
        val id = cursor.getLongValue(BlockedNumbers.COLUMN_ID)
        val number = cursor.getStringValue(BlockedNumbers.COLUMN_ORIGINAL_NUMBER) ?: ""
        val normalizedNumber = cursor.getStringValue(BlockedNumbers.COLUMN_E164_NUMBER) ?: number
        val comparableNumber = normalizedNumber.trimToComparableNumber()
        blockedNumbers.add(BlockedNumber(id, number, normalizedNumber, comparableNumber))
    }

    return blockedNumbers
}

fun Context.addBlockedNumber(number: String): Boolean {
    ContentValues().apply {
        put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        if (number.isPhoneNumber()) {
            put(BlockedNumbers.COLUMN_E164_NUMBER, PhoneNumberUtils.normalizeNumber(number))
        }
        try {
            contentResolver.insert(BlockedNumbers.CONTENT_URI, this)
        } catch (e: Exception) {
            showErrorToast(e)
            return false
        }
    }
    return true
}

fun Context.deleteBlockedNumber(number: String): Boolean {
    val selection = "${BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?"
    val selectionArgs = arrayOf(number)

    return if (isNumberBlocked(number)) {
        val deletedRowCount = contentResolver.delete(BlockedNumbers.CONTENT_URI, selection, selectionArgs)
        deletedRowCount > 0
    } else {
        true
    }
}

fun Context.isNumberBlocked(number: String, blockedNumbers: ArrayList<BlockedNumber> = getBlockedNumbers()): Boolean {
    if (!isNougatPlus()) {
        return false
    }

    val numberToCompare = number.trimToComparableNumber()
    return blockedNumbers.any {
        numberToCompare == it.numberToCompare ||
            numberToCompare == it.number ||
            PhoneNumberUtils.stripSeparators(number) == it.number
    } || isNumberBlockedByPattern(number, blockedNumbers)
}

fun Context.isNumberBlockedByPattern(number: String, blockedNumbers: ArrayList<BlockedNumber> = getBlockedNumbers()): Boolean {
    for (blockedNumber in blockedNumbers) {
        val num = blockedNumber.number
        if (num.isBlockedNumberPattern()) {
            val pattern = num.replace("+", "\\+").replace("*", ".*")
            if (number.matches(pattern.toRegex())) {
                return true
            }
        }
    }
    return false
}

fun Context.getPhotoThumbnailSize(): Int {
    val uri = android.provider.ContactsContract.DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI
    val projection = arrayOf(android.provider.ContactsContract.DisplayPhoto.THUMBNAIL_MAX_DIM)
    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getIntValue(android.provider.ContactsContract.DisplayPhoto.THUMBNAIL_MAX_DIM)
            }
        }
    } catch (ignored: Exception) {
    }
    return 512
}

fun Context.getPhoneNumberTypeText(type: Int, label: String): String {
    return if (type == BaseTypes.TYPE_CUSTOM) {
        label
    } else {
        getString(
            when (type) {
                Phone.TYPE_MOBILE -> R.string.mobile
                Phone.TYPE_HOME -> R.string.home
                Phone.TYPE_WORK -> R.string.work
                Phone.TYPE_MAIN -> R.string.main_number
                Phone.TYPE_FAX_WORK -> R.string.work_fax
                Phone.TYPE_FAX_HOME -> R.string.home_fax
                Phone.TYPE_PAGER -> R.string.pager
                else -> R.string.other
            }
        )
    }
}
