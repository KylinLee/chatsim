package io.github.kylinlee.chatsim.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import io.github.kylinlee.chatsim.common.TASK_ID
import io.github.kylinlee.chatsim.common.TASK_TYPE
import io.github.kylinlee.chatsim.data.scheduler.TaskScheduler
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(TASK_ID, 0L)
        if (taskId <= 0) return
        val useForegroundService = intent.getStringExtra(TASK_TYPE) == ScheduledTaskType.SEND_MESSAGE.name
        if (useForegroundService) ScheduledSendService.start(context)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chatsim:scheduled.task.receiver")
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskScheduler.handleAlarm(appContext, taskId)
            } finally {
                wakeLock.release()
                pendingResult.finish()
                if (useForegroundService) ScheduledSendService.stop(context)
            }
        }
    }

    private companion object {
        const val WAKE_LOCK_TIMEOUT_MILLIS = 30_000L
    }
}
