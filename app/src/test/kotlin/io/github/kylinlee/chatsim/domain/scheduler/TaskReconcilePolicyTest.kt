package io.github.kylinlee.chatsim.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReconcilePolicyTest {
    private val now = 1_000_000L

    @Test
    fun futurePendingTaskIsRegistered() {
        val task = task(id = 1, state = ScheduledTaskState.PENDING, triggerAt = now + 60_000)

        val actions = TaskReconcilePolicy.plan(listOf(task), now)

        assertEquals(1, actions.size)
        assertEquals(TaskReconcileAction.RegisterAlarm(task), actions.first())
    }

    @Test
    fun expiredPendingTaskFails() {
        val task = task(id = 2, state = ScheduledTaskState.PENDING, triggerAt = now - 1)

        val actions = TaskReconcilePolicy.plan(listOf(task), now)

        assertEquals(1, actions.size)
        val action = actions.first()
        assertTrue(action is TaskReconcileAction.Fail)
        assertEquals(TaskFailureReason.EXPIRED, (action as TaskReconcileAction.Fail).reason)
    }

    @Test
    fun staleRunningTaskFails() {
        val timeout = TaskReconcilePolicy.STALE_RUNNING_TIMEOUT_MILLIS
        val task = task(
            id = 3,
            state = ScheduledTaskState.RUNNING,
            triggerAt = now - 120_000,
            updatedAt = now - timeout - 1,
        )

        val actions = TaskReconcilePolicy.plan(listOf(task), now)

        assertEquals(1, actions.size)
        val action = actions.first()
        assertTrue(action is TaskReconcileAction.Fail)
        assertEquals(TaskFailureReason.STALE_RUNNING, (action as TaskReconcileAction.Fail).reason)
    }

    @Test
    fun freshRunningTaskIsKept() {
        val task = task(
            id = 4,
            state = ScheduledTaskState.RUNNING,
            triggerAt = now - 1_000,
            updatedAt = now - 1_000,
        )

        val actions = TaskReconcilePolicy.plan(listOf(task), now)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun terminalTasksAreIgnored() {
        val tasks = listOf(
            task(id = 5, state = ScheduledTaskState.DONE, triggerAt = now - 1),
            task(id = 6, state = ScheduledTaskState.FAILED, triggerAt = now - 1),
            task(id = 7, state = ScheduledTaskState.CANCELLED, triggerAt = now - 1),
        )

        val actions = TaskReconcilePolicy.plan(tasks, now)

        assertTrue(actions.isEmpty())
    }

    private fun task(
        id: Long,
        state: ScheduledTaskState,
        triggerAt: Long,
        updatedAt: Long = now,
    ) = ScheduledTask(
        id = id,
        type = ScheduledTaskType.SEND_MESSAGE,
        state = state,
        conversationId = 1,
        triggerAt = triggerAt,
        payload = "",
        createdAt = 0,
        updatedAt = updatedAt,
    )
}
