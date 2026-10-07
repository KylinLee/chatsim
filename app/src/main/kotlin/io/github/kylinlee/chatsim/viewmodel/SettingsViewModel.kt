package io.github.kylinlee.chatsim.viewmodel

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.data.events.AppEventBus
import io.github.kylinlee.chatsim.domain.model.AppEvent
import io.github.kylinlee.chatsim.data.legacy.MessagesReader
import io.github.kylinlee.chatsim.data.legacy.MessagesWriter
import io.github.kylinlee.chatsim.data.model.BackupType
import io.github.kylinlee.chatsim.data.model.ImportResult
import io.github.kylinlee.chatsim.data.model.MessagesBackup
import io.github.kylinlee.chatsim.data.model.MmsBackup
import io.github.kylinlee.chatsim.data.model.SmsBackup
import io.github.kylinlee.chatsim.domain.ContactNames
import io.github.kylinlee.chatsim.domain.scheduler.DEFAULT_RECYCLE_BIN_CLEAN_PERIOD_INDEX
import io.github.kylinlee.chatsim.domain.scheduler.RECYCLE_BIN_CLEAN_PERIODS
import io.github.kylinlee.chatsim.repository.CallLogRepository
import io.github.kylinlee.chatsim.repository.MessageRepository
import io.github.kylinlee.chatsim.repository.ScheduledTaskRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import io.github.kylinlee.chatsim.repository.UserRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.coroutines.resume

