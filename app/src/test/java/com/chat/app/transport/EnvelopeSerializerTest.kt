package com.chat.app.transport

import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.protocol.EnvelopeSerializer
import com.chat.app.transport.protocol.PacketType
import org.junit.Assert.*
import org.junit.Test

class EnvelopeSerializerTest {

    @Test
    fun testEnvelopeSerializationRoundTrip() {
        val original = Envelope(
            protocolVersion = 1,
            envelopeId = "env-12345",
            messageId = "msg-9999",
            senderId = "alice-uuid",
            recipientId = "bob-uuid",
            type = PacketType.TEXT,
            timestamp = 1770000000L,
            ciphertextBase64 = "IV_CIPHERTEXT_TAG==",
            senderPublicKey = "MIIB...",
            signature = "SIG=="
        )

        val jsonString = EnvelopeSerializer.toJson(original)
        assertNotNull(jsonString)
        assertTrue(jsonString.startsWith("{"))

        val deserialized = EnvelopeSerializer.fromJson(jsonString)
        assertNotNull("Deserialized envelope must not be null", deserialized)
        assertEquals(original.protocolVersion, deserialized?.protocolVersion)
        assertEquals(original.envelopeId, deserialized?.envelopeId)
        assertEquals(original.messageId, deserialized?.messageId)
        assertEquals(original.senderId, deserialized?.senderId)
        assertEquals(original.recipientId, deserialized?.recipientId)
        assertEquals(original.type, deserialized?.type)
        assertEquals(original.ciphertextBase64, deserialized?.ciphertextBase64)
        assertEquals(original.senderPublicKey, deserialized?.senderPublicKey)
        assertEquals(original.signature, deserialized?.signature)
    }

    @Test
    fun testMalformedJsonHandling() {
        val result = EnvelopeSerializer.fromJson("NOT_VALID_JSON")
        assertNull("Malformed JSON must return null without throwing", result)
    }

    @Test
    fun testUnknownPacketTypeHandling() {
        val json = """{"v":1,"eid":"e1","mid":"m1","from":"a","to":"b","type":"UNKNOWN_CUSTOM_TYPE","payload":"abc"}"""
        val result = EnvelopeSerializer.fromJson(json)
        assertNull("Unknown packet type must return null safely", result)
    }
}
