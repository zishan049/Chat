package com.chat.app.transport.relay

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TopicHasher {

    // Domain separation key to ensure topic hashes are unique to this app version and not raw user IDs
    private const val HMAC_KEY = "ChatApp_v2_Relay_Routing_Secret_2026"
    private const val PREFIX = "p2p_chat_"

    /**
     * Hashes a user/recipient UUID into an opaque, sanitized topic name for public SSE relays.
     * Prevents observers from discovering user IDs or contact mappings.
     */
    fun hashTopic(userId: String): String {
        val clean = userId.trim()
        if (clean.isBlank()) return "${PREFIX}global"

        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(HMAC_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(clean.toByteArray(Charsets.UTF_8))
            // Take first 16 bytes (32 hex characters) for a compact topic name
            val hexString = hmacBytes.take(16).joinToString("") { "%02x".format(it) }
            "$PREFIX$hexString"
        } catch (_: Exception) {
            // Fallback to SHA-256
            val digest = MessageDigest.getInstance("SHA-256").digest(clean.toByteArray(Charsets.UTF_8))
            val hex = digest.take(16).joinToString("") { "%02x".format(it) }
            "$PREFIX$hex"
        }
    }
}
