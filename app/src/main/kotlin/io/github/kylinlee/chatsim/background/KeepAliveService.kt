package io.github.kylinlee.chatsim.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.STOP_KEEP_ALIVE
import io.github.kylinlee.chatsim.common.isOreoPlus
import io.github.kylinlee.chatsim.ui.MainActivity

/** 最后手段：用户显式开启后以常驻通知换取更高的进程优先级。 */
class KeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startAsForeground() {
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_symbol_schedule)
        .setContentTitle(getString(R.string.keep_alive_notification_title))
        .setContentText(getString(R.string.keep_alive_notification_text))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setContentIntent(openAppPendingIntent())
        .addAction(
            R.drawable.ic_symbol_close,
            getString(R.string.keep_alive_stop),
            stopPendingIntent(),
        )
        .build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopPendingIntent(): PendingIntent {
        val intent = Intent(this, KeepAliveActionReceiver::class.java).setAction(STOP_KEEP_ALIVE)
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        if (!isOreoPlus()) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.keep_alive_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 45
        private const val CHANNEL_ID = "chatsim_keep_alive"

        fun sync(context: Context, enabled: Boolean) {
            if (enabled) {
                runCatching {
                    ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
                }
            } else {
                runCatching { context.stopService(Intent(context, KeepAliveService::class.java)) }
            }
        }
    }
}
