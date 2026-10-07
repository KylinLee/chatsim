package io.github.kylinlee.chatsim.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms.STATUS_FAILED
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.room.withTransaction
import io.github.kylinlee.chatsim.background.ScheduledTaskReceiver
import io.github.kylinlee.chatsim.common.TASK_ID
import io.github.kylinlee.chatsim.common.extensions.getMessagesDB
import io.github.kylinlee.chatsim.common.extensions.showErrorToast
import io.github.kylinlee.chatsim.common.isSPlus
import io.github.kylinlee.chatsim.common.refreshMessages
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.local.MessagesDatabase
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTask
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskType
import io.github.kylinlee.chatsim.domain.scheduler.TaskReconcileAction
import io.github.kylinlee.chatsim.domain.scheduler.TaskReconcilePolicy

/**
 * 持久化定时任务调度器：任务先落库再注册闹钟，触发时原子认领后执行，
 * 取消与执行互斥；启动/开机时对账（重排未到期、过期与超时未收尾标记失败）。
 */
object TaskScheduler {

    suspend fun schedule(context: Context, task: ScheduledTask, message: Message?): Boolean {
        val db = context.getMessagesDB()
        val inserted = db.withTransaction {
            if (message != null) {
                db.MessagesDao().insertOrIgnore(message)
                val conversation = db.ConversationsDao().getConversation(message.conversationId)
                if (conversation != null) {
                    db.ConversationsDao().insertOrUpdate(
                        conversation.copy(
                            snippet = message.body,
                            date = maxOf(conversation.date, message.date),
                        )
                    )
                }
            }
            db.ScheduledTasksDao().insert(task.toEntity())
        }
        if (inserted <= 0) {
            Log.w(TAG, "task ${task.id} already exists, schedule skipped")
            return false
        }
        registerAlarm(context, task.id, task.triggerAt)
        notifyConversationChanged(task.conversationId)
        return true
    }

    /** 取消待执行的定时任务并删除其影子消息；已认领/已结束的任务不可取消。 */
    suspend fun cancel(context: Context, taskId: Long): Boolean {
        val db = context.getMessagesDB()
        val conversationId = db.ScheduledTasksDao().getById(taskId)?.conversationId ?: 0
        val cancelled = db.withTransaction {
            val affected = db.ScheduledTasksDao().cancelPending(taskId, System.currentTimeMillis())
            if (affected > 0) {
                db.MessagesDao().delete(taskId)
                if (conversationId > 0) refreshConversation(db, conversationId)
            }
            affected > 0
        }
        if (cancelled) {
            cancelAlarm(context, taskId)
            notifyConversationChanged(conversationId)
        }
        return cancelled
    }

    /** 取消不关联消息的任务（如回收站自动清空）。 */
    suspend fun cancelTask(context: Context, taskId: Long) {
        val db = context.getMessagesDB()
        val cancelled = db.withTransaction {
            val affected = db.ScheduledTasksDao().cancelPending(taskId, System.currentTimeMillis())
            if (affected > 0) db.ScheduledTasksDao().delete(taskId)
            affected > 0
        }
        if (cancelled) cancelAlarm(context, taskId)
    }

    /** 删除一条定时消息（含失败气泡）：取消闹钟并清理任务记录与消息行。 */
    suspend fun deleteScheduled(context: Context, taskId: Long) {
        val db = context.getMessagesDB()
        val conversationId = db.ScheduledTasksDao().getById(taskId)?.conversationId ?: 0
        db.withTransaction {
            db.ScheduledTasksDao().cancelPending(taskId, System.currentTimeMillis())
            db.ScheduledTasksDao().delete(taskId)
            db.MessagesDao().delete(taskId)
            if (conversationId > 0) refreshConversation(db, conversationId)
        }
        cancelAlarm(context, taskId)
        notifyConversationChanged(conversationId)
    }

