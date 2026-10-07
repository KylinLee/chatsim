package io.github.kylinlee.chatsim.background


import io.github.kylinlee.chatsim.common.*
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import io.github.kylinlee.chatsim.common.extensions.getProperPrimaryColor
import io.github.kylinlee.chatsim.common.extensions.notificationManager
import io.github.kylinlee.chatsim.data.contacts.SimpleContactsHelper
import io.github.kylinlee.chatsim.common.isNougatPlus
import io.github.kylinlee.chatsim.common.isOreoPlus
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.extensions.config
import io.github.kylinlee.chatsim.data.messaging.isShortCodeWithLetters
import io.github.kylinlee.chatsim.background.DeleteSmsReceiver
import io.github.kylinlee.chatsim.background.DirectReplyReceiver
import io.github.kylinlee.chatsim.background.MarkAsReadReceiver
import io.github.kylinlee.chatsim.ui.MainActivity

private const val MISSED_CALL_NOTIFICATION_CHANNEL = "simple_sms_messenger_missed_calls"
private const val MISSED_CALL_NOTIFICATION_ID = 43

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager
    private val soundUri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    private val user = Person.Builder()
        .setName(context.getString(R.string.me))
        .build()

    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        address: String,
        body: String,
        conversationId: Long,
        roleId: Long,
        bitmap: Bitmap?,
        sender: String?,
        roleLabel: String = "",
        alertOnlyOnce: Boolean = false
    ) {
        maybeCreateChannel(name = context.getString(R.string.channel_received_sms))

        val senderLabel = if (roleLabel.isBlank()) {
            sender ?: address
        } else {
            "$roleLabel · ${sender ?: address}"
        }

        val notificationId = conversationId.hashCode()
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("sms:${Uri.encode(address)}")
            putExtra(ROLE_ID, roleId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            action = MARK_AS_READ
            putExtra(CONVERSATION_ID, conversationId)
        }
        val markAsReadPendingIntent =
            PendingIntent.getBroadcast(context, notificationId, markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        val deleteSmsIntent = Intent(context, DeleteSmsReceiver::class.java).apply {
            putExtra(CONVERSATION_ID, conversationId)
            putExtra(MESSAGE_ID, messageId)
        }
        val deleteSmsPendingIntent =
            PendingIntent.getBroadcast(context, notificationId, deleteSmsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        var replyAction: NotificationCompat.Action? = null
        val isNoReplySms = isShortCodeWithLetters(address)
        if (isNougatPlus() && !isNoReplySms) {
            val replyLabel = context.getString(R.string.reply)
            val remoteInput = RemoteInput.Builder(REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(CONVERSATION_ID, conversationId)
                putExtra(ROLE_ID, roleId)
                putExtra(THREAD_NUMBER, address)
            }

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    context.applicationContext,
                    notificationId,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            replyAction = NotificationCompat.Action.Builder(R.drawable.ic_symbol_send, replyLabel, replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build()
        }

        val largeIcon = bitmap ?: if (sender != null) {
            SimpleContactsHelper(context).getContactLetterIcon(sender)
        } else {
            null
        }
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL).apply {
            when (context.config.lockScreenVisibilitySetting) {
                LOCK_SCREEN_SENDER_MESSAGE -> {
                    setLargeIcon(largeIcon)
                    setStyle(getMessagesStyle(address, body, notificationId, senderLabel))
                }

                LOCK_SCREEN_SENDER -> {
                    setContentTitle(senderLabel)
                    setLargeIcon(largeIcon)
                    val summaryText = context.getString(R.string.new_message)
                    setStyle(NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body))
                }
            }

            color = context.getProperPrimaryColor()
            setSmallIcon(R.drawable.ic_messenger)
            setContentIntent(contentPendingIntent)
            priority = NotificationCompat.PRIORITY_MAX
            setDefaults(Notification.DEFAULT_LIGHTS)
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setOnlyAlertOnce(alertOnlyOnce)
            setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
        }

        if (replyAction != null && context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
            builder.addAction(replyAction)
        }

            builder.addAction(R.drawable.ic_symbol_check, context.getString(R.string.mark_as_read), markAsReadPendingIntent)
            .setChannelId(NOTIFICATION_CHANNEL)
        if (isNoReplySms) {
            builder.addAction(
                R.drawable.ic_symbol_delete,
                context.getString(io.github.kylinlee.chatsim.R.string.delete),
                deleteSmsPendingIntent
            ).setChannelId(NOTIFICATION_CHANNEL)
        }

        // 横幅（heads-up）默认展示展开内容：发件人 + 完整正文
        if (context.config.lockScreenVisibilitySetting != LOCK_SCREEN_NOTHING) {
            val headsUpView = RemoteViews(context.packageName, R.layout.message_notification_heads_up).apply {
                setTextViewText(R.id.heads_up_sender, senderLabel)
                setTextViewText(
                    R.id.heads_up_body,
                    if (context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER) {
                        context.getString(R.string.new_message)
                    } else {
                        body
                    },
                )
                if (largeIcon != null) {
                    setImageViewBitmap(R.id.heads_up_avatar, largeIcon)
                } else {
                    setViewVisibility(R.id.heads_up_avatar, View.GONE)
                }
            }
            builder.setCustomHeadsUpContentView(headsUpView)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        maybeCreateChannel(name = context.getString(R.string.message_not_sent_short))

        val notificationId = generateRandomId().hashCode()
        val contentPendingIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
            PendingIntent.getActivity(context, notificationId, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        }

        val summaryText = String.format(context.getString(R.string.message_sending_error), recipientName)
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_messenger)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(NOTIFICATION_CHANNEL)

        contentPendingIntent?.let { builder.setContentIntent(it) }

        notificationManager.notify(notificationId, builder.build())
    }

    @SuppressLint("NewApi")
    fun showDeliveryReportNotification(
        messageId: Long,
        address: String,
        recipientName: String,
        roleId: Long,
        body: String,
        delivered: Boolean,
    ) {
        maybeCreateChannel(name = context.getString(R.string.channel_received_sms))

        val notificationId = messageId.hashCode()
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            if (address.isNotBlank()) {
                data = Uri.parse("sms:${Uri.encode(address)}")
            }
            if (roleId > 0) {
                putExtra(ROLE_ID, roleId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryText = context.getString(
            if (delivered) R.string.message_delivered else R.string.message_not_delivered,
            recipientName.ifBlank { address },
        )
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setContentTitle(context.getString(R.string.delivery_report))
            .setContentText(summaryText)
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(if (delivered) R.drawable.ic_symbol_done else R.drawable.ic_symbol_error)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(NOTIFICATION_CHANNEL)

        if (body.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        notificationManager.notify(notificationId, builder.build())
    }

    @SuppressLint("NewApi")
    fun showMissedCallNotification(number: String?) {
        maybeCreateMissedCallChannel()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            if (!number.isNullOrBlank()) {
                data = Uri.parse("sms:${Uri.encode(number)}")
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            MISSED_CALL_NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, MISSED_CALL_NOTIFICATION_CHANNEL)
            .setContentTitle(context.getString(R.string.missed_call))
            .setContentText(number.orEmpty())
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_symbol_call)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setChannelId(MISSED_CALL_NOTIFICATION_CHANNEL)
        notificationManager.notify(MISSED_CALL_NOTIFICATION_ID, builder.build())
    }

    fun cancelMissedCallNotification() {
        notificationManager.cancel(MISSED_CALL_NOTIFICATION_ID)
    }

    private fun maybeCreateMissedCallChannel() {
        if (isOreoPlus()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            val importance = IMPORTANCE_HIGH
            NotificationChannel(MISSED_CALL_NOTIFICATION_CHANNEL, context.getString(R.string.missed_call), importance).apply {
                setSound(soundUri, audioAttributes)
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    private fun maybeCreateChannel(name: String) {
        if (isOreoPlus()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            val id = NOTIFICATION_CHANNEL
            val importance = IMPORTANCE_HIGH
            NotificationChannel(id, name, importance).apply {
                setBypassDnd(false)
                enableLights(true)
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    private fun getMessagesStyle(address: String, body: String, notificationId: Int, name: String?): NotificationCompat.MessagingStyle {
        val sender = if (name != null) {
            Person.Builder()
                .setName(name)
                .setKey(address)
                .build()
        } else {
            null
        }

        return NotificationCompat.MessagingStyle(user).also { style ->
            getOldMessages(notificationId).forEach {
                style.addMessage(it)
            }
            val newMessage = NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
            style.addMessage(newMessage)
        }
    }

    private fun getOldMessages(notificationId: Int): List<NotificationCompat.MessagingStyle.Message> {
        if (!isNougatPlus()) {
            return emptyList()
        }
        val currentNotification = notificationManager.activeNotifications.find { it.id == notificationId }
        return if (currentNotification != null) {
            val activeStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(currentNotification.notification)
            activeStyle?.messages.orEmpty()
        } else {
            emptyList()
        }
    }
}
