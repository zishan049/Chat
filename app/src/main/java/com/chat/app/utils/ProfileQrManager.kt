package com.chat.app.utils

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Base64
import com.chat.app.data.Profile
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.NetworkInterface
import java.util.Collections

data class ScannedProfileData(
    val id: String,
    val name: String,
    val age: Int? = null,
    val description: String? = null,
    val avatarUri: String? = null,
    val deviceInfo: String? = null,
    val timestamp: Long? = null,
    val ip: String? = null,
    val port: Int? = null,
    val sessionToken: String? = null,
    val publicKey: String? = null,
    val signature: String? = null,
    val isVerified: Boolean = false,
    val ttl: Long? = null,
    val isExpired: Boolean = false
)

object ProfileQrManager {

    /**
     * Gets sanitized device manufacturer & model for privacy-conscious display.
     */
    fun getDeviceInfo(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val model = Build.MODEL
        val modelName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        return modelName
    }

    /**
     * Attempts to find the device's local Wi-Fi or Hotspot IPv4 address.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4 && !sAddr.startsWith("127.")) {
                            return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Builds a canonical string representation of core profile attributes for cryptographic signing.
     */
    fun buildCanonicalPayloadString(
        id: String,
        name: String,
        publicKey: String,
        timestamp: Long,
        ip: String? = null,
        port: Int? = null
    ): String {
        val cleanIp = ip?.trim() ?: ""
        val cleanPort = port ?: 0
        return "id=$id|name=$name|pk=$publicKey|ts=$timestamp|ip=$cleanIp|port=$cleanPort"
    }

