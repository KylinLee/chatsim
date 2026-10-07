package io.github.kylinlee.chatsim.background


import io.github.kylinlee.chatsim.common.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kylinlee.chatsim.common.ACCEPT_CALL
import io.github.kylinlee.chatsim.data.legacy.CallManager
import io.github.kylinlee.chatsim.common.DECLINE_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> CallManager.accept()
            DECLINE_CALL -> CallManager.reject()
        }
    }
}
