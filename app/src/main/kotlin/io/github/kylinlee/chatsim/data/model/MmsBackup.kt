package io.github.kylinlee.chatsim.data.model

import android.content.ContentValues
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import kotlinx.serialization.Serializable

@Serializable
data class MmsBackup(
    val creator: String?,
    val contentType: String?,
    val deliveryReport: Int,
    val date: Long,
    val dateSent: Long,
    val locked: Int,
    val messageType: Int,
    val messageBox: Int,
    val read: Int,
    val readReport: Int,
    val seen: Int,
    val textOnly: Int,
    val status: String?,
    val subject: String?,
    val subjectCharSet: String?,
    val subscriptionId: Long,
    val transactionId: String?,
    val addresses: List<MmsAddress>,
    val parts: List<MmsPart>,

    override val backupType: BackupType = BackupType.MMS,
): MessagesBackup() {

    fun toContentValues(): ContentValues {
        return contentValuesOf(
            Telephony.Mms.TRANSACTION_ID to transactionId,
            Telephony.Mms.SUBSCRIPTION_ID to subscriptionId,
            Telephony.Mms.SUBJECT to subject,
            Telephony.Mms.DATE to date,
            Telephony.Mms.DATE_SENT to dateSent,
            Telephony.Mms.LOCKED to locked,
            Telephony.Mms.READ to read,
            Telephony.Mms.STATUS to status,
            Telephony.Mms.SUBJECT_CHARSET to subjectCharSet,
            Telephony.Mms.SEEN to seen,
            Telephony.Mms.MESSAGE_TYPE to messageType,
            Telephony.Mms.MESSAGE_BOX to messageBox,
            Telephony.Mms.DELIVERY_REPORT to deliveryReport,
            Telephony.Mms.READ_REPORT to readReport,
            Telephony.Mms.CONTENT_TYPE to contentType,
            Telephony.Mms.TEXT_ONLY to textOnly,
        )
    }
}