    /**
     * Resizes and encodes the local avatar image to a compact Base64 thumbnail string for QR / P2P exchange.
     */
    fun encodeAvatarToBase64(context: Context, avatarUri: String?): String? {
        if (avatarUri.isNullOrBlank()) return null
        return try {
            val file = if (avatarUri.startsWith("/")) {
                File(avatarUri)
            } else {
                File(context.filesDir, avatarUri)
            }
            val bitmap = if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else if (avatarUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(avatarUri))?.use {
                    BitmapFactory.decodeStream(it)
                }
            } else null

            if (bitmap != null) {
                val maxDim = 96
                val scale = minOf(1.0f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
                val scaledBitmap = if (scale < 1.0f) {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else bitmap

                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 65, stream)
                val bytes = stream.toByteArray()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes Base64 avatar bytes received from a peer and saves it locally to the app's internal avatars directory.
     */
    fun saveBase64Avatar(context: Context, peerId: String, base64Str: String?): String? {
        if (base64Str.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val avatarDir = File(context.filesDir, "avatars")
            if (!avatarDir.exists()) avatarDir.mkdirs()
            val file = File(avatarDir, "avatar_${peerId}.jpg")
            FileOutputStream(file).use { out ->
                out.write(bytes)
                out.flush()
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Builds a cryptographically signed V4 QR payload string containing user profile,
     * EC public key, timestamp, TTL, ECDSA digital signature, and optional endpoint.
     */
    fun buildProfileQrPayload(
        profile: Profile?,
        timestamp: Long,
        ip: String? = null,
        port: Int? = null,
        sessionToken: String? = null,
        ttl: Long? = 300_000L // 5 minutes TTL for dynamic endpoint
    ): String {
        if (profile == null) return ""
        val publicKey = CryptoUtils.getSelfPublicKeyBase64()
        val canonical = buildCanonicalPayloadString(
            id = profile.id,
            name = profile.username,
            publicKey = publicKey,
            timestamp = timestamp,
            ip = ip,
            port = port
        )
        val signature = CryptoUtils.signPayload(canonical)

        val json = JSONObject().apply {
            put("type", "CHATCONTACT")
            put("v", 4)
            put("id", profile.id)
            put("name", profile.username)
            put("pk", publicKey)
            put("sig", signature)
            put("ts", timestamp)
            if (profile.age != null && profile.age > 0) put("age", profile.age)
            if (!profile.description.isNullOrBlank()) put("status", profile.description)
            put("dev", getDeviceInfo())
            if (!ip.isNullOrBlank()) put("ip", ip)
            if (port != null && port > 0) put("port", port)
            if (ttl != null && ttl > 0) put("ttl", ttl)
            if (!sessionToken.isNullOrBlank()) put("token", sessionToken)
        }

        // Encode as compact URL-safe Base64 string for reduced QR density and clean link sharing
        val jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
        val base64Url = Base64.encodeToString(jsonBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "CHATCONTACT_V4:$base64Url"
    }

    /**
     * Generates a deep link URL for sharing via external messaging or clipboard.
     */
    fun buildShareableContactUrl(
        profile: Profile?,
        timestamp: Long = System.currentTimeMillis(),
        ip: String? = null,
        port: Int? = null
    ): String {
        val payload = buildProfileQrPayload(profile, timestamp, ip, port)
        val rawData = payload.removePrefix("CHATCONTACT_V4:")
        return "chat://contact?v=4&d=$rawData"
    }

    /**
     * Parses scanned string from QR code or clipboard.
     * Supports CHATCONTACT_V4 (URL-safe signed Base64), deep-links, CHATCONTACT_V3, CHATCONTACT_V2, raw JSON, and legacy format.
     */
    fun parseProfileQrPayload(raw: String, context: Context? = null): ScannedProfileData? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return try {
            when {
                trimmed.startsWith("CHATCONTACT_V4:") -> {
                    val base64Data = trimmed.removePrefix("CHATCONTACT_V4:")
                    val jsonBytes = Base64.decode(base64Data, Base64.URL_SAFE or Base64.DEFAULT)
                    val jsonStr = String(jsonBytes, Charsets.UTF_8)
                    parseJsonProfile(JSONObject(jsonStr), context)
                }
                trimmed.startsWith("chat://contact") -> {
                    val uri = android.net.Uri.parse(trimmed)
                    val dataParam = uri.getQueryParameter("d") ?: uri.getQueryParameter("data") ?: uri.getQueryParameter("payload")
                    if (!dataParam.isNullOrBlank()) {
                        val jsonBytes = Base64.decode(dataParam, Base64.URL_SAFE or Base64.DEFAULT)
                        val jsonStr = String(jsonBytes, Charsets.UTF_8)
                        parseJsonProfile(JSONObject(jsonStr), context)
                    } else null
                }
                trimmed.startsWith("CHATCONTACT_V3:") -> {
                    val jsonStr = trimmed.removePrefix("CHATCONTACT_V3:")
                    parseJsonProfile(JSONObject(jsonStr), context)
                }
                trimmed.startsWith("CHATCONTACT_V2:") -> {
                    val jsonStr = trimmed.removePrefix("CHATCONTACT_V2:")
                    parseJsonProfile(JSONObject(jsonStr), context)
                }
                trimmed.startsWith("{") && trimmed.endsWith("}") -> {
                    val json = JSONObject(trimmed)
                    if (json.optString("type") == "CHATCONTACT" || json.has("id")) {
                        parseJsonProfile(json, context)
                    } else null
                }
                trimmed.startsWith("CHATCONTACT:") -> {
                    val parts = trimmed.removePrefix("CHATCONTACT:").split(",")
                    if (parts.size >= 2) {
                        ScannedProfileData(
                            id = parts[0].trim(),
                            name = parts[1].trim(),
                            age = parts.getOrNull(2)?.trim()?.toIntOrNull()?.takeIf { it > 0 },
                            description = parts.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() },
                            avatarUri = null,
                            deviceInfo = null,
                            timestamp = null,
                            isVerified = false
                        )
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseJsonProfile(json: JSONObject, context: Context? = null): ScannedProfileData {
        val id = json.optString("id")
        val name = json.optString("name")
        val age = if (json.has("age") && json.getInt("age") > 0) json.getInt("age") else null
        val description = json.optString("status").takeIf { !it.isNullOrBlank() }
        val avatarB64 = json.optString("avatarB64").takeIf { !it.isNullOrBlank() }
        val savedAvatar = if (context != null && !avatarB64.isNullOrBlank()) {
            saveBase64Avatar(context, id, avatarB64)
        } else null
        val avatarUri = savedAvatar ?: json.optString("avatarUri").takeIf { !it.isNullOrBlank() }

        val deviceInfo = (json.optString("dev").takeIf { !it.isNullOrBlank() } ?: json.optString("deviceInfo")).takeIf { !it.isNullOrBlank() }
        val timestamp = when {
            json.has("ts") -> json.getLong("ts")
            json.has("timestamp") -> json.getLong("timestamp")
            else -> null
        }
        val ip = json.optString("ip").takeIf { !it.isNullOrBlank() }
        val port = if (json.has("port") && json.getInt("port") > 0) json.getInt("port") else null
        val sessionToken = (json.optString("token").takeIf { !it.isNullOrBlank() } ?: json.optString("sessionToken")).takeIf { !it.isNullOrBlank() }
        val publicKey = (json.optString("pk").takeIf { !it.isNullOrBlank() } ?: json.optString("publicKey")).takeIf { !it.isNullOrBlank() }
        val signature = (json.optString("sig").takeIf { !it.isNullOrBlank() } ?: json.optString("signature")).takeIf { !it.isNullOrBlank() }
        val ttl = if (json.has("ttl") && json.getLong("ttl") > 0) json.getLong("ttl") else null

        // Validate cryptographic signature if public key & signature are present
        var isVerified = false
        if (!publicKey.isNullOrBlank() && !signature.isNullOrBlank() && timestamp != null) {
            val canonical = buildCanonicalPayloadString(
                id = id,
                name = name,
                publicKey = publicKey,
                timestamp = timestamp,
                ip = ip,
                port = port
            )
            isVerified = CryptoUtils.verifySignature(publicKey, canonical, signature)
            if (isVerified) {
                // Automatically derive mutual ECDH symmetric key
                CryptoUtils.deriveSharedKeyForContact(id, publicKey)
            }
        }

        // Check expiration
        val isExpired = if (ttl != null && timestamp != null) {
            (System.currentTimeMillis() - timestamp) > ttl
        } else false

        return ScannedProfileData(
            id = id,
            name = name,
            age = age,
            description = description,
            avatarUri = avatarUri,
            deviceInfo = deviceInfo,
            timestamp = timestamp,
            ip = ip,
            port = port,
            sessionToken = sessionToken,
            publicKey = publicKey,
            signature = signature,
            isVerified = isVerified,
            ttl = ttl,
            isExpired = isExpired
        )
    }

    /**
     * Generates a high quality QR Code Bitmap from content string.
     */
    fun generateQRCodeBitmap(content: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val hints = mapOf(
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
            com.google.zxing.EncodeHintType.MARGIN to 1
        )
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }
}
