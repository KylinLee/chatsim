package io.github.kylinlee.chatsim.data.repository

import android.content.Context
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.common.extensions.ruleHitsDB
import io.github.kylinlee.chatsim.common.extensions.userTagsDB
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.model.RuleHitEntity
import io.github.kylinlee.chatsim.data.model.UserTagEntity
import io.github.kylinlee.chatsim.data.rules.UserRuleExecutor
import io.github.kylinlee.chatsim.data.rules.UserRuleStore
import io.github.kylinlee.chatsim.di.IoDispatcher
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.rule.user.UserRule
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import io.github.kylinlee.chatsim.repository.CallLogRepository
import io.github.kylinlee.chatsim.repository.MessageRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import io.github.kylinlee.chatsim.repository.UserRuleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRuleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: AppEventBus,
    private val settings: SettingsRepository,
    private val messageRepository: MessageRepository,
    private val callLogRepository: CallLogRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserRuleRepository {
    private val _rules = MutableStateFlow<List<UserRule>>(emptyList())
    override val rules: StateFlow<List<UserRule>> = _rules.asStateFlow()

    private val _tags = MutableStateFlow<List<UserTagEntity>>(emptyList())
    override val tags: StateFlow<List<UserTagEntity>> = _tags.asStateFlow()

    override suspend fun refresh() = withContext(ioDispatcher) {
        refreshInternal()
    }

    override suspend fun saveRule(rule: UserRule): Long = withContext(ioDispatcher) {
        val id = UserRuleStore.save(context, rule)
        refreshInternal()
        eventBus.tryEmit(AppEvent.RefreshRules)
        id
    }

    override suspend fun setRuleEnabled(id: Long, enabled: Boolean) = withContext(ioDispatcher) {
        UserRuleStore.setEnabled(context, id, enabled)
        refreshInternal()
        eventBus.tryEmit(AppEvent.RefreshRules)
    }

    override suspend fun deleteRule(id: Long) = withContext(ioDispatcher) {
        UserRuleStore.delete(context, id)
        refreshInternal()
        eventBus.tryEmit(AppEvent.RefreshRules)
    }

    override suspend fun deleteTag(name: String): Boolean = withContext(ioDispatcher) {
        val deleted = UserRuleStore.deleteTag(context, name)
        if (deleted) {
            refreshInternal()
            eventBus.tryEmit(AppEvent.RefreshRules)
        }
        deleted
    }

    override suspend fun addTag(name: String) = withContext(ioDispatcher) {
        UserRuleStore.ensureTags(context, listOf(name.trim()))
        refreshInternal()
        eventBus.tryEmit(AppEvent.RefreshRules)
    }

    override suspend fun trashRuleCount(): Int = withContext(ioDispatcher) {
        UserRuleStore.countByAction(context, UserRuleAction.TRASH)
    }

    override suspend fun runRules(): Int = withContext(ioDispatcher) {
        val processed = UserRuleExecutor.runAll(context, UserRuleStore.rules(context))
        if (processed > 0) {
            eventBus.tryEmit(AppEvent.RefreshRules)
            eventBus.tryEmit(AppEvent.RefreshMessages)
        }
        processed
    }

    override suspend fun runRule(id: Long): Int = withContext(ioDispatcher) {
        val rule = UserRuleStore.rules(context).firstOrNull { it.id == id } ?: return@withContext 0
        val processed = UserRuleExecutor.runRule(context, rule)
        if (processed > 0) {
            eventBus.tryEmit(AppEvent.RefreshRules)
            eventBus.tryEmit(AppEvent.RefreshMessages)
        }
        processed
    }

    override suspend fun hits(action: UserRuleAction): List<RuleHitEntity> = withContext(ioDispatcher) {
        UserRuleStore.hits(context, action)
    }

    override suspend fun deleteRecords(hitIds: Set<Long>, toRecycleBin: Boolean) =
        withContext(ioDispatcher) {
            if (hitIds.isEmpty()) return@withContext

            val hits = context.ruleHitsDB.getByIds(hitIds.toList())
            if (hits.isEmpty()) return@withContext

            val entityIds = hits.map { it.messageId }.filter { it != 0L }.distinct()

            // 命中记录先删：关联的消息/通话可能已被删除，删除命中必须始终生效
            context.ruleHitsDB.deleteByIds(hits.map { it.id })
            if (entityIds.isNotEmpty()) {
                context.ruleHitsDB.deleteByMessageIds(entityIds)
            }

            // 只处理仍存在的关联实体，已删除的直接跳过
            val messages = if (entityIds.isEmpty()) emptyList() else context.messagesDB.getByIds(entityIds)
            if (messages.isEmpty()) return@withContext

            if (toRecycleBin && settings.useRecycleBin) {
                messageRepository.moveMessagesToRecycleBin(messages)
            } else {
                val calls = messages.filter { it.isCall }
                if (calls.isNotEmpty()) {
                    callLogRepository.removeCalls(calls.map { (-it.id).toInt() })
                }
                val others = messages.filterNot { it.isCall }
                if (others.isNotEmpty()) {
                    messageRepository.deleteMessages(others)
                }
            }

            eventBus.tryEmit(AppEvent.RefreshMessages)
            if (messages.any { it.isCall }) {
                eventBus.tryEmit(AppEvent.RefreshCallLog)
            }
        }

    private fun refreshInternal() {
        _rules.value = UserRuleStore.reload(context)
        _tags.value = context.userTagsDB.getAll()
    }
}
