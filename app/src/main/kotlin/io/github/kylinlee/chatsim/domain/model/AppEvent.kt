package io.github.kylinlee.chatsim.domain.model

sealed interface AppEvent {
    data object RefreshMessages : AppEvent

    data object RefreshConversations : AppEvent

    data object RefreshContacts : AppEvent

    data object RefreshCallLog : AppEvent

    data object CallOverlayChanged : AppEvent

    data object RefreshRules : AppEvent

    data class MessageReceived(val threadId: Long, val messageId: Long) : AppEvent

    data class MessageStatusChanged(val messageId: Long) : AppEvent

    data class ConversationsChanged(val threadId: Long) : AppEvent

    data class ConversationRead(val conversationId: Long, val read: Boolean = true) : AppEvent

    data class Error(val throwable: Throwable? = null) : AppEvent
}
