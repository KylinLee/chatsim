package io.github.kylinlee.chatsim.data.legacy


import io.github.kylinlee.chatsim.common.*
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.telecom.Call
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import io.github.kylinlee.chatsim.common.extensions.notificationManager
import io.github.kylinlee.chatsim.common.extensions.setText
import io.github.kylinlee.chatsim.common.isOreoPlus
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.background.CallActionReceiver
import io.github.kylinlee.chatsim.ui.call.CallActivity

class CallNotificationManager(private val context: Context) {
    private val CALL_NOTIFICATION_ID = 42
    private val ACCEPT_CALL_CODE = 0
    private val DECLINE_CALL_CODE = 1
    private val CALL_ACTIVITY_CODE = 0
    private val notificationManager = context.notificationManager
    private val callContactAvatarHelper = CallContactAvatarHelper(context)

    @SuppressLint("NewApi")
    fun setupNotification() {
        getCallContact(context.applicationContext, CallManager.getPrimaryCall()) { callContact ->
            val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact)
            val callState = CallManager.getState()
            val isRinging = callState == Call.STATE_RINGING
            val channelId = if (isRinging) RINGING_CHANNEL_ID else CALL_CHANNEL_ID
            if (isOreoPlus()) {
                val importance = if (isRinging) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                val name = if (isRinging) {
                    context.getString(R.string.call_notification_channel_ringing)
                } else {
                    context.getString(R.string.call_notification_channel)
                }

                NotificationChannel(channelId, name, importance).apply {
                    if (isRinging) {
                        setSound(ringtoneUri, RINGTONE_AUDIO_ATTRIBUTES)
                        enableVibration(true)
                        vibrationPattern = RINGING_VIBRATION_PATTERN
                    } else {
                        setSound(null, null)
                    }
                    notificationManager.createNotificationChannel(this)
                }
            }

            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                CALL_ACTIVITY_CODE,
                CallActivity.getStartIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val acceptCallIntent = Intent(context, CallActionReceiver::class.java)
            acceptCallIntent.action = ACCEPT_CALL
            val acceptPendingIntent =
                PendingIntent.getBroadcast(context, ACCEPT_CALL_CODE, acceptCallIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)

            val declineCallIntent = Intent(context, CallActionReceiver::class.java)
            declineCallIntent.action = DECLINE_CALL
            val declinePendingIntent =
                PendingIntent.getBroadcast(context, DECLINE_CALL_CODE, declineCallIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)

            var callerName = if (callContact.name.isNotEmpty()) callContact.name else context.getString(R.string.unknown_caller)
            if (callContact.numberLabel.isNotEmpty()) {
                callerName += " - ${callContact.numberLabel}"
            }

            val contentTextId = when (callState) {
                Call.STATE_RINGING -> R.string.is_calling
                Call.STATE_DIALING -> R.string.dialing
                Call.STATE_DISCONNECTED -> R.string.call_ended
                Call.STATE_DISCONNECTING -> R.string.call_ending
                else -> R.string.ongoing_call
            }

            val collapsedView = RemoteViews(context.packageName, R.layout.call_notification).apply {
                setText(R.id.notification_caller_name, callerName)
                setText(R.id.notification_call_status, context.getString(contentTextId))

                if (callContactAvatar != null) {
                    setImageViewBitmap(R.id.notification_thumbnail, callContactAvatarHelper.getCircularBitmap(callContactAvatar))
                }
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_symbol_call)
                .setContentIntent(openAppPendingIntent)
                .setPriority(if (isRinging) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_CALL)
                .setCustomContentView(collapsedView)
                .setOngoing(true)
                .setSound(null)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(callState == Call.STATE_ACTIVE)
                .setChannelId(channelId)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())

            builder.addAction(
                R.drawable.ic_symbol_call_end,
                context.getString(if (isRinging) R.string.decline else R.string.hang_up),
                declinePendingIntent,
            )
            if (isRinging) {
                builder.addAction(
                    R.drawable.ic_symbol_call,
                    context.getString(R.string.answer),
                    acceptPendingIntent,
                )
            }

            if (isRinging) {
                builder.setFullScreenIntent(openAppPendingIntent, true)
            }

            val notification = builder.build()
            // it's rare but possible for the call state to change by now
            if (CallManager.getState() == callState) {
                notificationManager.notify(CALL_NOTIFICATION_ID, notification)
            }
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(CALL_NOTIFICATION_ID)
    }

    private val ringtoneUri: Uri?
        get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    companion object {
        private const val CALL_CHANNEL_ID = "simple_dialer_call"
        private const val RINGING_CHANNEL_ID = "simple_dialer_call_ringing"
        private val RINGING_VIBRATION_PATTERN = longArrayOf(0, 1000, 1000)
        private val RINGTONE_AUDIO_ATTRIBUTES = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
