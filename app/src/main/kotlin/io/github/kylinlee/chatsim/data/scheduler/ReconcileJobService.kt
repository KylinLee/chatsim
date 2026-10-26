package io.github.kylinlee.chatsim.data.scheduler

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

class ReconcileJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            try {
                TaskScheduler.reconcile(applicationContext)
                RecycleBinCleanScheduler.ensure(applicationContext)
                jobFinished(params, false)
            } catch (_: CancellationException) {
                // stopped by the system
            } catch (t: Throwable) {
                Log.w(TAG, "reconcile job failed", t)
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ReconcileJobService"
    }
}
