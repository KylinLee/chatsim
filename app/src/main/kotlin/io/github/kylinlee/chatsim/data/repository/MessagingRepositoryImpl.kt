package io.github.kylinlee.chatsim.data.repository

import android.content.Context
import io.github.kylinlee.chatsim.di.IoDispatcher
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.domain.rule.OutgoingEvent
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import io.github.kylinlee.chatsim.domain.rule.RoleRuleContext
import io.github.kylinlee.chatsim.data.messaging.sendMessageCompat
import io.github.kylinlee.chatsim.repository.MessagingRepository
import io.github.kylinlee.chatsim.repository.RoleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roleRepository: RoleRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MessagingRepository {
    override suspend fun sendMessage(
        text: String,
        addresses: List<String>,
        subscriptionId: Int?,
        messageId: Long?,
        applyRole: Boolean,
        role: Role?,
    ) = withContext(ioDispatcher) {
        val activeRole = roleRepository.getActiveRole()
        val requestedRoleId = if (applyRole) {
            role?.id ?: activeRole?.id
        } else {
            null
        }
        val ruleContext = RoleRuleContext(roleRepository.getRoles(), activeRoleId = activeRole?.id)
        val routes = addresses.map { address ->
            RuleEngines.engine.routeOutgoing(
                OutgoingEvent(
                    address = address,
                    channel = RoleChannel.SMS,
                    requestedRoleId = requestedRoleId,
                    subscriptionId = subscriptionId,
                ),
                ruleContext,
            )
        }

        val finalAddresses = routes.map { it.number }
        val finalSubscriptionId = routes.firstOrNull()?.subscriptionId ?: subscriptionId
        context.sendMessageCompat(text, finalAddresses, finalSubscriptionId, messageId)
    }
}
