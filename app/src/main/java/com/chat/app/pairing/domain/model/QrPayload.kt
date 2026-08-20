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
                val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
                val name = json.optString("name").takeIf { it.isNotBlank() }
                    ?: json.optString("displayName").takeIf { it.isNotBlank() }
                    ?: "Peer"
                val pk = json.optString("pk").takeIf { it.isNotBlank() }
                    ?: json.optString("publicKeyBase64").takeIf { it.isNotBlank() }
                    ?: return null
                val fp = json.optString("fp").takeIf { it.isNotBlank() }
                    ?: json.optString("fingerprint").takeIf { it.isNotBlank() }
                    ?: ""
                val ip = json.optString("ip").takeIf { it.isNotBlank() }
                    ?: json.optString("lanIp").takeIf { it.isNotBlank() }
                val port = json.optInt("port", 47832)
                val ts = json.optLong("ts", json.optLong("timestamp", System.currentTimeMillis()))
                val sig = json.optString("sig", json.optString("signature", ""))

                QrPayload(
                    version = json.optInt("v", json.optInt("version", 1)),
                    id = id,
                    displayName = name,
                    publicKeyBase64 = pk,
                    fingerprint = fp,
                    lanIp = ip,
                    port = port,
                    timestamp = ts,
                    signature = sig
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
