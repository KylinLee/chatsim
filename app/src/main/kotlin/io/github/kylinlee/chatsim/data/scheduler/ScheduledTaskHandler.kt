package io.github.kylinlee.chatsim.data.scheduler

import android.content.Context
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTask
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskType

/** 具体任务类型的执行体；抛出的异常会被调度器记为任务失败。 */
interface ScheduledTaskHandler {
    val type: ScheduledTaskType

    suspend fun execute(context: Context, task: ScheduledTask)
}

object ScheduledTaskHandlers {
    private val handlers: List<ScheduledTaskHandler> = listOf(SendMessageTaskHandler, EmptyRecycleBinTaskHandler)

    fun handlerFor(type: ScheduledTaskType): ScheduledTaskHandler? = handlers.firstOrNull { it.type == type }
}
