package io.github.kylinlee.chatsim.data.model

import android.content.ContentValues
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import kotlinx.serialization.Serializable

@Serializable
data class MmsPart(
    val contentDisposition: String?,
    val charset: String?,
    val contentId: String?,
    val contentLocation: String?,
    val contentType: String,
    val ctStart: String?,
    val ctType: String?,
    val filename: String?,
    val name: String?,
    val sequenceOrder: Int,
    val text: String?,
    val data: String?,
) {

    fun toContentValues(): ContentValues {
        return contentValuesOf(
            Telephony.Mms.Part.CONTENT_DISPOSITION to contentDisposition,
            Telephony.Mms.Part.CHARSET to charset,
            Telephony.Mms.Part.CONTENT_ID to contentId,
            Telephony.Mms.Part.CONTENT_LOCATION to contentLocation,
            Telephony.Mms.Part.CONTENT_TYPE to contentType,
            Telephony.Mms.Part.CT_START to ctStart,
            Telephony.Mms.Part.CT_TYPE to ctType,
            Telephony.Mms.Part.FILENAME to filename,
            Telephony.Mms.Part.NAME to name,
            Telephony.Mms.Part.SEQ to sequenceOrder,
            Telephony.Mms.Part.TEXT to text,
        )
    }

    fun isNonText(): Boolean {
        return !(text != null || contentType.lowercase().startsWith("text") || contentType.lowercase() == "application/smil")
    }
}
