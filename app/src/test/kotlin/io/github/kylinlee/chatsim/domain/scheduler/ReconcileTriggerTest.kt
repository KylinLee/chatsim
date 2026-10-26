package io.github.kylinlee.chatsim.domain.scheduler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileTriggerTest {
    @Test
    fun `known system events trigger reconcile`() {
        listOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.DATE_CHANGED",
        ).forEach { action ->
            assertTrue(action, ReconcileTrigger.shouldReconcile(action))
        }
    }

    @Test
    fun `other events are ignored`() {
        assertFalse(ReconcileTrigger.shouldReconcile(null))
        assertFalse(ReconcileTrigger.shouldReconcile("android.intent.action.SCREEN_ON"))
        assertFalse(ReconcileTrigger.shouldReconcile("io.github.kylinlee.chatsim.action.reply"))
    }
}
