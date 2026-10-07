package io.github.kylinlee.chatsim.data.repository

import android.content.Context
import android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED
import android.provider.Telephony.Sms.STATUS_NONE
import io.github.kylinlee.chatsim.common.extensions.scheduledTasksDB
import io.github.kylinlee.chatsim.common.generateRandomId
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.data.scheduler.RecycleBinCleanScheduler
import io.github.kylinlee.chatsim.data.scheduler.TaskScheduler
import io.github.kylinlee.chatsim.data.scheduler.toDomain
import io.github.kylinlee.chatsim.di.IoDispatcher
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTask
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskState
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskType
import io.github.kylinlee.chatsim.domain.scheduler.SendMessagePayload
import io.github.kylinlee.chatsim.domain.scheduler.SendMessagePayloadCodec
import io.github.kylinlee.chatsim.repository.ScheduledTaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledTaskRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ScheduledTaskRepository {

    override suspend fun scheduleMessage(
        text: String,
        conversationId: Long,
        subscriptionId: Int,
        triggerAtMillis: Long,
    ) {
        withContext(ioDispatcher) {
            val messageId = generateRandomId()
            val now = System.currentTimeMillis()
            val message = Message(
                id = messageId,
                body = text,
                type = MESSAGE_TYPE_QUEUED,
                status = STATUS_NONE,
                participants = ArrayList(),
                date = (triggerAtMillis / 1000).toInt(),
                read = false,
                threadId = 0,
                isMMS = false,
                attachment = null,
                senderPhoneNumber = "",
                senderName = "",
                senderPhotoUri = "",
                subscriptionId = subscriptionId,
                isScheduled = true,
                conversationId = conversationId,
            )
            val task = ScheduledTask(
                id = messageId,
                type = ScheduledTaskType.SEND_MESSAGE,
                state = ScheduledTaskState.PENDING,
                conversationId = conversationId,
                triggerAt = triggerAtMillis,
                payload = SendMessagePayloadCodec.encode(SendMessagePayload(conversationId)),
                createdAt = now,
                updatedAt = now,
            )

            TaskScheduler.schedule(context, task, message)
        }
    }

    override suspend fun cancel(taskId: Long): Boolean = withContext(ioDispatcher) {
        TaskScheduler.cancel(context, taskId)
    }

    override suspend fun cancelForConversation(conversationId: Long) = withContext(ioDispatcher) {
        TaskScheduler.cancelForConversation(context, conversationId)
    }

    override suspend fun deleteScheduledMessage(taskId: Long) = withContext(ioDispatcher) {
        TaskScheduler.deleteScheduled(context, taskId)
    }

    override suspend fun ensureRecycleBinCleanSchedule() = withContext(ioDispatcher) {
        RecycleBinCleanScheduler.ensure(context)
    }

    override fun pendingTasks(conversationId: Long): Flow<List<ScheduledTask>> =
        context.scheduledTasksDB.observePendingByConversation(conversationId)
            .map { entities -> entities.mapNotNull { it.toDomain() } }
}
