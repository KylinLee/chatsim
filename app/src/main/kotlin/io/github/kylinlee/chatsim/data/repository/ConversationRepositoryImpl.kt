package io.github.kylinlee.chatsim.data.repository

import android.content.Context
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.di.IoDispatcher
import io.github.kylinlee.chatsim.common.extensions.conversationsDB
import io.github.kylinlee.chatsim.common.extensions.conversationSnippet
import io.github.kylinlee.chatsim.common.extensions.deleteConversation
import io.github.kylinlee.chatsim.common.extensions.getConversations
import io.github.kylinlee.chatsim.common.extensions.getThreadId
import io.github.kylinlee.chatsim.common.extensions.getThreadSubscriptionIds
import io.github.kylinlee.chatsim.common.extensions.markMessagesRead
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.common.extensions.updateUnreadCountBadge
import io.github.kylinlee.chatsim.domain.ContactFilter
import io.github.kylinlee.chatsim.domain.ResolvedNumber
import io.github.kylinlee.chatsim.domain.RolePrefixer
import io.github.kylinlee.chatsim.domain.displayName
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.domain.model.RoleChannel
import io.github.kylinlee.chatsim.domain.rule.IncomingEvent
import io.github.kylinlee.chatsim.domain.rule.IncomingKind
import io.github.kylinlee.chatsim.domain.rule.OutgoingEvent
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import io.github.kylinlee.chatsim.domain.rule.RoleRuleContext
import io.github.kylinlee.chatsim.data.model.Conversation
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.data.model.SystemConversation
import io.github.kylinlee.chatsim.data.rules.UserRuleExecutor
import io.github.kylinlee.chatsim.data.rules.toRuleRecord
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleOutcome
import io.github.kylinlee.chatsim.repository.ContactRepository
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.MessageRepository
import io.github.kylinlee.chatsim.repository.RoleRepository
import io.github.kylinlee.chatsim.repository.ScheduledTaskRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val roleRepository: RoleRepository,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val scheduledTaskRepository: ScheduledTaskRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ConversationRepository {
    private val _conversations = MutableStateFlow<List<ConversationThread>>(emptyList())

    override val conversations: StateFlow<List<ConversationThread>> = _conversations.asStateFlow()

    override suspend fun loadConversations(): List<ConversationThread> = withContext(ioDispatcher) {
        readLocalThreads().also {
            _conversations.value = it
            refreshBadge()
        }
    }

    override suspend fun syncWithSystem(): List<ConversationThread> = withContext(ioDispatcher) {
        try {
            roleRepository.ensureSimRoles()
            if (contactRepository.getCachedContacts().isEmpty()) {
                contactRepository.loadContacts()
            }
            val roles = roleRepository.getRoles()
            val fallbackRole = roleRepository.getActiveRole()
            val systemThreads = context.getConversations()
            val threadSubscriptionIds = context.getThreadSubscriptionIds()
            val existingConversations = context.conversationsDB.getAllConversations()
                .associateBy { it.roleId to it.peerNumber }
                .toMutableMap()
            val scheduledConversationIds = context.messagesDB.getScheduledConversationIds().toSet()

            val systemKeys = mutableSetOf<Pair<Long, String>>()
            systemThreads.forEach { thread ->
                val resolved = resolveSystemThread(thread, roles, fallbackRole, threadSubscriptionIds) ?: return@forEach
                systemKeys.add(resolved.role.id to resolved.peerNumber)
                upsertConversation(
                    conversations = existingConversations,
                    role = resolved.role,
                    peerNumber = resolved.peerNumber,
                    snippet = thread.snippet,
                    date = thread.date,
                    read = thread.read,
                    fallbackTitle = thread.title,
                    fallbackPhoto = thread.photoUri,
                    rawAddress = thread.phoneNumber,
                    isGroup = thread.isGroupConversation,
                )
            }

            pruneMissingConversations(systemKeys, existingConversations.values.toList(), scheduledConversationIds)

            readLocalThreads().also { _conversations.value = it }
        } catch (e: Exception) {
            android.util.Log.e("ChatSimSync", "syncWithSystem failed", e)
            readLocalThreads().also { _conversations.value = it }
        }.also { refreshBadge() }
    }

    override suspend fun refresh(): List<ConversationThread> = syncWithSystem()

    override fun getCachedConversations(): List<ConversationThread> = _conversations.value

    override fun search(query: String): List<ConversationThread> {
        if (query.isBlank()) return _conversations.value

        val normalizedQuery = ContactFilter.normalizeText(query)
        val queryDigits = ContactFilter.normalizePhoneNumber(query)

        return _conversations.value.filter { thread ->
            ContactFilter.normalizeText(thread.title).contains(normalizedQuery) ||
                ContactFilter.normalizeText(thread.snippet).contains(normalizedQuery) ||
                ContactFilter.normalizeText(thread.role?.label.orEmpty()).contains(normalizedQuery) ||
                (queryDigits.isNotEmpty() && ContactFilter.phoneMatches(thread.peerNumber, queryDigits))
        }
    }

    override suspend fun getConversation(conversationId: Long): ConversationThread? = withContext(ioDispatcher) {
        context.conversationsDB.getConversation(conversationId)?.toThread()
    }

    override suspend fun findConversationId(roleId: Long, peerNumber: String): Long? = withContext(ioDispatcher) {
        context.conversationsDB.getConversationByKey(roleId, peerNumber)?.conversationId
    }

    override suspend fun getOrCreateConversation(roleId: Long, peerNumber: String): Long = withContext(ioDispatcher) {
        val existing = context.conversationsDB.getConversationByKey(roleId, peerNumber)
        if (existing != null) {
            val title = resolveTitle(peerNumber, existing.title, peerNumber)
            if (title != existing.title) {
                context.conversationsDB.insertOrUpdate(existing.copy(title = title))
            }
            return@withContext existing.conversationId
        }

        context.conversationsDB.insertOrUpdate(
            Conversation(
                roleId = roleId,
                peerNumber = peerNumber,
                snippet = "",
                date = 0,
                read = true,
                title = resolveTitle(peerNumber, peerNumber, peerNumber),
                photoUri = resolvePhoto(peerNumber, ""),
                isGroupConversation = false,
            )
        )
    }

    override suspend fun getSystemThreadId(roleId: Long, peerNumber: String): Long = withContext(ioDispatcher) {
        val role = roleRepository.getRole(roleId)
        val route = RuleEngines.engine.routeOutgoing(
            OutgoingEvent(
                address = peerNumber,
                channel = RoleChannel.SMS,
                requestedRoleId = role?.id,
                subscriptionId = role?.subscriptionId,
            ),
            RoleRuleContext(roleRepository.getRoles(), activeRoleId = roleRepository.getActiveRole()?.id),
        )
        context.getThreadId(route.number)
    }

    override suspend fun persistSystemMessages(
        conversationId: Long,
        peerNumber: String,
        messages: List<Message>,
    ) = withContext(ioDispatcher) {
        if (conversationId <= 0 || messages.isEmpty()) return@withContext

        val existingDeliveryStatus = context.messagesDB.getByIds(messages.map { it.id })
            .associate { it.id to it.deliveryStatus }
        val resolved = messages.map { message ->
            message.copy(
                conversationId = conversationId,
                senderPhoneNumber = peerNumber,
                deliveryStatus = existingDeliveryStatus[message.id] ?: message.deliveryStatus,
            )
        }
        val roleId = context.conversationsDB.getConversation(conversationId)?.roleId
        val knownIds = context.messagesDB.getExistingIds(resolved.map { it.id }).toHashSet()

        val toInsert = ArrayList<Message>(resolved.size)
        val newOutcomes = ArrayList<Pair<Message, UserRuleOutcome>>()
        resolved.forEach { message ->
            if (message.id in knownIds) {
                toInsert.add(message)
                return@forEach
            }

            val record = message.toRuleRecord(roleId)
            val outcome = UserRuleExecutor.evaluate(context, record)
            if (outcome.blocked) {
                UserRuleExecutor.recordBlockedHits(context, outcome, record, message.senderName)
            } else {
                toInsert.add(message)
                newOutcomes.add(message to outcome)
            }
        }

        context.messagesDB.insertMessages(*toInsert.toTypedArray())
        newOutcomes.forEach { (message, outcome) ->
            UserRuleExecutor.applyToStored(context, message.id, outcome, message.toRuleRecord(roleId), message.senderName)
        }
        refreshConversation(conversationId)
    }

    override suspend fun restoreConversations(peerNumber: String): Int = withContext(ioDispatcher) {
        var restored = 0
        roleRepository.getRoles().forEach { role ->
            val threadId = getSystemThreadId(role.id, peerNumber)
            if (threadId <= 0) return@forEach

            val conversationId = getOrCreateConversation(role.id, peerNumber)
            val messages = messageRepository.loadSystemMessages(
                threadId = threadId,
                getImageResolutions = false,
                includeScheduledMessages = false,
            )
            if (messages.isNotEmpty()) {
                persistSystemMessages(conversationId, peerNumber, messages)
            }
            refreshConversation(conversationId)
            restored++
        }
        restored
    }

    override suspend fun getRecycleBinConversations(): List<ConversationThread> = withContext(ioDispatcher) {
        try {
            context.conversationsDB.getAllWithMessagesInRecycleBin().map { it.toThread() }.sortedByDescending { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun markRead(conversationId: Long) = withContext(ioDispatcher) {
        val messageIds = context.messagesDB.getConversationMessageIds(conversationId)
        context.markMessagesRead(messageIds)
        context.messagesDB.markConversationRead(conversationId)
        context.conversationsDB.markRead(conversationId)
        refreshBadge()
    }

    override suspend fun markUnread(conversationId: Long) = withContext(ioDispatcher) {
        context.conversationsDB.markUnread(conversationId)
        refreshBadge()
    }

    override suspend fun deleteConversation(conversationId: Long) = withContext(ioDispatcher) {
        scheduledTaskRepository.cancelForConversation(conversationId)
        context.deleteConversation(conversationId)
        refreshBadge()
    }

    override suspend fun refreshConversation(conversationId: Long) = withContext(ioDispatcher) {
        if (conversationId <= 0) return@withContext

        val conversation = context.conversationsDB.getConversation(conversationId) ?: return@withContext
        val date = context.messagesDB.getLatestConversationDate(conversationId) ?: 0
        if (date == 0) {
            context.conversationsDB.deleteConversation(conversationId)
        } else {
            val latest = context.messagesDB.getLatestConversationMessage(conversationId)
            context.conversationsDB.insertOrUpdate(conversation.copy(snippet = context.conversationSnippet(latest), date = date))
        }
    }

    override suspend fun applyMessage(conversationId: Long, message: Message, markUnread: Boolean) = withContext(ioDispatcher) {
        val existing = context.conversationsDB.getConversation(conversationId) ?: return@withContext

        context.conversationsDB.insertOrUpdate(
            existing.copy(
                snippet = message.body,
                date = maxOf(existing.date, message.date),
                read = if (markUnread) false else existing.read,
                title = resolveTitle(existing.peerNumber, existing.title, existing.peerNumber),
                photoUri = resolvePhoto(existing.peerNumber, existing.photoUri),
            )
        )
    }

    override fun pinConversation(conversationId: Long) {
        settings.pinConversation(conversationId)
        updatePinnedState(conversationId, isPinned = true)
    }

    override fun unpinConversation(conversationId: Long) {
        settings.unpinConversation(conversationId)
        updatePinnedState(conversationId, isPinned = false)
    }

    private fun updatePinnedState(conversationId: Long, isPinned: Boolean) {
        _conversations.value = _conversations.value.map {
            if (it.conversationId == conversationId) it.copy(isPinned = isPinned) else it
        }
    }

    private fun refreshBadge() {
        context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
    }

    private fun readLocalThreads(): List<ConversationThread> {
        return try {
            context.conversationsDB.getAll()
                .filter { it.date > 0 }
                .map { it.toThread() }
                .sortedByDescending { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Conversation.toThread(): ConversationThread {
        val role = roleRepository.getRole(roleId)
        return ConversationThread(
            conversationId = conversationId,
            roleId = roleId,
            role = role,
            peerNumber = peerNumber,
            title = title.ifBlank { peerNumber },
            photoUri = photoUri,
            snippet = snippet,
            date = date.toLong(),
            read = read,
            isGroupConversation = isGroupConversation,
            isScheduled = isScheduled,
            isPinned = settings.pinnedConversations.contains(conversationId.toString()),
            isAvailable = roleRepository.isAvailable(role),
        )
    }

    private fun resolveSystemThread(
        thread: SystemConversation,
        roles: List<Role>,
        fallbackRole: Role?,
        threadSubscriptionIds: Map<Long, Int>,
    ): ResolvedNumber? {
        val address = thread.phoneNumber
        if (address.isBlank()) return null

        val subscriptionId = if (RolePrefixer.matchRole(address, roles, RoleChannel.SMS) == null) {
            threadSubscriptionIds[thread.threadId]
        } else {
            null
        }

        return RuleEngines.engine.evaluateIncoming(
            IncomingEvent(IncomingKind.SMS, address, subscriptionId),
            RoleRuleContext(roles, activeRoleId = fallbackRole?.id, fallbackRoleId = fallbackRole?.id),
        ).resolution
    }

    private fun upsertConversation(
        conversations: MutableMap<Pair<Long, String>, Conversation>,
        role: Role,
        peerNumber: String,
        snippet: String,
        date: Int,
        read: Boolean,
        fallbackTitle: String,
        fallbackPhoto: String,
        rawAddress: String,
        isGroup: Boolean,
    ) {
        val key = role.id to peerNumber
        val existing = conversations[key]
        val title = resolveTitle(peerNumber, fallbackTitle, rawAddress)
        val photoUri = resolvePhoto(peerNumber, fallbackPhoto)

        if (existing == null) {
            val conversation = Conversation(
                roleId = role.id,
                peerNumber = peerNumber,
                snippet = snippet,
                date = date,
                read = read,
                title = title,
                photoUri = photoUri,
                isGroupConversation = isGroup,
            )
            val conversationId = context.conversationsDB.insertOrUpdate(conversation)
            conversations[key] = conversation.copy(conversationId = conversationId)
        } else {
            val updated = existing.copy(
                // 本地已有更新的内容（如定时消息）时不要用旧的系统摘要覆盖
                snippet = if (date >= existing.date) {
                    snippet.ifEmpty { existing.snippet }
                } else {
                    existing.snippet
                },
                date = maxOf(existing.date, date),
                title = title.ifEmpty { existing.title },
                photoUri = photoUri.ifEmpty { existing.photoUri },
                isGroupConversation = isGroup,
            )
            if (updated != existing) {
                context.conversationsDB.insertOrUpdate(updated)
                conversations[key] = updated
            }
        }
    }

    private fun pruneMissingConversations(
        systemKeys: Set<Pair<Long, String>>,
        allConversations: List<Conversation>,
        scheduledConversationIds: Set<Long>,
    ) {
        val callConversationIds = context.messagesDB.getCallConversationIds().toSet()

        allConversations.forEach { conversation ->
            if (conversation.isScheduled) return@forEach
            if (conversation.conversationId in scheduledConversationIds) return@forEach
            if (conversation.conversationId in callConversationIds) return@forEach

            val key = conversation.roleId to conversation.peerNumber
            if (key !in systemKeys) {
                context.conversationsDB.deleteConversation(conversation.conversationId)
                context.messagesDB.deleteConversationMessages(conversation.conversationId)
            }
        }
    }

    private fun resolveTitle(peerNumber: String, fallback: String, rawAddress: String): String {
        val contact = findContact(peerNumber)
        if (contact != null) return contact.displayName()

        return fallback.takeIf { it.isNotBlank() && it != rawAddress } ?: peerNumber
    }

    private fun resolvePhoto(peerNumber: String, fallback: String): String {
        val contact = findContact(peerNumber)
        return contact?.photoUri?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun findContact(peerNumber: String): Contact? {
        if (peerNumber.isBlank()) return null
        return contactRepository.getContactByNumber(peerNumber)
    }
}
