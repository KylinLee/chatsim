package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.CallRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class CallLogGrouperTest {
    @Test
    fun groupsConsecutiveCallsFromSameNumber() {
        val calls = listOf(
            call(id = 1, number = "13800000000", ts = 300),
            call(id = 2, number = "13800000000", ts = 200),
            call(id = 3, number = "13800000000", ts = 100),
        )

        val grouped = CallLogGrouper.group(calls)

        assertEquals(1, grouped.size)
        assertEquals(listOf(2, 3), grouped.first().neighbourIDs)
        assertEquals(3, grouped.first().groupedCount)
    }

    @Test
    fun doesNotGroupDifferentNumbersOrSims() {
        val calls = listOf(
            call(id = 1, number = "13800000000", simID = 1, ts = 300),
            call(id = 2, number = "13800000000", simID = 2, ts = 200),
            call(id = 3, number = "13900000000", simID = 2, ts = 100),
        )

        val grouped = CallLogGrouper.group(calls)

        assertEquals(3, grouped.size)
    }

    @Test
    fun keepsSingleCall() {
        val calls = listOf(
            call(id = 1, number = "13800000000", ts = 300),
        )

        val grouped = CallLogGrouper.group(calls)

        assertEquals(1, grouped.size)
    }

    private fun call(id: Int, number: String, simID: Int = 0, ts: Int): CallRecord = CallRecord(
        id = id,
        phoneNumber = number,
        displayNumber = number,
        name = number,
        photoUri = "",
        startTS = ts,
        duration = 0,
        type = 0,
        simID = simID,
    )
}
