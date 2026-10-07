package io.github.kylinlee.chatsim.viewmodel

import android.content.Context
import android.telecom.Call
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kylinlee.chatsim.common.extensions.audioManager
import io.github.kylinlee.chatsim.data.legacy.CallManager
import io.github.kylinlee.chatsim.data.legacy.CallManagerListener
import io.github.kylinlee.chatsim.data.legacy.NoCall
import io.github.kylinlee.chatsim.data.legacy.PhoneState
import io.github.kylinlee.chatsim.data.legacy.getCallContact
import io.github.kylinlee.chatsim.data.model.AudioRoute
import io.github.kylinlee.chatsim.domain.rule.IncomingEvent
import io.github.kylinlee.chatsim.domain.rule.IncomingKind
import io.github.kylinlee.chatsim.domain.rule.RoleRuleContext
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import io.github.kylinlee.chatsim.repository.RoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallUiState(
    val phoneState: PhoneState = NoCall,
    val callState: Int? = null,
    val audioRoute: AudioRoute? = null,
    val supportedRoutes: List<AudioRoute> = emptyList(),
    val conferenceCalls: List<Call> = emptyList(),
    val hasPrimaryCall: Boolean = false,
    val callerName: String = "",
    val callerNumber: String = "",
    val callerPhotoUri: String = "",
    val numberLabel: String = "",
    val roleLabel: String = "",
    val isMuted: Boolean = false,
    val durationSeconds: Int = 0,
)

@HiltViewModel
class CallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roleRepository: RoleRepository,
) : ViewModel(), CallManagerListener {
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private var durationJob: Job? = null
    private var isMuted = false

    init {
        CallManager.addListener(this)
        refreshCallerInfo(CallManager.getPrimaryCall())
        updateState()
    }

    override fun onCleared() {
        durationJob?.cancel()
        CallManager.removeListener(this)
        super.onCleared()
    }

    override fun onStateChanged() {
        updateState()
    }

    override fun onAudioStateChanged(audioState: AudioRoute) {
        updateState()
    }

    override fun onPrimaryCallChanged(call: Call) {
        isMuted = false
        context.audioManager.isMicrophoneMute = false
        refreshCallerInfo(call)
        updateState()
    }

    fun accept() {
        CallManager.accept()
    }

    fun reject() {
        CallManager.reject()
    }

    fun disconnect() {
        CallManager.getPrimaryCall()?.disconnect()
    }

    fun toggleHold(): Boolean = CallManager.toggleHold()

    fun swap() {
        CallManager.swap()
    }

    fun merge() {
        CallManager.merge()
    }

    fun keypad(char: Char) {
        CallManager.keypad(char)
    }

    fun setAudioRoute(route: Int) {
        CallManager.setAudioRoute(route)
    }

    fun toggleMute() {
        isMuted = !isMuted
        context.audioManager.isMicrophoneMute = isMuted
        _state.value = _state.value.copy(isMuted = isMuted)
    }

    fun toggleSpeaker() {
        val currentRoute = CallManager.getCallAudioRoute()
        val newRoute = if (currentRoute == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER
        CallManager.setAudioRoute(newRoute.route)
    }

    private fun updateState() {
        val callState = CallManager.getState()
        _state.value = _state.value.copy(
            phoneState = CallManager.getPhoneState(),
            callState = callState,
            audioRoute = CallManager.getCallAudioRoute(),
            supportedRoutes = CallManager.getSupportedAudioRoutes().toList(),
            conferenceCalls = CallManager.getConferenceCalls(),
            hasPrimaryCall = CallManager.getPrimaryCall() != null,
            isMuted = isMuted,
        )

        updateDurationTicker(callState)
    }

    private fun updateDurationTicker(callState: Int?) {
        if (callState == Call.STATE_ACTIVE) {
            if (durationJob?.isActive != true) {
                durationJob = viewModelScope.launch {
                    while (isActive) {
                        delay(1000L)
                        _state.value = _state.value.copy(durationSeconds = _state.value.durationSeconds + 1)
                    }
                }
            }
        } else {
            durationJob?.cancel()
            durationJob = null

            if (callState == null || callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) {
                _state.value = _state.value.copy(durationSeconds = 0)
            }
        }
    }

    private fun refreshCallerInfo(call: Call?) {
        getCallContact(context, call) { callContact ->
            _state.value = _state.value.copy(
                callerName = callContact.name,
                callerNumber = callContact.number,
                callerPhotoUri = callContact.photoUri,
                numberLabel = callContact.numberLabel,
                roleLabel = resolveRoleLabel(callContact.number),
            )
        }
    }

    private fun resolveRoleLabel(number: String): String {
        if (number.isBlank()) return ""

        val roles = roleRepository.getRoles()
        val activeRole = roleRepository.getActiveRole()
        val subscriptionId = CallManager.getPrimaryCall()?.details?.accountHandle?.id?.toIntOrNull()
        val role = RuleEngines.engine.evaluateIncoming(
            IncomingEvent(IncomingKind.CALL_LOG, number, subscriptionId),
            RoleRuleContext(roles, activeRoleId = activeRole?.id, fallbackRoleId = activeRole?.id),
        ).resolution?.role ?: return ""

        return role.label.ifBlank { role.displayPrefix }
    }

    private fun readState(): CallUiState {
        return CallUiState(
            phoneState = CallManager.getPhoneState(),
            callState = CallManager.getState(),
            audioRoute = CallManager.getCallAudioRoute(),
            supportedRoutes = CallManager.getSupportedAudioRoutes().toList(),
            conferenceCalls = CallManager.getConferenceCalls(),
            hasPrimaryCall = CallManager.getPrimaryCall() != null,
            isMuted = isMuted,
        )
    }
}