    suspend fun cancelForConversation(context: Context, conversationId: Long) {
        val db = context.getMessagesDB()
        val pending = db.ScheduledTasksDao().getPendingByConversation(conversationId)
        if (pending.isEmpty()) return

        db.withTransaction {
            val now = System.currentTimeMillis()
            pending.forEach { db.ScheduledTasksDao().cancelPending(it.id, now) }
        }
        pending.forEach { cancelAlarm(context, it.id) }
    }

    suspend fun handleAlarm(context: Context, taskId: Long) {
        val db = context.getMessagesDB()
        val task = db.ScheduledTasksDao().getById(taskId)?.toDomain() ?: return
        if (db.ScheduledTasksDao().claim(taskId, System.currentTimeMillis()) <= 0) return

        val handler = ScheduledTaskHandlers.handlerFor(task.type)
        if (handler == null) {
            fail(context, task, "no handler for ${task.type}")
            return
        }

        try {
            handler.execute(context, task)
        } catch (t: Throwable) {
            fail(context, task, t.message ?: t.javaClass.simpleName)
            return
        }

        db.ScheduledTasksDao().markDone(taskId, System.currentTimeMillis())
        refreshMessages()
    }

    /** 启动/开机对账：重排未到期任务，过期与超时未收尾任务标记失败。 */
    suspend fun reconcile(context: Context) {
        val db = context.getMessagesDB()
        val now = System.currentTimeMillis()
        val tasks = db.ScheduledTasksDao().getAll().mapNotNull { it.toDomain() }
        val actions = TaskReconcilePolicy.plan(tasks, now)
        if (actions.isEmpty()) return

        actions.forEach { action ->
            when (action) {
                is TaskReconcileAction.RegisterAlarm ->
                    registerAlarm(context, action.task.id, action.task.triggerAt)

                is TaskReconcileAction.Fail -> {
                    db.ScheduledTasksDao().markFailed(action.task.id, now, action.reason.name)
                    if (action.task.type == ScheduledTaskType.SEND_MESSAGE) {
                        db.MessagesDao().updateStatus(action.task.id, STATUS_FAILED)
                    }
                }
            }
        }
        refreshMessages()
    }

    /** 执行失败：保留影子消息作为失败气泡（显示错误图标），标记任务失败。 */
    private fun fail(context: Context, task: ScheduledTask, error: String) {
        val db = context.getMessagesDB()
        if (task.type == ScheduledTaskType.SEND_MESSAGE) {
            db.MessagesDao().updateStatus(task.id, STATUS_FAILED)
        }
        db.ScheduledTasksDao().markFailed(task.id, System.currentTimeMillis(), error)
        Log.w(TAG, "task ${task.id} failed: $error")
        refreshMessages()
    }

    private fun notifyConversationChanged(conversationId: Long) {
        if (conversationId > 0) {
            AppEventBus.tryEmit(AppEvent.ConversationsChanged(conversationId))
        }
    }

    private fun registerAlarm(context: Context, taskId: Long, triggerAt: Long) {
        val pendingIntent = getTaskPendingIntent(context, taskId)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (isSPlus() && !alarmManager.canScheduleExactAlarms()) {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context, taskId: Long) {
        val intent = Intent(context, ScheduledTaskReceiver::class.java)
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        PendingIntent.getBroadcast(context, taskId.toInt(), intent, flags)?.cancel()
    }

    private fun getTaskPendingIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, ScheduledTaskReceiver::class.java)
            .putExtra(TASK_ID, taskId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, taskId.toInt(), intent, flags)
    }

    private const val TAG = "TaskScheduler"
}

/** 按最新消息重算会话摘要与日期；没有消息则删除会话。 */
internal fun refreshConversation(db: MessagesDatabase, conversationId: Long) {
    val conversation = db.ConversationsDao().getConversation(conversationId) ?: return
    val date = db.MessagesDao().getLatestConversationDate(conversationId) ?: 0
    if (date == 0) {
        db.ConversationsDao().deleteConversation(conversationId)
    } else {
        val latest = db.MessagesDao().getLatestConversationMessage(conversationId)
        db.ConversationsDao().insertOrUpdate(conversation.copy(snippet = latest?.body.orEmpty(), date = date))
    }
}
