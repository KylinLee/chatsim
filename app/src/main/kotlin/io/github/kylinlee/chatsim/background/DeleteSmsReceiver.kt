package io.github.kylinlee.chatsim.background


import io.github.kylinlee.chatsim.common.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kylinlee.chatsim.common.extensions.notificationManager
import io.github.kylinlee.chatsim.common.ensureBackgroundThread
import io.github.kylinlee.chatsim.common.extensions.conversationsDB
import io.github.kylinlee.chatsim.common.extensions.deleteMessage
import io.github.kylinlee.chatsim.common.extensions.updateLastConversationMessage
import io.github.kylinlee.chatsim.common.extensions.updateUnreadCountBadge
import io.github.kylinlee.chatsim.common.CONVERSATION_ID
import io.github.kylinlee.chatsim.common.IS_MMS
import io.github.kylinlee.chatsim.common.MESSAGE_ID
import io.github.kylinlee.chatsim.common.refreshMessages

class DeleteSmsReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getLongExtra(CONVERSATION_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        context.notificationManager.cancel(conversationId.hashCode())
        ensureBackgroundThread {
            context.deleteMessage(messageId, isMms)
            context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
            context.updateLastConversationMessage(conversationId)
            refreshMessages()
        }
    }
}
