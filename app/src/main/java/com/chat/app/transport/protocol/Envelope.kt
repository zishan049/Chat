package com.chat.app.transport.protocol

import java.util.UUID

/**
 * Versioned, authenticated packet envelope for all network transports.
 */
data class Envelope(
    val protocolVersion: Int = 1,
    val envelopeId: String = UUID.randomUUID().toString(),
    val messageId: String,
    val senderId: String,
    val recipientId: String,
    val type: PacketType,
    val timestamp: Long = System.currentTimeMillis(),
    val ciphertextBase64: String = "",
    val senderPublicKey: String? = null,
    val signature: String? = null
)
