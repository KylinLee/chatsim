package io.github.kylinlee.chatsim.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kylinlee.chatsim.common.STOP_KEEP_ALIVE
import io.github.kylinlee.chatsim.common.extensions.config

class KeepAliveActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != STOP_KEEP_ALIVE) return
        context.config.keepAliveEnabled = false
        KeepAliveService.sync(context, false)
    }
}
