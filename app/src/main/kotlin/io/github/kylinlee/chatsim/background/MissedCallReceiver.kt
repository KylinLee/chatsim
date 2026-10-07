package io.github.kylinlee.chatsim.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import io.github.kylinlee.chatsim.common.extensions.notificationHelper
import io.github.kylinlee.chatsim.data.rules.IncomingRuleExecutor
import io.github.kylinlee.chatsim.domain.rule.IncomingKind

class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
        val blocked = IncomingRuleExecutor.isFullyBlocked(context, IncomingKind.CALL, number.orEmpty())
        if (count <= 0 || blocked) {
            context.notificationHelper.cancelMissedCallNotification()
        } else {
            context.notificationHelper.showMissedCallNotification(number)
        }
    }
}
