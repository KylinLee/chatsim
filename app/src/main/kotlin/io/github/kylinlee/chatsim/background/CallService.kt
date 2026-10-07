package io.github.kylinlee.chatsim.background


import io.github.kylinlee.chatsim.common.*
import android.app.KeyguardManager
import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import io.github.kylinlee.chatsim.common.extensions.isOutgoing
import io.github.kylinlee.chatsim.common.extensions.powerManager
import io.github.kylinlee.chatsim.data.legacy.CallManager
import io.github.kylinlee.chatsim.data.legacy.CallNotificationManager
import io.github.kylinlee.chatsim.data.legacy.NoCall
import io.github.kylinlee.chatsim.ui.call.CallActivity
import io.github.kylinlee.chatsim.ui.call.CallOverlayController

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                callNotificationManager.setupNotification()
            }
            CallOverlayController.onCallStateChanged()
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallOverlayController.attach(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        callNotificationManager.setupNotification()
        CallOverlayController.onCallStateChanged()

        val isScreenLocked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        val isRinging = call.state == Call.STATE_RINGING
        if (call.isOutgoing() || isRinging || !powerManager.isInteractive || isScreenLocked) {
            startCallActivity()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            if (wasPrimaryCall) {
                startCallActivity()
            }
        }
        CallOverlayController.onCallStateChanged()
    }

    private fun startCallActivity() {
        try {
            startActivity(CallActivity.getStartIntent(this))
        } catch (ignored: Exception) {
            callNotificationManager.setupNotification()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callNotificationManager.cancelNotification()
        CallOverlayController.detach()
    }
}
