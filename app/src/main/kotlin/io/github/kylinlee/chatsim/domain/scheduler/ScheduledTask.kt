package io.github.kylinlee.chatsim.domain.scheduler

/** 定时任务类型；新增类型时同步实现对应的 ScheduledTaskHandler。 */
enum class ScheduledTaskType {
    SEND_MESSAGE,
    EMPTY_RECYCLE_BIN,
}

/** 任务状态：PENDING 可取消，RUNNING 已被原子认领，其余为终态。 */
enum class ScheduledTaskState {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED,
}

/** 一条持久化的定时任务；发送任务的 [id] 复用影子消息 id。 */
data class ScheduledTask(
    val id: Long,
    val type: ScheduledTaskType,
    val state: ScheduledTaskState,
    val conversationId: Long,
    val triggerAt: Long,
    val payload: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)
