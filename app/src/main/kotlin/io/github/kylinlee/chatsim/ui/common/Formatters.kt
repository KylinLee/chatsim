package io.github.kylinlee.chatsim.ui.common

import android.provider.CallLog
import io.github.kylinlee.chatsim.domain.model.contacts.ContactSource
import io.github.kylinlee.chatsim.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val DAY_MILLIS = 24 * 60 * 60 * 1000L

fun contactSourceTitle(source: ContactSource): String =
    source.publicName.ifBlank { source.name }

fun formatTimestamp(seconds: Long): String {
    if (seconds <= 0) return ""

    val date = Date(seconds * 1000)
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

    return when {
        sameDay -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        sameYear -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }
}

fun formatDateTime(seconds: Long): String {
    if (seconds <= 0) return ""

    val date = Date(seconds * 1000)
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

    return when {
        sameDay -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        sameYear -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
    }
}

fun formatMessageTime(seconds: Long): String {
    if (seconds <= 0) return ""

    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(seconds * 1000))
}

fun formatDateHeader(seconds: Long): String {
    if (seconds <= 0) return ""

    return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(seconds * 1000))
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes >= 60) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", minutes / 60, minutes % 60, secs)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }
}

fun callStatusLabel(type: Int, duration: Int): Int = when (type) {
    CallLog.Calls.MISSED_TYPE -> R.string.missed_call

    CallLog.Calls.REJECTED_TYPE -> R.string.rejected_call

    CallLog.Calls.BLOCKED_TYPE -> R.string.blocked_call

    CallLog.Calls.VOICEMAIL_TYPE -> R.string.voicemail_call

    CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> R.string.answered_externally_call

    CallLog.Calls.OUTGOING_TYPE -> if (duration > 0) R.string.outgoing_call else R.string.call_not_connected

    else -> if (duration > 0) R.string.incoming_call else R.string.call_not_connected
}

fun isMissedCallType(type: Int): Boolean = type == CallLog.Calls.MISSED_TYPE ||
    type == CallLog.Calls.REJECTED_TYPE ||
    type == CallLog.Calls.BLOCKED_TYPE