data class SettingsState(
    val showTabs: Int = 0,
    val startNameWithSurname: Boolean = true,
    val nameSeparator: String = "",
    val swipeToAnswer: Boolean = false,
    val dialpadMuted: Boolean = false,
    val hideDialpadNumbers: Boolean = false,
    val callOverlay: Boolean = true,
    val useSimpleCharacters: Boolean = false,
    val enableDeliveryReports: Boolean = false,
    val lockScreenVisibilitySetting: Int = 0,
    val canDrawOverlays: Boolean = false,
    val useRecycleBin: Boolean = false,
    val recycleBinCleanBaseTime: Long = 0L,
    val recycleBinCleanPeriodIndex: Int = DEFAULT_RECYCLE_BIN_CLEAN_PERIOD_INDEX,
    val isAppPasswordProtectionOn: Boolean = false,
    val recycleBinMessagesCount: Int = 0,
    val trashRuleCount: Int = 0,
    val appVersionName: String = "",
    val appVersionCode: Long = 0L,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val messageRepository: MessageRepository,
    private val callLogRepository: CallLogRepository,
    private val userRuleRepository: UserRuleRepository,
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = readState()
        viewModelScope.launch {
            val count = messageRepository.getRecycleBinCount()
            val trashRules = userRuleRepository.trashRuleCount()
            _state.value = _state.value.copy(recycleBinMessagesCount = count, trashRuleCount = trashRules)
        }
    }

    fun setShowTabs(mask: Int) {
        settings.showTabs = mask
        _state.value = _state.value.copy(showTabs = mask)
    }

    fun setStartNameWithSurname(enabled: Boolean) {
        settings.startNameWithSurname = enabled
        _state.value = _state.value.copy(startNameWithSurname = enabled)
        eventBus.tryEmit(AppEvent.RefreshContacts)
        eventBus.tryEmit(AppEvent.RefreshConversations)
    }

    fun setNameSeparator(separator: String) {
        settings.nameSeparator = separator
        ContactNames.separator = separator
        _state.value = _state.value.copy(nameSeparator = separator)
        eventBus.tryEmit(AppEvent.RefreshContacts)
        eventBus.tryEmit(AppEvent.RefreshConversations)
    }

    fun setSwipeToAnswer(enabled: Boolean) {
        settings.swipeToAnswer = enabled
        _state.value = _state.value.copy(swipeToAnswer = enabled)
    }

    fun setDialpadMuted(enabled: Boolean) {
        settings.dialpadMuted = enabled
        _state.value = _state.value.copy(dialpadMuted = enabled)
    }

    fun setHideDialpadNumbers(enabled: Boolean) {
        settings.hideDialpadNumbers = enabled
        _state.value = _state.value.copy(hideDialpadNumbers = enabled)
    }

    fun setCallOverlay(enabled: Boolean) {
        settings.callOverlay = enabled
        _state.value = _state.value.copy(callOverlay = enabled)
        eventBus.tryEmit(AppEvent.CallOverlayChanged)
    }

    fun setUseSimpleCharacters(enabled: Boolean) {
        settings.useSimpleCharacters = enabled
        _state.value = _state.value.copy(useSimpleCharacters = enabled)
    }

    fun setEnableDeliveryReports(enabled: Boolean) {
        settings.enableDeliveryReports = enabled
        _state.value = _state.value.copy(enableDeliveryReports = enabled)
    }

    fun setLockScreenVisibilitySetting(value: Int) {
        settings.lockScreenVisibilitySetting = value
        _state.value = _state.value.copy(lockScreenVisibilitySetting = value)
    }

    fun setUseRecycleBin(enabled: Boolean) {
        settings.useRecycleBin = enabled
        _state.value = _state.value.copy(useRecycleBin = enabled)
        rescheduleRecycleBinClean()
    }

    fun setRecycleBinCleanBaseTime(millis: Long) {
        settings.recycleBinCleanBaseTime = millis
        _state.value = _state.value.copy(recycleBinCleanBaseTime = millis)
        rescheduleRecycleBinClean()
    }

    fun setRecycleBinCleanPeriodIndex(index: Int) {
        val safeIndex = index.coerceIn(RECYCLE_BIN_CLEAN_PERIODS.indices)
        settings.recycleBinCleanPeriod = RECYCLE_BIN_CLEAN_PERIODS[safeIndex]
        _state.value = _state.value.copy(recycleBinCleanPeriodIndex = safeIndex)
        rescheduleRecycleBinClean()
    }

    private fun rescheduleRecycleBinClean() {
        viewModelScope.launch { scheduledTaskRepository.ensureRecycleBinCleanSchedule() }
    }

    fun setAppPasswordProtection(enabled: Boolean, hash: String, protectionType: Int) {
        settings.isAppPasswordProtectionOn = enabled
        settings.appPasswordHash = if (enabled) hash else ""
        settings.appProtectionType = protectionType
        _state.value = _state.value.copy(isAppPasswordProtectionOn = enabled)
    }

    fun exportMessages(uri: Uri, includeSms: Boolean = true, includeMms: Boolean = true) {
        viewModelScope.launch {
            _isBusy.value = true
            runCatching {
                val messages = readMessagesForExport(includeSms, includeMms)
                val jsonString = Json { encodeDefaults = true }.encodeToString(messages)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
            }
            _isBusy.value = false
        }
    }

    fun importMessages(uri: Uri, importSms: Boolean = true, importMms: Boolean = true) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = runCatching {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                } ?: return@runCatching ImportResult.IMPORT_FAIL

                val messages = Json.decodeFromString<List<MessagesBackup>>(jsonString)
                if (messages.isEmpty()) return@runCatching ImportResult.IMPORT_NOTHING_NEW

                val writer = MessagesWriter(context)
                var imported = 0
                var failed = 0
                messages.forEach { message ->
                    try {
                        when (message.backupType) {
                            BackupType.SMS -> if (importSms) {
                                writer.writeSmsMessage(message as SmsBackup)
                                imported++
                            }

                            BackupType.MMS -> if (importMms) {
                                writer.writeMmsMessage(message as MmsBackup)
                                imported++
                            }
                        }
                    } catch (e: Exception) {
                        failed++
                    }
                }

                when {
                    imported == 0 && failed == 0 -> ImportResult.IMPORT_NOTHING_NEW
                    failed > 0 && imported > 0 -> ImportResult.IMPORT_PARTIAL
                    failed > 0 -> ImportResult.IMPORT_FAIL
                    else -> ImportResult.IMPORT_OK
                }
            }.getOrDefault(ImportResult.IMPORT_FAIL)

            if (result != ImportResult.IMPORT_FAIL) {
                eventBus.tryEmit(AppEvent.RefreshMessages)
            }
            _isBusy.value = false
        }
    }

    fun exportCallHistory(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            runCatching {
                val calls = callLogRepository.getCallsForExport()
                val jsonString = Json.encodeToString(calls)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
            }
            _isBusy.value = false
        }
    }

    fun importCallHistory(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            runCatching {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                } ?: return@runCatching

                val calls = Json.decodeFromString<List<io.github.kylinlee.chatsim.data.model.RecentCall>>(jsonString)
                callLogRepository.restoreCalls(calls)
                callLogRepository.invalidateCache()
                eventBus.tryEmit(AppEvent.RefreshCallLog)
            }
            _isBusy.value = false
        }
    }

    private suspend fun readMessagesForExport(includeSms: Boolean, includeMms: Boolean): List<MessagesBackup> =
        suspendCancellableCoroutine { continuation ->
            MessagesReader(context).getMessagesToExport(includeSms, includeMms) { messages ->
                if (continuation.isActive) {
                    continuation.resume(messages)
                }
            }
        }

    private fun readState() = SettingsState(
        showTabs = settings.showTabs,
        startNameWithSurname = settings.startNameWithSurname,
        nameSeparator = settings.nameSeparator,
        swipeToAnswer = settings.swipeToAnswer,
        dialpadMuted = settings.dialpadMuted,
        hideDialpadNumbers = settings.hideDialpadNumbers,
        callOverlay = settings.callOverlay,
        useSimpleCharacters = settings.useSimpleCharacters,
        enableDeliveryReports = settings.enableDeliveryReports,
        lockScreenVisibilitySetting = settings.lockScreenVisibilitySetting,
        canDrawOverlays = Settings.canDrawOverlays(context),
        useRecycleBin = settings.useRecycleBin,
        recycleBinCleanBaseTime = settings.recycleBinCleanBaseTime,
        recycleBinCleanPeriodIndex = recycleBinCleanPeriodIndex(settings.recycleBinCleanPeriod),
        isAppPasswordProtectionOn = settings.isAppPasswordProtectionOn,
        appVersionName = packageInfo?.versionName.orEmpty(),
        appVersionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L,
    )

    private val packageInfo: PackageInfo?
        get() = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()

    private fun recycleBinCleanPeriodIndex(periodMillis: Long): Int =
        RECYCLE_BIN_CLEAN_PERIODS.indexOf(periodMillis).takeIf { it >= 0 } ?: DEFAULT_RECYCLE_BIN_CLEAN_PERIOD_INDEX
}
