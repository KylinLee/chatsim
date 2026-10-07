package io.github.kylinlee.chatsim.domain.model

data class ConversationThread(
    val conversationId: Long,
    val roleId: Long,
    val role: Role?,
    val peerNumber: String,
    val title: String,
    val photoUri: String,
    val snippet: String,
    val date: Long,
    val read: Boolean,
    val isGroupConversation: Boolean,
    val isScheduled: Boolean,
    val isPinned: Boolean,
    val isAvailable: Boolean,
    val lastCall: CallRecord? = null,
    val hasMessages: Boolean = true,
) {
    val isCallOnly: Boolean
        get() = !hasMessages
}
