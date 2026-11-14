package io.github.kylinlee.chatsim.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnreadScrollTest {
    @Test
    fun returnsNullWithoutUnreadConversations() {
        assertNull(UnreadScroll.nextUnreadId(emptyList(), 2L))
    }

    @Test
    fun startsAtFirstUnreadWithoutCurrent() {
        assertEquals(5L, UnreadScroll.nextUnreadId(listOf(5L, 7L, 9L), null))
    }

    @Test
    fun movesToNextUnreadInListOrder() {
        val unreadIds = listOf(5L, 7L, 9L)

        assertEquals(7L, UnreadScroll.nextUnreadId(unreadIds, 5L))
        assertEquals(9L, UnreadScroll.nextUnreadId(unreadIds, 7L))
    }

    @Test
    fun wrapsBackToFirstUnreadAfterTheLast() {
        assertEquals(5L, UnreadScroll.nextUnreadId(listOf(5L, 7L, 9L), 9L))
    }

    @Test
    fun restartsAtFirstUnreadWhenCurrentIsNotUnreadAnymore() {
        assertEquals(5L, UnreadScroll.nextUnreadId(listOf(5L, 7L, 9L), 6L))
    }

    @Test
    fun singleUnreadConversationCyclesToItself() {
        assertEquals(5L, UnreadScroll.nextUnreadId(listOf(5L), 5L))
    }
}
