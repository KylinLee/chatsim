package io.github.kylinlee.chatsim.background


import io.github.kylinlee.chatsim.common.*
import android.app.Service
import android.content.Intent
import android.net.Uri
import com.klinker.android.send_message.Settings
import io.github.kylinlee.chatsim.data.messaging.sendMessageCompat

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent == null) {
                return START_NOT_STICKY
            }

            val number = Uri.decode(intent.dataString!!.removePrefix("sms:").removePrefix("smsto:").removePrefix("mms").removePrefix("mmsto:").trim())
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrEmpty()) {
                val addresses = listOf(number)
                val subId = Settings.DEFAULT_SUBSCRIPTION_ID
                sendMessageCompat(text, addresses, subId)
            }
        } catch (ignored: Exception) {
        }

        return super.onStartCommand(intent, flags, startId)
    }
}
