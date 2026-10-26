package io.github.kylinlee.chatsim.domain.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundStatusTest {
    @Test
    fun `healthy status has no issues`() {
        val status = BackgroundStatus(
            ignoringBatteryOptimizations = true,
            canScheduleExactAlarms = true,
            notificationsGranted = true,
            isDefaultSms = true,
            isDefaultDialer = true,
        )

        assertTrue(status.issues().isEmpty())
        assertTrue(status.isHealthy)
    }

    @Test
    fun `issues are reported in display order`() {
        val status = BackgroundStatus(
            ignoringBatteryOptimizations = false,
            canScheduleExactAlarms = false,
            notificationsGranted = false,
            isDefaultSms = false,
            isDefaultDialer = false,
        )

        assertEquals(
            listOf(
                BackgroundIssue.BATTERY_OPTIMIZATION,
                BackgroundIssue.EXACT_ALARM,
                BackgroundIssue.NOTIFICATIONS,
                BackgroundIssue.DEFAULT_SMS,
                BackgroundIssue.DEFAULT_DIALER,
            ),
            status.issues(),
        )
        assertFalse(status.isHealthy)
    }

    @Test
    fun `only missing capabilities are reported`() {
        val status = BackgroundStatus(
            ignoringBatteryOptimizations = true,
            canScheduleExactAlarms = false,
            notificationsGranted = true,
            isDefaultSms = false,
            isDefaultDialer = true,
        )

        assertEquals(
            listOf(BackgroundIssue.EXACT_ALARM, BackgroundIssue.DEFAULT_SMS),
            status.issues(),
        )
    }
}
