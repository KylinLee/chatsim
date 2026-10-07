package io.github.kylinlee.chatsim.background


import io.github.kylinlee.chatsim.common.*
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.RemoteInput
import io.github.kylinlee.chatsim.common.extensions.showErrorToast
import io.github.kylinlee.chatsim.data.contacts.SimpleContactsHelper
import io.github.kylinlee.chatsim.common.ensureBackgroundThread
import io.github.kylinlee.chatsim.common.extensions.*
import io.github.kylinlee.chatsim.common.REPLY
import io.github.kylinlee.chatsim.common.CONVERSATION_ID
import io.github.kylinlee.chatsim.common.ROLE_ID
import io.github.kylinlee.chatsim.data.local.SharedPrefsRoleStore
import io.github.kylinlee.chatsim.data.messaging.sendMessageCompat
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.domain.rule.OutgoingEvent
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import io.github.kylinlee.chatsim.domain.rule.RoleRuleContext

class DirectReplyReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getLongExtra(CONVERSATION_ID, 0L)
        val roleId = intent.getLongExtra(ROLE_ID, 0L)
        var body = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REPLY)?.toString() ?: return

        body = context.removeDiacriticsIfNeeded(body)

        if (conversationId <= 0) return

        ensureBackgroundThread {
            val conversation = context.conversationsDB.getConversation(conversationId) ?: return@ensureBackgroundThread
            val roles = SharedPrefsRoleStore(context).loadRoles()
            val role = roles.firstOrNull { it.id == roleId } ?: roles.firstOrNull { it.id == conversation.roleId }
            val route = RuleEngines.engine.routeOutgoing(
                OutgoingEvent(
                    address = conversation.peerNumber,
                    channel = RoleChannel.SMS,
                    requestedRoleId = role?.id,
                    subscriptionId = role?.subscriptionId,
                ),
                RoleRuleContext(roles, activeRoleId = role?.id),
            )
            val destination = route.number

            var messageId = 0L
            try {
                context.sendMessageCompat(body, listOf(destination), route.subscriptionId)
                val threadId = context.getThreadId(destination)
                val message = context.getMessages(threadId, getImageResolutions = false, includeScheduledMessages = false, limit = 1).lastOrNull()
                if (message != null) {
                    context.messagesDB.insertOrUpdate(
                        message.copy(conversationId = conversationId, senderPhoneNumber = conversation.peerNumber)
                    )
                    messageId = message.id

                    context.updateLastConversationMessage(conversationId)
                }
            } catch (e: Exception) {
                context.showErrorToast(e)
            }

            val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(conversation.peerNumber)
            val bitmap = context.getNotificationBitmap(photoUri)
            Handler(Looper.getMainLooper()).post {
                context.notificationHelper.showMessageNotification(
                    messageId,
                    conversation.peerNumber,
                    body,
                    conversationId,
                    role?.id ?: conversation.roleId,
                    bitmap,
                    sender = null,
                    alertOnlyOnce = true
                )
            }

            val ids = context.messagesDB.getConversationMessageIds(conversationId)
            context.markMessagesRead(ids)
            context.conversationsDB.markRead(conversationId)
            context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
            refreshMessages()
        }
    }
}
