package io.github.kylinlee.chatsim.data.scheduler

import android.content.Context
import io.github.kylinlee.chatsim.common.extensions.config
import io.github.kylinlee.chatsim.common.extensions.getMessagesDB
import io.github.kylinlee.chatsim.data.model.ScheduledTaskEntity
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTask
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskState
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskType
import io.github.kylinlee.chatsim.domain.scheduler.nextRecycleBinCleanTrigger

/** 维护单条「回收站自动清空」任务：运行时刻 = 基准时间 + N×周期，错过则顺延下一周期。 */
object RecycleBinCleanScheduler {
    const val RECYCLE_BIN_CLEAN_TASK_ID = Long.MAX_VALUE

    suspend fun ensure(context: Context) {
        val db = context.getMessagesDB()
        val config = context.config
        val existing = db.ScheduledTasksDao().getById(RECYCLE_BIN_CLEAN_TASK_ID)

        val period = config.recycleBinCleanPeriod
        if (!config.useRecycleBin || period <= 0L) {
            clear(context, existing)
            return
        }

        val next = nextRecycleBinCleanTrigger(
            baseTime = config.recycleBinCleanBaseTime,
            periodMillis = period,
            now = System.currentTimeMillis(),
        )
        if (existing?.state == ScheduledTaskState.PENDING.name && existing.triggerAt == next) return

        clear(context, existing)
        val now = System.currentTimeMillis()
        TaskScheduler.schedule(
            context = context,
            task = ScheduledTask(
                id = RECYCLE_BIN_CLEAN_TASK_ID,
                type = ScheduledTaskType.EMPTY_RECYCLE_BIN,
                state = ScheduledTaskState.PENDING,
                conversationId = 0L,
                triggerAt = next,
                payload = "",
                createdAt = now,
                updatedAt = now,
            ),
            message = null,
        )
    }

    private suspend fun clear(context: Context, existing: ScheduledTaskEntity?) {
        if (existing == null) return
        if (existing.state == ScheduledTaskState.PENDING.name) {
            TaskScheduler.cancelTask(context, existing.id)
        }
        context.getMessagesDB().ScheduledTasksDao().delete(existing.id)
    }
}
