package io.github.kylinlee.chatsim.domain

object UnreadScroll {
    fun nextUnreadId(unreadIds: List<Long>, currentId: Long?): Long? {
        if (unreadIds.isEmpty()) return null
        if (currentId == null) return unreadIds.first()
        val index = unreadIds.indexOf(currentId)
        if (index < 0) return unreadIds.first()
        return unreadIds[(index + 1) % unreadIds.size]
    }
}
