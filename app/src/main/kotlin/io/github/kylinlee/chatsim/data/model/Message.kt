package io.github.kylinlee.chatsim.data.model

import android.provider.Telephony
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.kylinlee.chatsim.domain.model.SimpleContact

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversation_id"])]
)
data class Message(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "status") val status: Int,
    @ColumnInfo(name = "participants") val participants: ArrayList<SimpleContact>,
    @ColumnInfo(name = "date") val date: Int,
    @ColumnInfo(name = "read") val read: Boolean,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "is_mms") val isMMS: Boolean,
    @ColumnInfo(name = "attachment") val attachment: MessageAttachment?,
    @ColumnInfo(name = "sender_phone_number") val senderPhoneNumber: String,
    @ColumnInfo(name = "sender_name") var senderName: String,
    @ColumnInfo(name = "sender_photo_uri") val senderPhotoUri: String,
    @ColumnInfo(name = "subscription_id") var subscriptionId: Int,
    @ColumnInfo(name = "is_scheduled") var isScheduled: Boolean = false,
    @ColumnInfo(name = "conversation_id") var conversationId: Long = 0,
    @ColumnInfo(name = "is_call", defaultValue = "0") val isCall: Boolean = false,
    @ColumnInfo(name = "call_type", defaultValue = "0") val callType: Int = 0,
    @ColumnInfo(name = "call_duration", defaultValue = "0") val callDuration: Int = 0,
    @ColumnInfo(name = "sim_id", defaultValue = "-1") val simId: Int = -1,
    @ColumnInfo(name = "delivery_status", defaultValue = "-1") val deliveryStatus: Int = Telephony.Sms.STATUS_NONE,
) : ThreadItem() {

    fun isReceivedMessage() = type == Telephony.Sms.MESSAGE_TYPE_INBOX

    fun millis() = date * 1000L

    fun getSender(): SimpleContact? =
        participants.firstOrNull { it.doesHavePhoneNumber(senderPhoneNumber) }
            ?: participants.firstOrNull { it.name == senderName }
            ?: participants.firstOrNull()

    companion object {

        fun getStableId(message: Message): Long {
            var result = message.id.hashCode()
            result = 31 * result + message.body.hashCode()
            result = 31 * result + message.date.hashCode()
            result = 31 * result + message.threadId.hashCode()
            result = 31 * result + message.isMMS.hashCode()
            result = 31 * result + (message.attachment?.hashCode() ?: 0)
            result = 31 * result + message.senderPhoneNumber.hashCode()
            result = 31 * result + message.senderName.hashCode()
            result = 31 * result + message.senderPhotoUri.hashCode()
            result = 31 * result + message.isScheduled.hashCode()
            return result.toLong()
        }

        fun areItemsTheSame(old: Message, new: Message): Boolean {
            return old.id == new.id
        }

        fun areContentsTheSame(old: Message, new: Message): Boolean {
            return old.body == new.body &&
                old.threadId == new.threadId &&
                old.date == new.date &&
                old.isMMS == new.isMMS &&
                old.attachment == new.attachment &&
                old.senderPhoneNumber == new.senderPhoneNumber &&
                old.senderName == new.senderName &&
                old.senderPhotoUri == new.senderPhotoUri &&
                old.isScheduled == new.isScheduled
        }
    }
}
