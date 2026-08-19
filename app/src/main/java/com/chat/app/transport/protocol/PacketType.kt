package com.chat.app.transport.protocol

enum class PacketType {
    TEXT,
    DELIVERY_ACK,
    READ_RECEIPT,
    TYPING,
    PRESENCE_PING,
    PRESENCE_PONG,
    MEDIA_CHUNK;

    companion object {
        fun fromString(value: String): PacketType? {
            return try {
                valueOf(value)
            } catch (_: Exception) {
                null
            }
        }
    }
}
