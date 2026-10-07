package io.github.kylinlee.chatsim.data.local

import androidx.room.TypeConverter
import io.github.kylinlee.chatsim.domain.model.SimpleContact
import io.github.kylinlee.chatsim.data.model.Attachment
import io.github.kylinlee.chatsim.data.model.MessageAttachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun jsonToAttachmentList(value: String?): ArrayList<Attachment> =
        if (value.isNullOrEmpty()) arrayListOf() else runCatching {
            json.decodeFromString<ArrayList<Attachment>>(value)
        }.getOrDefault(arrayListOf())

    @TypeConverter
    fun attachmentListToJson(list: ArrayList<Attachment>) = json.encodeToString(list)

    @TypeConverter
    fun jsonToSimpleContactList(value: String?): ArrayList<SimpleContact> =
        if (value.isNullOrEmpty()) arrayListOf() else runCatching {
            json.decodeFromString<ArrayList<SimpleContact>>(value)
        }.getOrDefault(arrayListOf())

    @TypeConverter
    fun simpleContactListToJson(list: ArrayList<SimpleContact>) = json.encodeToString(list)

    @TypeConverter
    fun jsonToMessageAttachment(value: String?): MessageAttachment? =
        if (value.isNullOrEmpty()) null else runCatching {
            json.decodeFromString<MessageAttachment>(value)
        }.getOrNull()

    @TypeConverter
    fun messageAttachmentToJson(messageAttachment: MessageAttachment?) = messageAttachment?.let { json.encodeToString(it) }
}
