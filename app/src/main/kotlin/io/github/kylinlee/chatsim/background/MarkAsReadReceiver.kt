package io.github.kylinlee.chatsim.background


import io.github.kylinlee.chatsim.common.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kylinlee.chatsim.common.extensions.notificationManager
import io.github.kylinlee.chatsim.common.ensureBackgroundThread
import io.github.kylinlee.chatsim.common.extensions.conversationsDB
import io.github.kylinlee.chatsim.common.extensions.markMessagesRead
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.common.extensions.updateUnreadCountBadge
import io.github.kylinlee.chatsim.common.CONVERSATION_ID
import io.github.kylinlee.chatsim.common.MARK_AS_READ
import io.github.kylinlee.chatsim.common.refreshMessages
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.domain.model.AppEvent

class MarkAsReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MARK_AS_READ -> {
                val conversationId = intent.getLongExtra(CONVERSATION_ID, 0L)
                context.notificationManager.cancel(conversationId.hashCode())
                ensureBackgroundThread {
                    val ids = context.messagesDB.getConversationMessageIds(conversationId)
                    context.markMessagesRead(ids)
                    context.conversationsDB.markRead(conversationId)
                    context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                    AppEventBus.tryEmit(AppEvent.ConversationRead(conversationId, read = true))
                    refreshMessages()
                }
            }
        }
    }
}
