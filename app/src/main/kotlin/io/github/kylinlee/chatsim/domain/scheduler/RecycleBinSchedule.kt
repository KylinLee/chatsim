package io.github.kylinlee.chatsim.domain.scheduler

/** 回收站自动清空周期的可选档位：1 天 / 3 天 / 1 周 / 2 周 / 1 个月 / 3 个月。 */
val RECYCLE_BIN_CLEAN_PERIODS: List<Long> = listOf(
    1L * DAY_MILLIS,
    3L * DAY_MILLIS,
    7L * DAY_MILLIS,
    14L * DAY_MILLIS,
    30L * DAY_MILLIS,
    90L * DAY_MILLIS,
)

const val DEFAULT_RECYCLE_BIN_CLEAN_PERIOD_INDEX = 4

/** 基准时间之后、严格晚于 [now] 的第一个「基准时间 + N×周期」时刻。 */
fun nextRecycleBinCleanTrigger(baseTime: Long, periodMillis: Long, now: Long): Long {
    if (now < baseTime) return baseTime
    val cycles = (now - baseTime) / periodMillis + 1
    return baseTime + cycles * periodMillis
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
