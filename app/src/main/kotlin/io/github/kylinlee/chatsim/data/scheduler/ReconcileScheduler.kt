package io.github.kylinlee.chatsim.data.scheduler

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log

/** 周期对账的 JobScheduler 兜底：闹钟被系统清理或时间变更后自动重排。 */
object ReconcileScheduler {
    private const val JOB_ID = 7301
    private const val PERIOD_MILLIS = 6L * 60 * 60 * 1000
    private const val TAG = "ReconcileScheduler"

    fun ensure(context: Context) {
        val jobScheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (jobScheduler.getPendingJob(JOB_ID) != null) return

        val component = ComponentName(context, ReconcileJobService::class.java)
        val jobInfo = JobInfo.Builder(JOB_ID, component)
            .setPersisted(true)
            .setPeriodic(PERIOD_MILLIS)
            .build()

        runCatching { jobScheduler.schedule(jobInfo) }
            .onFailure { Log.w(TAG, "failed to schedule reconcile job", it) }
    }
}
