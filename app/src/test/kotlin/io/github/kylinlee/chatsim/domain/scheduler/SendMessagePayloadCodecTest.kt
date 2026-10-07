package io.github.kylinlee.chatsim.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendMessagePayloadCodecTest {
    @Test
    fun roundTripKeepsConversationId() {
        val payload = SendMessagePayload(conversationId = 42)

        assertEquals(payload, SendMessagePayloadCodec.decode(SendMessagePayloadCodec.encode(payload)))
    }

    @Test
    fun malformedPayloadDecodesToNull() {
        assertNull(SendMessagePayloadCodec.decode("not json"))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        assertEquals(
            SendMessagePayload(conversationId = 7),
            SendMessagePayloadCodec.decode("""{"conversationId":7,"extra":"x"}"""),
        )
    }
}
