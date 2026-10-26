package io.github.kylinlee.chatsim.domain.background

/** 影响后台可靠性的系统能力快照。 */
data class BackgroundStatus(
    val ignoringBatteryOptimizations: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val notificationsGranted: Boolean = true,
    val isDefaultSms: Boolean = false,
    val isDefaultDialer: Boolean = false,
)

enum class BackgroundIssue {
    BATTERY_OPTIMIZATION,
    EXACT_ALARM,
    NOTIFICATIONS,
    DEFAULT_SMS,
    DEFAULT_DIALER,
}

/** 需要用户处理的项，按界面展示顺序返回。 */
fun BackgroundStatus.issues(): List<BackgroundIssue> = buildList {
    if (!ignoringBatteryOptimizations) add(BackgroundIssue.BATTERY_OPTIMIZATION)
    if (!canScheduleExactAlarms) add(BackgroundIssue.EXACT_ALARM)
    if (!notificationsGranted) add(BackgroundIssue.NOTIFICATIONS)
    if (!isDefaultSms) add(BackgroundIssue.DEFAULT_SMS)
    if (!isDefaultDialer) add(BackgroundIssue.DEFAULT_DIALER)
}

val BackgroundStatus.isHealthy: Boolean get() = issues().isEmpty()
