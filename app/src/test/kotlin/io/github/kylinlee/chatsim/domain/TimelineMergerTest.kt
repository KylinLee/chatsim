package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.domain.model.TimelineItem
import io.github.kylinlee.chatsim.data.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMergerTest {
    @Test
    fun mergeSortsMessagesAndCallsByTimestamp() {
        val messages = listOf(message(id = 1, date = 100), message(id = 2, date = 300))
        val calls = listOf(call(id = 1, ts = 200))

        val timeline = TimelineMerger.merge(messages, calls)

        assertEquals(listOf("message:1", "call:1", "message:2"), timeline.map { it.key })
        assertTrue(timeline[1] is TimelineItem.CallItem)
    }

    private fun message(id: Long, date: Int): Message = Message(
        id = id,
        body = "body",
        type = 1,
        status = 0,
        participants = ArrayList(),
        date = date,
        read = true,
        threadId = 1L,
        isMMS = false,
        attachment = null,
        senderPhoneNumber = "",
        senderName = "",
        senderPhotoUri = "",
        subscriptionId = 0,
    )

    private fun call(id: Int, number: String = "13800000000", ts: Int = 0): CallRecord = CallRecord(
        id = id,
        phoneNumber = number,
        displayNumber = number,
        name = number,
        photoUri = "",
        startTS = ts,
        duration = 0,
        type = 0,
        simID = 0,
    )
}
