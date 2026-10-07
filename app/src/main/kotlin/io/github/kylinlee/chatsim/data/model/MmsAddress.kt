package io.github.kylinlee.chatsim.data.model

import android.content.ContentValues
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import kotlinx.serialization.Serializable

@Serializable
data class MmsAddress(
    val address: String,
    val type: Int,
    val charset: Int
) {

    fun toContentValues(): ContentValues {
        // msgId would be added at the point of insertion
        // because it may have changed
        return contentValuesOf(
            Telephony.Mms.Addr.ADDRESS to address,
            Telephony.Mms.Addr.TYPE to type,
            Telephony.Mms.Addr.CHARSET to charset,
        )
    }
}
