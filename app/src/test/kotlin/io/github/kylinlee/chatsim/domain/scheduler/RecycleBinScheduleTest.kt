package io.github.kylinlee.chatsim.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test

class RecycleBinScheduleTest {
    private val period = 24L * 60 * 60 * 1000
    private val base = 1_700_000_000_000L

    @Test
    fun futureBaseTimeIsKept() {
        assertEquals(base, nextRecycleBinCleanTrigger(base, period, base - 1))
    }

    @Test
    fun baseTimeItselfMovesToNextCycle() {
        assertEquals(base + period, nextRecycleBinCleanTrigger(base, period, base))
    }

    @Test
    fun nowInsideFirstCycleMovesToCycleEnd() {
        assertEquals(base + period, nextRecycleBinCleanTrigger(base, period, base + period / 2))
    }

    @Test
    fun exactCycleBoundaryMovesToNextCycle() {
        assertEquals(base + 4 * period, nextRecycleBinCleanTrigger(base, period, base + 3 * period))
    }

    @Test
    fun unalignedNowAlignsToBaseTime() {
        assertEquals(base + 5 * period, nextRecycleBinCleanTrigger(base, period, base + 4 * period + 1))
    }

    @Test
    fun periodOptionsMatchSliderStops() {
        assertEquals(6, RECYCLE_BIN_CLEAN_PERIODS.size)
        assertEquals(30L * period, RECYCLE_BIN_CLEAN_PERIODS[DEFAULT_RECYCLE_BIN_CLEAN_PERIOD_INDEX])
    }
}
