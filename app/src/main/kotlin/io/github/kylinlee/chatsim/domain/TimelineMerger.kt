package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.domain.model.TimelineItem
import io.github.kylinlee.chatsim.data.model.Message

object TimelineMerger {
    fun merge(messages: List<Message>, calls: List<CallRecord>): List<TimelineItem> {
        val items = ArrayList<TimelineItem>(messages.size + calls.size)
        messages.forEach { items.add(TimelineItem.MessageItem(it)) }
        calls.forEach { items.add(TimelineItem.CallItem(it)) }

        return items.sortedWith(compareBy({ it.timestamp }, { it.key }))
    }
}
