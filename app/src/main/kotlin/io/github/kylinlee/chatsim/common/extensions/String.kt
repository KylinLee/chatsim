package io.github.kylinlee.chatsim.common.extensions

import android.telephony.PhoneNumberUtils
import java.text.Normalizer
import java.util.Locale

// ===== Migrated from Simple-Commons =====

private val normalizeRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun String.normalizePhoneNumber() = PhoneNumberUtils.normalizeNumber(this)

fun String.areDigitsOnly() = matches(Regex("[0-9]+"))

fun String.areLettersOnly() = matches(Regex("[a-zA-Z]+"))

fun String.getNameLetter() = normalizeString().toCharArray().getOrNull(0)?.toString()?.uppercase(Locale.getDefault()) ?: "A"

fun String.normalizeString() = Normalizer.normalize(this, Normalizer.Form.NFD).replace(normalizeRegex, "")

fun String.isPhoneNumber(): Boolean {
    return this.matches("^[0-9+\\-\\)\\( *#]+\$".toRegex())
}

fun String.trimToComparableNumber(): String {
    if (!this.isPhoneNumber()) {
        return this
    }
    val normalizedNumber = this.normalizeString()
    val startIndex = Math.max(0, normalizedNumber.length - 9)
    return normalizedNumber.substring(startIndex)
}

fun String.isBlockedNumberPattern() = contains("*")

fun String.getExtensionFromMimeType(): String {
    return when (lowercase()) {
        "image/png" -> ".png"
        "image/apng" -> ".apng"
        "image/webp" -> ".webp"
        "image/svg+xml" -> ".svg"
        "image/gif" -> ".gif"
        else -> ".jpg"
    }
}

fun String.isImageMimeType(): Boolean {
    return lowercase().startsWith("image")
}

fun String.isGifMimeType(): Boolean {
    return lowercase().endsWith("gif")
}

fun String.isVideoMimeType(): Boolean {
    return lowercase().startsWith("video")
}

fun String.isVCardMimeType(): Boolean {
    val lowercase = lowercase()
    return lowercase.endsWith("x-vcard") || lowercase.endsWith("vcard")
}

fun String.isAudioMimeType(): Boolean {
    return lowercase().startsWith("audio")
}

fun String.isCalendarMimeType(): Boolean {
    return lowercase().endsWith("calendar")
}

fun String.isPdfMimeType(): Boolean {
    return lowercase().endsWith("pdf")
}

fun String.isZipMimeType(): Boolean {
    return lowercase().endsWith("zip")
}

fun String.isPlainTextMimeType(): Boolean {
    return lowercase() == "text/plain"
}
