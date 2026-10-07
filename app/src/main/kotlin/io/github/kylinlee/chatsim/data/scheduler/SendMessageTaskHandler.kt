package io.github.kylinlee.chatsim.data.scheduler

import android.content.Context
import io.github.kylinlee.chatsim.common.extensions.conversationsDB
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.data.local.SharedPrefsRoleStore
import io.github.kylinlee.chatsim.data.messaging.sendMessageCompatOrThrow
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.domain.rule.OutgoingEvent
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import io.github.kylinlee.chatsim.domain.rule.RoleRuleContext
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTask
import io.github.kylinlee.chatsim.domain.scheduler.ScheduledTaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 发送一条定时消息：按规则路由后从主线程发短信，成功后删除影子消息。 */
object SendMessageTaskHandler : ScheduledTaskHandler {
    override val type: ScheduledTaskType = ScheduledTaskType.SEND_MESSAGE

    override suspend fun execute(context: Context, task: ScheduledTask) {
        val message = context.messagesDB.getByIds(listOf(task.id)).firstOrNull()
            ?: throw IllegalStateException("scheduled message ${task.id} is missing")
        val conversation = context.conversationsDB.getConversation(task.conversationId)
            ?: throw IllegalStateException("conversation ${task.conversationId} is missing")
        val roles = SharedPrefsRoleStore(context).loadRoles()
        val role = roles.firstOrNull { it.id == conversation.roleId }
            ?: throw IllegalStateException("role ${conversation.roleId} is missing")

        val route = RuleEngines.engine.routeOutgoing(
            OutgoingEvent(
                address = conversation.peerNumber,
                channel = RoleChannel.SMS,
                requestedRoleId = role.id,
                subscriptionId = role.subscriptionId,
            ),
            RoleRuleContext(roles, activeRoleId = role.id),
        )

        withContext(Dispatchers.Main) {
            context.sendMessageCompatOrThrow(
                text = message.body,
                addresses = listOf(route.number),
                subId = route.subscriptionId ?: message.subscriptionId,
                deleteSystemMessageOnFailure = true,
            )
        }

        context.messagesDB.delete(task.id)
    }
}
