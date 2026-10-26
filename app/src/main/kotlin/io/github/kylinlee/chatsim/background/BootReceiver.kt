package io.github.kylinlee.chatsim.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kylinlee.chatsim.common.extensions.config
import io.github.kylinlee.chatsim.data.scheduler.ReconcileScheduler
import io.github.kylinlee.chatsim.data.scheduler.RecycleBinCleanScheduler
import io.github.kylinlee.chatsim.data.scheduler.TaskScheduler
import io.github.kylinlee.chatsim.domain.scheduler.ReconcileTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机/应用更新后恢复定时任务闹钟。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ReconcileTrigger.shouldReconcile(intent.action)) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        KeepAliveService.sync(appContext, appContext.config.keepAliveEnabled)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskScheduler.reconcile(appContext)
                RecycleBinCleanScheduler.ensure(appContext)
                ReconcileScheduler.ensure(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
