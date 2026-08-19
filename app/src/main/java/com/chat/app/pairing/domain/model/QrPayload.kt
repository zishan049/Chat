package com.chat.app.pairing.domain.model

import org.json.JSONObject

/**
 * Data payload encoded into and decoded from QR codes during peer pairing.
 */
data class QrPayload(
    val version: Int = 1,
    val id: String,
    val displayName: String,
    val publicKeyBase64: String,
    val fingerprint: String,
    val lanIp: String? = null,
    val port: Int = 47832,
    val timestamp: Long = System.currentTimeMillis(),
    val signature: String = ""
) {
    /**
     * Produces the canonical data string that is signed by the identity private key.
     */
    fun toCanonicalString(): String {
        return "$version|$id|$displayName|$publicKeyBase64|$fingerprint|${lanIp ?: ""}|$port|$timestamp"
    }

    /**
     * Serializes to a compact JSON string suitable for QR encoding.
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("v", version)
            put("id", id)
            put("name", displayName)
            put("pk", publicKeyBase64)
            put("fp", fingerprint)
            if (!lanIp.isNullOrBlank()) put("ip", lanIp)
            put("port", port)
            put("ts", timestamp)
            put("sig", signature)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): QrPayload? {
            return try {
                val json = JSONObject(jsonStr)
                QrPayload(
                    version = json.optInt("v", 1),
                    id = json.getString("id"),
                    displayName = json.getString("name"),
                    publicKeyBase64 = json.getString("pk"),
                    fingerprint = json.getString("fp"),
                    lanIp = json.optString("ip").takeIf { it.isNotBlank() },
                    port = json.optInt("port", 47832),
                    timestamp = json.optLong("ts", System.currentTimeMillis()),
                    signature = json.optString("sig", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
