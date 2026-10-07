package io.github.kylinlee.chatsim.viewmodel

import android.telecom.Call
import androidx.lifecycle.ViewModel
import io.github.kylinlee.chatsim.data.legacy.CallManager
import io.github.kylinlee.chatsim.data.legacy.CallManagerListener
import io.github.kylinlee.chatsim.data.model.AudioRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ConferenceViewModel @Inject constructor() : ViewModel(), CallManagerListener {
    private val _calls = MutableStateFlow(CallManager.getConferenceCalls())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    init {
        CallManager.addListener(this)
        _calls.value = CallManager.getConferenceCalls()
    }

    override fun onCleared() {
        CallManager.removeListener(this)
        super.onCleared()
    }

    override fun onStateChanged() {
        _calls.value = CallManager.getConferenceCalls()
    }

    override fun onAudioStateChanged(audioState: AudioRoute) {
    }

    override fun onPrimaryCallChanged(call: Call) {
        _calls.value = CallManager.getConferenceCalls()
    }

    fun merge() {
        CallManager.merge()
    }

    fun swap() {
        CallManager.swap()
    }

    fun toggleHold(): Boolean = CallManager.toggleHold()

    fun disconnect(call: Call) {
        call.disconnect()
    }
}
