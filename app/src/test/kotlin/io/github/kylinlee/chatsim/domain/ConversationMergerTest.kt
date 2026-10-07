package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import io.github.kylinlee.chatsim.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMergerTest {
    private val role = Role(id = 5, label = "SIM 1", kind = io.github.kylinlee.chatsim.domain.model.RoleKind.SIM, subscriptionId = 1)

    @Test
    fun attachesLatestCallToMatchingConversationAndBumpsDate() {
        val thread = thread(conversationId = 1, number = "13800000000", date = 100)
        val calls = listOf(
            call(id = 1, number = "13800000000", ts = 200),
            call(id = 2, number = "13800000000", ts = 150),
        )

        val merged = ConversationMerger.merge(listOf(thread), calls)

        assertEquals(1, merged.size)
        assertEquals(200L, merged.first().date)
        assertEquals(1, merged.first().lastCall?.id)
        assertTrue(merged.first().hasMessages)
    }

    @Test
    fun keepsConversationDateWhenItIsNewerThanCall() {
        val thread = thread(conversationId = 1, number = "13800000000", date = 500)
        val calls = listOf(call(id = 1, number = "13800000000", ts = 200))

        val merged = ConversationMerger.merge(listOf(thread), calls)

        assertEquals(500L, merged.first().date)
        assertNotNull(merged.first().lastCall)
    }

    @Test
    fun callsWithDifferentRoleDoNotMatch() {
        val thread = thread(conversationId = 1, number = "13800000000", date = 100)
        val otherRole = role.copy(id = 9)
        val calls = listOf(call(id = 1, number = "13800000000", ts = 200, role = otherRole))

        val merged = ConversationMerger.merge(listOf(thread), calls)

        assertEquals(2, merged.size)
    }

    @Test
    fun createsSyntheticConversationForCallOnlyNumbers() {
        val calls = listOf(call(id = 7, number = "13900000000", ts = 300))

        val merged = ConversationMerger.merge(emptyList(), calls)

        assertEquals(1, merged.size)
        val callOnly = merged.first()
        assertTrue(callOnly.isCallOnly)
        assertFalse(callOnly.hasMessages)
        assertEquals("13900000000", callOnly.peerNumber)
        assertEquals(300L, callOnly.date)
        assertTrue(callOnly.conversationId < 0)
    }

    @Test
    fun sortsByDateDescending() {
        val threads = listOf(
            thread(conversationId = 1, number = "13800000000", date = 100),
            thread(conversationId = 2, number = "13900000000", date = 300),
        )

        val merged = ConversationMerger.merge(threads, emptyList())

        assertEquals(listOf(2L, 1L), merged.map { it.conversationId })
    }

    private fun thread(conversationId: Long, number: String, date: Long): ConversationThread = ConversationThread(
        conversationId = conversationId,
        roleId = role.id,
        role = role,
        peerNumber = number,
        title = number,
        photoUri = "",
        snippet = "snippet",
        date = date,
        read = true,
        isGroupConversation = false,
        isScheduled = false,
        isPinned = false,
        isAvailable = true,
    )

    private fun call(id: Int, number: String, ts: Int, role: Role? = this.role): CallRecord = CallRecord(
        id = id,
        phoneNumber = number,
        displayNumber = number,
        name = number,
        photoUri = "",
        startTS = ts,
        duration = 0,
        type = 0,
        simID = 0,
        role = role,
    )
}
