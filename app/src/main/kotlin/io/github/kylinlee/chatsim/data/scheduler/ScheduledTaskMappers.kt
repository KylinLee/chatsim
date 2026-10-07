package io.github.kylinlee.chatsim.data.scheduler

import io.github.kylinlee.chatsim.data.model.ScheduledTaskEntity
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTask
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskState
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskType

fun ScheduledTaskEntity.toDomain(): ScheduledTask? {
    val taskType = runCatching { ScheduledTaskType.valueOf(type) }.getOrNull() ?: return null
    val taskState = runCatching { ScheduledTaskState.valueOf(state) }.getOrNull() ?: return null
    return ScheduledTask(
        id = id,
        type = taskType,
        state = taskState,
        conversationId = conversationId,
        triggerAt = triggerAt,
        payload = payload,
        createdAt = createdAt,
        updatedAt = updatedAt,
        attempts = attempts,
        lastError = lastError,
    )
}

fun ScheduledTask.toEntity(): ScheduledTaskEntity = ScheduledTaskEntity(
    id = id,
    type = type.name,
    state = state.name,
    conversationId = conversationId,
    triggerAt = triggerAt,
    payload = payload,
    createdAt = createdAt,
    updatedAt = updatedAt,
    attempts = attempts,
    lastError = lastError,
)
