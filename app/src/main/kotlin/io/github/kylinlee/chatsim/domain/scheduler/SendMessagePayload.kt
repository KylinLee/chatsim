package io.github.kylinlee.chatsim.domain.scheduler

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SendMessagePayload(val conversationId: Long)

object SendMessagePayloadCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: SendMessagePayload): String = json.encodeToString(payload)

    fun decode(raw: String): SendMessagePayload? =
        runCatching { json.decodeFromString<SendMessagePayload>(raw) }.getOrNull()
}
