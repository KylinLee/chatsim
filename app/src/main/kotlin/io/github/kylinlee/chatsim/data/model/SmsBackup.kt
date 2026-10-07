package io.github.kylinlee.chatsim.data.model


import android.content.ContentValues
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import kotlinx.serialization.Serializable

@Serializable
data class SmsBackup(
    val subscriptionId: Long,
    val address: String,
    val body: String?,
    val date: Long,
    val dateSent: Long,
    val locked: Int,
    val protocol: String?,
    val read: Int,
    val status: Int,
    val type: Int,
    val serviceCenter: String?,

    override val backupType: BackupType = BackupType.SMS,
    ): MessagesBackup() {

    fun toContentValues(): ContentValues {
        return contentValuesOf(
            Telephony.Sms.SUBSCRIPTION_ID to subscriptionId,
            Telephony.Sms.ADDRESS to address,
            Telephony.Sms.BODY to body,
            Telephony.Sms.DATE to date,
            Telephony.Sms.DATE_SENT to dateSent,
            Telephony.Sms.LOCKED to locked,
            Telephony.Sms.PROTOCOL to protocol,
            Telephony.Sms.READ to read,
            Telephony.Sms.STATUS to status,
            Telephony.Sms.TYPE to type,
            Telephony.Sms.SERVICE_CENTER to serviceCenter,
        )
    }
}
