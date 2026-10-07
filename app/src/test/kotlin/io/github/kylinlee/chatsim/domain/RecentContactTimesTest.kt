package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentContactTimesTest {
    @Test
    fun buildTakesLatestTimePerNumber() {
        val times = RecentContactTimes.build(
            listOf(
                thread("13800000000", 1_000L),
                thread("13800000000", 3_000L),
                thread("+8613800000001", 2_000L),
            )
        )

        assertEquals(3_000L, RecentContactTimes.latestForNumber(times, "13800000000"))
        assertEquals(2_000L, RecentContactTimes.latestForNumber(times, "13800000001"))
        assertEquals(2_000L, RecentContactTimes.latestForNumber(times, "+86 138 0000 0001"))
    }

    @Test
    fun latestForNumberMatchesCountryCodeAndTrunkZeroVariants() {
        val times = RecentContactTimes.build(listOf(thread("+8657126883077", 5_000L)))

        assertEquals(5_000L, RecentContactTimes.latestForNumber(times, "057126883077"))
        assertEquals(0L, RecentContactTimes.latestForNumber(times, "057126883078"))
    }

    @Test
    fun groupConversationsAreIgnored() {
        val times = RecentContactTimes.build(
            listOf(thread("13800000000|13900000000", 9_000L, isGroup = true))
        )

        assertEquals(0L, RecentContactTimes.latestForNumber(times, "13800000000"))
    }

    @Test
    fun callsAreMergedWhenProvided() {
        val times = RecentContactTimes.build(
            conversations = listOf(thread("13800000000", 1_000L)),
            calls = listOf(call("13800000000", 4_000)),
        )

        assertEquals(4_000L, RecentContactTimes.latestForNumber(times, "13800000000"))
    }

    private fun thread(number: String, date: Long, isGroup: Boolean = false) = ConversationThread(
        conversationId = 1L,
        roleId = 0L,
        role = null,
        peerNumber = number,
        title = number,
        photoUri = "",
        snippet = "",
        date = date,
        read = true,
        isGroupConversation = isGroup,
        isScheduled = false,
        isPinned = false,
        isAvailable = true,
    )

    private fun call(number: String, startTS: Int) = CallRecord(
        id = 1,
        phoneNumber = number,
        displayNumber = number,
        name = "",
        photoUri = "",
        startTS = startTS,
        duration = 0,
        type = 0,
        simID = -1,
    )
}
