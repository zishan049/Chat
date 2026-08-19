package com.chat.app.transport.protocol

import org.json.JSONObject

object EnvelopeSerializer {

    fun toJson(envelope: Envelope): String {
        return JSONObject().apply {
            put("v", envelope.protocolVersion)
            put("eid", envelope.envelopeId)
            put("mid", envelope.messageId)
            put("from", envelope.senderId)
            put("to", envelope.recipientId)
            put("type", envelope.type.name)
            put("ts", envelope.timestamp)
            put("payload", envelope.ciphertextBase64)
            if (!envelope.senderPublicKey.isNullOrBlank()) put("pk", envelope.senderPublicKey)
            if (!envelope.signature.isNullOrBlank()) put("sig", envelope.signature)
        }.toString()
    }

    fun fromJson(jsonStr: String): Envelope? {
        val trimmed = jsonStr.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("{")) return null
        return try {
            val json = JSONObject(trimmed)
            val version = json.optInt("v", 1)
            val typeStr = json.optString("type")
            val type = PacketType.fromString(typeStr) ?: return null

            Envelope(
                protocolVersion = version,
                envelopeId = json.optString("eid", java.util.UUID.randomUUID().toString()),
                messageId = json.getString("mid"),
                senderId = json.getString("from"),
                recipientId = json.getString("to"),
                type = type,
                timestamp = json.optLong("ts", System.currentTimeMillis()),
                ciphertextBase64 = json.optString("payload", ""),
                senderPublicKey = json.optString("pk").takeIf { it.isNotBlank() },
                signature = json.optString("sig").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            null
        }
    }
}
