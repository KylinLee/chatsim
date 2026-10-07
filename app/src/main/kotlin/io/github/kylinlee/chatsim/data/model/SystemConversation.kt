package io.github.kylinlee.chatsim.data.model

/** A raw conversation thread read from the system telephony provider. */
data class SystemConversation(
    val threadId: Long,
    val snippet: String,
    val date: Int,
    val read: Boolean,
    val title: String,
    val photoUri: String,
    val isGroupConversation: Boolean,
    val phoneNumber: String,
)
