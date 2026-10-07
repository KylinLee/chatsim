package io.github.kylinlee.chatsim.domain.model

import io.github.kylinlee.chatsim.data.model.Message

sealed interface TimelineItem {
    val timestamp: Long
    val key: String

    data class MessageItem(val message: Message) : TimelineItem {
        override val timestamp: Long
            get() = message.date.toLong()

        override val key: String
            get() = "message:${message.id}"
    }

    data class CallItem(val call: CallRecord) : TimelineItem {
        override val timestamp: Long
            get() = call.startTS.toLong()

        override val key: String
            get() = "call:${call.id}"
    }
}
