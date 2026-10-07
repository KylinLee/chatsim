package io.github.kylinlee.chatsim.domain.scheduler

enum class TaskFailureReason {
    /** 设备关机/应用更新期间错过触发时间。 */
    EXPIRED,

    /** 执行中进程被杀，认领后未收尾。 */
    STALE_RUNNING,
}

sealed interface TaskReconcileAction {
    data class RegisterAlarm(val task: ScheduledTask) : TaskReconcileAction

    data class Fail(val task: ScheduledTask, val reason: TaskFailureReason) : TaskReconcileAction
}

/**
 * 启动/开机对账：未到期任务重排闹钟，过期任务标记失败，超时未收尾的任务标记失败。
 * 只处理 PENDING/RUNNING，终态任务不动。
 */
object TaskReconcilePolicy {
    const val STALE_RUNNING_TIMEOUT_MILLIS = 10 * 60 * 1000L

    fun plan(
        tasks: List<ScheduledTask>,
        now: Long,
        staleRunningTimeoutMillis: Long = STALE_RUNNING_TIMEOUT_MILLIS,
    ): List<TaskReconcileAction> = tasks.mapNotNull { task ->
        when (task.state) {
            ScheduledTaskState.PENDING ->
                if (task.triggerAt > now) {
                    TaskReconcileAction.RegisterAlarm(task)
                } else {
                    TaskReconcileAction.Fail(task, TaskFailureReason.EXPIRED)
                }

            ScheduledTaskState.RUNNING ->
                if (now - task.updatedAt >= staleRunningTimeoutMillis) {
                    TaskReconcileAction.Fail(task, TaskFailureReason.STALE_RUNNING)
                } else {
                    null
                }

            else -> null
        }
    }
}
