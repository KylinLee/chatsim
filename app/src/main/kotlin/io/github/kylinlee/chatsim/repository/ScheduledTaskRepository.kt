package io.github.kylinlee.chatsim.repository

import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTask
import kotlinx.coroutines.flow.Flow

interface ScheduledTaskRepository {
    suspend fun scheduleMessage(
        text: String,
        conversationId: Long,
        subscriptionId: Int,
        triggerAtMillis: Long,
    )

    suspend fun cancel(taskId: Long): Boolean

    suspend fun cancelForConversation(conversationId: Long)

    suspend fun deleteScheduledMessage(taskId: Long)

    /** 按当前设置对账回收站自动清空任务（回收站设置变更后调用）。 */
    suspend fun ensureRecycleBinCleanSchedule()

    fun pendingTasks(conversationId: Long): Flow<List<ScheduledTask>>
}
