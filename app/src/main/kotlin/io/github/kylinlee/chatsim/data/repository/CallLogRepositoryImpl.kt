package io.github.kylinlee.chatsim.data.repository

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog.Calls
import io.github.kylinlee.chatsim.common.extensions.getBlockedNumbers
import io.github.kylinlee.chatsim.common.extensions.getStringValueOrNull
import io.github.kylinlee.chatsim.common.extensions.getIntValue
import io.github.kylinlee.chatsim.common.extensions.getLongValue
import io.github.kylinlee.chatsim.common.extensions.getPhoneNumberTypeText
import io.github.kylinlee.chatsim.common.extensions.hasPermission
import io.github.kylinlee.chatsim.common.extensions.isNumberBlocked
import io.github.kylinlee.chatsim.common.extensions.normalizePhoneNumber
import io.github.kylinlee.chatsim.common.extensions.removeCallLogEntries
import io.github.kylinlee.chatsim.common.PERMISSION_READ_CALL_LOG
import io.github.kylinlee.chatsim.common.PERMISSION_WRITE_CALL_LOG
import io.github.kylinlee.chatsim.common.isNougatPlus
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.common.extensions.conversationsDB
import io.github.kylinlee.chatsim.common.extensions.getAvailableSIMCardLabels
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.data.model.RecentCall
import io.github.kylinlee.chatsim.data.model.toCallRecord
import io.github.kylinlee.chatsim.data.model.toMessage
import io.github.kylinlee.chatsim.data.rules.UserRuleExecutor
import io.github.kylinlee.chatsim.data.rules.toRuleRecord
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.di.IoDispatcher
import io.github.kylinlee.chatsim.domain.CallLogGrouper
import io.github.kylinlee.chatsim.domain.ContactFilter
import io.github.kylinlee.chatsim.domain.displayName
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.domain.rule.IncomingEvent
import io.github.kylinlee.chatsim.domain.rule.IncomingKind
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import io.github.kylinlee.chatsim.domain.rule.RoleRuleContext
import io.github.kylinlee.chatsim.repository.CallLogRepository
import io.github.kylinlee.chatsim.repository.ContactRepository
import io.github.kylinlee.chatsim.repository.ConversationRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import io.github.kylinlee.chatsim.repository.RoleRepository
import io.github.kylinlee.chatsim.repository.SimRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val contactRepository: ContactRepository,
    private val roleRepository: RoleRepository,
    private val simRepository: SimRepository,
    private val conversationRepository: ConversationRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CallLogRepository {
    private val _recentCalls = MutableStateFlow<List<CallRecord>>(emptyList())

    override val recentCalls: StateFlow<List<CallRecord>> = _recentCalls.asStateFlow()

    private val observerHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val invalidateCachedCalls = Runnable {
        scope.launch {
            runCatching { syncCalls() }
            AppEventBus.tryEmit(AppEvent.RefreshCallLog)
        }
    }

    init {
        context.contentResolver.registerContentObserver(
            Calls.CONTENT_URI,
            true,
            object : ContentObserver(observerHandler) {
                override fun onChange(selfChange: Boolean) {
                    // the call log is updated in several steps when a call ends, coalesce them
                    observerHandler.removeCallbacks(invalidateCachedCalls)
                    observerHandler.postDelayed(invalidateCachedCalls, CALL_LOG_DEBOUNCE_MS)
                }
            },
        )
    }

    override suspend fun syncCalls(maxSize: Int) = withContext(ioDispatcher) {
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) return@withContext

        val systemCalls = readFilteredCalls(maxSize)
        val systemIds = systemCalls.map { it.id.toLong() }.toHashSet()
        val localIds = context.messagesDB.getExistingIds(systemCalls.map { -it.id.toLong() }).toHashSet()

        val affected = HashSet<Long>()
        systemCalls.forEach { call ->
            val conversationId = conversationRepository.getOrCreateConversation(call.role?.id ?: 0L, call.displayNumber)
            val message = call.toMessage(conversationId)
            context.messagesDB.insertOrUpdate(message)
            if (-call.id.toLong() !in localIds) {
                val record = message.toRuleRecord(call.role?.id)
                val outcome = UserRuleExecutor.evaluate(context, record)
                if (!outcome.blocked) {
                    UserRuleExecutor.applyToStored(context, message.id, outcome, record, call.name)
                }
            }
            affected.add(conversationId)
        }

        val staleRows = context.messagesDB.getAllCalls().filterNot { -it.id in systemIds }
        if (staleRows.isNotEmpty()) {
            context.messagesDB.deleteFromMessages(staleRows.map { it.id })
            staleRows.map { it.conversationId }.forEach { affected.add(it) }
        }

        affected.forEach { conversationRepository.refreshConversation(it) }
        _recentCalls.value = emptyList()
    }

    override suspend fun loadRecentCalls(maxSize: Int): List<CallRecord> = withContext(ioDispatcher) {
        val grouped = CallLogGrouper.group(readLocalCalls()).take(maxSize)
        _recentCalls.value = grouped
        grouped
    }

    override suspend fun getConversationCalls(conversationId: Long): List<CallRecord> = withContext(ioDispatcher) {
        val role = context.conversationsDB.getConversation(conversationId)?.roleId?.let { roleRepository.getRole(it) }
        context.messagesDB.getConversationCalls(conversationId).map { it.toCallRecord(role) }
    }

    private fun readLocalCalls(): List<CallRecord> {
        val roles = roleRepository.getRoles().associateBy { it.id }
        val roleIdByConversation = context.conversationsDB.getAllConversations().associate { it.conversationId to it.roleId }
        return context.messagesDB.getAllCalls().map { row ->
            row.toCallRecord(roles[roleIdByConversation[row.conversationId]])
        }
    }

    override suspend fun refresh(): List<CallRecord> = loadRecentCalls()

    override fun getCachedCalls(): List<CallRecord> = _recentCalls.value

    override fun invalidateCache() {
        _recentCalls.value = emptyList()
    }

    override fun search(query: String): List<CallRecord> {
        if (query.isBlank()) return _recentCalls.value

        val normalizedQuery = ContactFilter.normalizeText(query)
        val queryDigits = ContactFilter.normalizePhoneNumber(query)

        return _recentCalls.value.filter { call ->
            ContactFilter.normalizeText(call.name).contains(normalizedQuery) ||
                (queryDigits.isNotEmpty() && ContactFilter.phoneMatches(call.displayNumber, queryDigits))
        }
    }

    override suspend fun removeCalls(ids: List<Int>) = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext

        val messageIds = ids.map { -it.toLong() }
        val conversationIds = context.messagesDB.getByIds(messageIds).map { it.conversationId }.distinct()

        context.removeCallLogEntries(ids)
        context.messagesDB.deleteFromMessages(messageIds)
        conversationIds.forEach { conversationRepository.refreshConversation(it) }
        _recentCalls.value = emptyList()
    }

    override suspend fun removeCallsForConversation(roleId: Long, peerNumber: String) = withContext(ioDispatcher) {
        if (!context.hasPermission(PERMISSION_WRITE_CALL_LOG)) return@withContext

        val targetNumber = ContactFilter.normalizePhoneNumber(peerNumber)
        if (targetNumber.isEmpty()) return@withContext

        val accountIdToSimIDMap = HashMap<String, Int>()
        context.getAvailableSIMCardLabels().forEach { accountIdToSimIDMap[it.handle.id] = it.id }
        val simIdToSubscriptionIdMap = simRepository.getActiveSubscriptions().associate { it.id to it.subscriptionId }
        val roleContext = RoleRuleContext(roleRepository.getRoles())

        val ids = ArrayList<Int>()
        runCatching {
            context.contentResolver.query(
                Calls.CONTENT_URI,
                arrayOf(Calls._ID, Calls.NUMBER, Calls.PHONE_ACCOUNT_ID),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getStringValueOrNull(Calls.NUMBER).orEmpty()
                    if (number.isEmpty() || number == "-1") continue

                    val accountId = cursor.getStringValueOrNull(Calls.PHONE_ACCOUNT_ID)
                    val simID = accountId?.let { accountIdToSimIDMap[it] } ?: -1
                    val resolution = RuleEngines.engine.evaluateIncoming(
                        IncomingEvent(IncomingKind.CALL_LOG, number, simIdToSubscriptionIdMap[simID]),
                        roleContext,
                    ).resolution

                    val displayNumber = resolution?.peerNumber ?: number
                    val role = resolution?.role
                    if ((role?.id ?: 0L) == roleId && ContactFilter.normalizePhoneNumber(displayNumber) == targetNumber) {
                        ids.add(cursor.getIntValue(Calls._ID))
                    }
                }
            }
        }

        if (ids.isNotEmpty()) {
            removeCalls(ids)
            val removed = ids.toHashSet()
            _recentCalls.value = _recentCalls.value.filterNot { it.id in removed }
        }
    }

    override suspend fun removeAllCalls() = withContext(ioDispatcher) {
        if (context.hasPermission(PERMISSION_WRITE_CALL_LOG)) {
            context.contentResolver.delete(Calls.CONTENT_URI, null, null)
        }
        context.messagesDB.deleteAllCalls()
        _recentCalls.value = emptyList()
    }

    override suspend fun restoreCalls(calls: List<RecentCall>) = withContext(ioDispatcher) {
        if (calls.isEmpty()) return@withContext
        if (!context.hasPermission(PERMISSION_WRITE_CALL_LOG)) return@withContext

        val values = calls
            .sortedBy { it.startTS }
            .map {
                ContentValues().apply {
                    put(Calls.NUMBER, it.phoneNumber)
                    put(Calls.TYPE, it.type)
                    put(Calls.DATE, it.startTS.toLong() * 1000L)
                    put(Calls.DURATION, it.duration)
                    put(Calls.CACHED_NAME, it.name)
                }
            }.toTypedArray()

        context.contentResolver.bulkInsert(Calls.CONTENT_URI, values)
    }

    override suspend fun getCallsForExport(maxSize: Int): List<RecentCall> = withContext(ioDispatcher) {
        CallLogGrouper.group(readFilteredCalls(maxSize)).map { it.toRecentCall() }
    }

    private suspend fun readFilteredCalls(maxSize: Int): List<CallRecord> {
        if (!context.hasPermission(PERMISSION_READ_CALL_LOG)) return emptyList()

        if (contactRepository.getCachedContacts().isEmpty()) {
            contactRepository.loadContacts()
        }
        val rawCalls = queryCallLog(maxSize)
        val blockedNumbers = context.getBlockedNumbers()

        return rawCalls.filter { !context.isNumberBlocked(it.phoneNumber, blockedNumbers) }
    }

    @SuppressLint("NewApi")
    private fun queryCallLog(maxSize: Int): List<CallRecord> {
        val result = mutableListOf<CallRecord>()
        var previousStartTS = 0

        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.CACHED_NAME,
            Calls.CACHED_PHOTO_URI,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.PHONE_ACCOUNT_ID
        )

        val accountIdToSimIDMap = HashMap<String, Int>()
        context.getAvailableSIMCardLabels().forEach {
            accountIdToSimIDMap[it.handle.id] = it.id
        }
        val simIdToSubscriptionIdMap = simRepository.getActiveSubscriptions().associate { it.id to it.subscriptionId }
        val roleContext = RoleRuleContext(roleRepository.getRoles())

        val cursor = if (isNougatPlus()) {
            val limitedUri = Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(Calls.LIMIT_PARAM_KEY, maxSize.toString())
                .build()
            context.contentResolver.query(limitedUri, projection, null, null, "${Calls.DATE} DESC")
        } else {
            context.contentResolver.query(Calls.CONTENT_URI, projection, null, null, "${Calls.DATE} DESC LIMIT $maxSize")
        }

        cursor?.use {
            if (!cursor.moveToFirst()) return emptyList()

            do {
                val id = cursor.getIntValue(Calls._ID)
                val number = cursor.getStringValueOrNull(Calls.NUMBER)
                val isUnknownNumber = number == null || number == "-1"
                val phoneNumber = number.orEmpty()
                val accountId = cursor.getStringValueOrNull(Calls.PHONE_ACCOUNT_ID)
                val simID = accountId?.let { accountIdToSimIDMap[it] } ?: -1
                val simSubscriptionId = simIdToSubscriptionIdMap[simID]
                val resolution = RuleEngines.engine.evaluateIncoming(
                    IncomingEvent(IncomingKind.CALL_LOG, phoneNumber, simSubscriptionId),
                    roleContext,
                ).resolution
                val displayNumber = resolution?.peerNumber ?: phoneNumber
                val role = resolution?.role
                val contact = if (isUnknownNumber) null else contactRepository.getContactByNumber(displayNumber)

                var name = cursor.getStringValueOrNull(Calls.CACHED_NAME).orEmpty()
                if (name.isEmpty() || name == "-1") {
                    name = contact?.displayName().orEmpty()
                }
                if (name.isEmpty()) {
                    name = if (isUnknownNumber) context.getString(R.string.unknown) else displayNumber
                }

                var photoUri = cursor.getStringValueOrNull(Calls.CACHED_PHOTO_URI).orEmpty()
                if (photoUri.isEmpty() && contact != null) {
                    photoUri = contact.photoUri
                }

                val startTS = (cursor.getLongValue(Calls.DATE) / 1000L).toInt()
                if (previousStartTS == startTS) continue
                previousStartTS = startTS

                val duration = cursor.getIntValue(Calls.DURATION)
                val type = cursor.getIntValue(Calls.TYPE)

                var specificNumber = ""
                var specificType = ""
                if (contact != null && contact.phoneNumbers.size > 1) {
                    val normalizedNumber = displayNumber.normalizePhoneNumber()
                    val specificPhoneNumber = contact.phoneNumbers.firstOrNull {
                        it.value == displayNumber || it.normalizedNumber == normalizedNumber
                    }
                    if (specificPhoneNumber != null) {
                        specificNumber = specificPhoneNumber.value
                        specificType = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                    }
                }

                result.add(
                    CallRecord(
                        id = id,
                        phoneNumber = phoneNumber,
                        displayNumber = displayNumber,
                        name = name,
                        photoUri = photoUri,
                        startTS = startTS,
                        duration = duration,
                        type = type,
                        simID = simID,
                        role = role,
                        specificNumber = specificNumber,
                        specificType = specificType,
                        isUnknownNumber = isUnknownNumber,
                    )
                )
            } while (cursor.moveToNext() && result.size < maxSize)
        }

        return result
    }

    private fun CallRecord.toRecentCall() = RecentCall(
        id = id,
        phoneNumber = phoneNumber,
        name = name,
        photoUri = photoUri,
        startTS = startTS,
        duration = duration,
        type = type,
        neighbourIDs = neighbourIDs.toMutableList(),
        simID = simID,
        specificNumber = specificNumber,
        specificType = specificType,
        isUnknownNumber = isUnknownNumber,
    )

    private companion object {
        const val CALL_LOG_DEBOUNCE_MS = 300L
    }
}
