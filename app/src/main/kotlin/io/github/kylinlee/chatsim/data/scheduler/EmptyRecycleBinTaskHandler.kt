package io.github.kylinlee.chatsim.data.scheduler

import android.content.Context
import io.github.kylinlee.chatsim.common.extensions.getMessagesDB
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.common.extensions.purgeRecycleBinMessages
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTask
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskType

/** 到点清空回收站：永久删除全部记录并排下一次运行。 */
object EmptyRecycleBinTaskHandler : ScheduledTaskHandler {
    override val type: ScheduledTaskType = ScheduledTaskType.EMPTY_RECYCLE_BIN

    override suspend fun execute(context: Context, task: ScheduledTask) {
        val messages = context.messagesDB.getAllRecycleBinMessages()
        val conversationIds = context.purgeRecycleBinMessages(messages)

        val db = context.getMessagesDB()
        conversationIds.forEach { conversationId -> refreshConversation(db, conversationId) }
        if (messages.any { it.isCall }) {
            AppEventBus.tryEmit(AppEvent.RefreshCallLog)
        }

        RecycleBinCleanScheduler.ensure(context)
    }
}
