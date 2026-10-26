package io.github.kylinlee.chatsim.domain.scheduler

/** 需要触发定时任务对账的系统事件。 */
object ReconcileTrigger {
    private val ACTIONS = setOf(
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.TIME_SET",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.intent.action.DATE_CHANGED",
    )

    fun shouldReconcile(action: String?): Boolean = action in ACTIONS
}
