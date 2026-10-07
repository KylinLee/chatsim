package io.github.kylinlee.chatsim.data.messaging

import android.content.Context
import android.widget.Toast.LENGTH_LONG
import com.klinker.android.send_message.Settings
import io.github.kylinlee.chatsim.common.extensions.showErrorToast
import io.github.kylinlee.chatsim.common.extensions.toast
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.extensions.config
import io.github.kylinlee.chatsim.common.extensions.messagingUtils
import io.github.kylinlee.chatsim.common.extensions.removeDiacriticsIfNeeded
import io.github.kylinlee.chatsim.data.messaging.SmsException.Companion.EMPTY_DESTINATION_ADDRESS
import io.github.kylinlee.chatsim.data.messaging.SmsException.Companion.ERROR_PERSISTING_MESSAGE
import io.github.kylinlee.chatsim.data.messaging.SmsException.Companion.ERROR_SENDING_MESSAGE

@Deprecated("TODO: Move/rewrite messaging config code into the app.")
fun Context.getSendMessageSettings(): Settings {
    val settings = Settings()
    settings.useSystemSending = true
    settings.deliveryReports = config.enableDeliveryReports
    settings.group = false
    return settings
}

/** Sends the message using the in-app SmsManager API wrappers. */
fun Context.sendMessageCompat(
    text: String,
    addresses: List<String>,
    subId: Int?,
    messageId: Long? = null,
    threadAddresses: List<String> = addresses,
) {
    try {
        sendMessageCompatOrThrow(text, addresses, subId, messageId, threadAddresses)
    } catch (e: SmsException) {
        when (e.errorCode) {
            EMPTY_DESTINATION_ADDRESS -> toast(id = R.string.empty_destination_address, length = LENGTH_LONG)
            ERROR_PERSISTING_MESSAGE -> toast(id = R.string.unable_to_save_message, length = LENGTH_LONG)
            ERROR_SENDING_MESSAGE -> toast(
                msg = getString(R.string.unknown_error_occurred_sending_message, e.errorCode),
                length = LENGTH_LONG
            )
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

/** Same as [sendMessageCompat] but propagates failures to the caller (e.g. the task scheduler). */
fun Context.sendMessageCompatOrThrow(
    text: String,
    addresses: List<String>,
    subId: Int?,
    messageId: Long? = null,
    threadAddresses: List<String> = addresses,
    deleteSystemMessageOnFailure: Boolean = false,
) {
    val settings = getSendMessageSettings()
    if (subId != null) {
        settings.subscriptionId = subId
    }

    val messageText = removeDiacriticsIfNeeded(text)
    messagingUtils.sendSmsMessage(
        text = messageText,
        addresses = addresses.toSet(),
        subId = settings.subscriptionId,
        requireDeliveryReport = settings.deliveryReports,
        messageId = messageId,
        threadAddresses = threadAddresses,
        deleteSystemMessageOnFailure = deleteSystemMessageOnFailure,
    )
}

/**
 * Check if a given "address" is a short code.
 * There's not much info available on these special numbers, even the wikipedia page (https://en.wikipedia.org/wiki/Short_code)
 * contains outdated information regarding max number of digits. The exact parameters for short codes can vary by country and by carrier.
 *
 * This function simply returns true if the [address] contains at least one letter.
 */
fun isShortCodeWithLetters(address: String): Boolean {
    return address.any { it.isLetter() }
}
