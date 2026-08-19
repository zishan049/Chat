package com.chat.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.chat.app.data.Profile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * Manages P2P QR Contact Handshakes and Live Profile Exchanges.
 * Supports simultaneous LAN TCP socket exchange and zero-config cross-network Web Relay.
 */
object P2PQrExchangeManager {

    private var serverSocket: ServerSocket? = null
    private var listeningJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Shared Flow for contact exchange broadcast signals
    private val _localBroadcastFlow = MutableSharedFlow<ScannedProfileData>(extraBufferCapacity = 20)
    val localBroadcastFlow = _localBroadcastFlow.asSharedFlow()

    private var activePort: Int = 0
    private var currentSelfProfileProvider: (() -> Profile?)? = null
    private var currentOnProfileReceived: ((ScannedProfileData) -> Unit)? = null

    fun getActivePort(): Int = activePort

    /**
     * Starts listening persistently on a TCP socket for incoming profile exchange & live update requests.
     */
    fun startListener(
        context: Context,
        selfProfileProvider: () -> Profile?,
        onProfileReceived: (ScannedProfileData) -> Unit
    ): Int {
        currentSelfProfileProvider = selfProfileProvider
        currentOnProfileReceived = onProfileReceived

        if (serverSocket != null && !serverSocket!!.isClosed && activePort > 0) {
            return activePort
        }
        stopListener()
        try {
            val server = ServerSocket(0)
            serverSocket = server
            activePort = server.localPort

            listeningJob = scope.launch {
                while (isActive && !server.isClosed) {
                    try {
                        val client = server.accept()
                        launch {
                            handleIncomingConnection(context, selfProfileProvider, client, onProfileReceived)
                        }
                    } catch (e: Exception) {
                        if (!server.isClosed) e.printStackTrace()
                    }
                }
            }
            return activePort
        } catch (e: Exception) {
            e.printStackTrace()
            activePort = 0
            return 0
        }
    }

    /**
     * Stops the socket listener.
     */
    fun stopListener() {
        listeningJob?.cancel()
        listeningJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        activePort = 0
    }

    /**
     * Sends self profile to the peer over TCP socket and cross-network Web Relay concurrently.
     */
    fun sendProfileToPeer(
        context: Context,
        ip: String,
        port: Int,
        selfProfile: Profile?,
        peerContactId: String? = null,
        onResponseReceived: (Boolean, ScannedProfileData?) -> Unit
    ) {
        if (selfProfile == null) {
            onResponseReceived(false, null)
            return
        }

        scope.launch {
            var success = false
            var peerProfileWithAvatar: ScannedProfileData? = null

            try {
                val bestIp = GlobalP2PMessagingManager.getBestIpAddresses()
                val selfPk = CryptoUtils.getSelfPublicKeyBase64()
                val timestamp = System.currentTimeMillis()
                val canonical = ProfileQrManager.buildCanonicalPayloadString(
                    id = selfProfile.id,
                    name = selfProfile.username,
                    publicKey = selfPk,
                    timestamp = timestamp,
                    ip = bestIp,
                    port = activePort
                )
                val signature = CryptoUtils.signPayload(canonical)

                val avatarB64 = ProfileQrManager.encodeAvatarToBase64(context, selfProfile.avatarUri)
                val payloadJson = JSONObject().apply {
                    put("type", "P2P_PROFILE_EXCHANGE")
                    put("id", selfProfile.id)
                    put("name", selfProfile.username)
                    put("age", selfProfile.age ?: 0)
                    put("status", selfProfile.description ?: "")
                    if (!avatarB64.isNullOrBlank()) put("avatarB64", avatarB64)
                    put("publicKey", selfPk)
                    put("signature", signature)
                    put("deviceInfo", ProfileQrManager.getDeviceInfo())
                    put("ip", bestIp)
                    put("port", activePort)
                    put("timestamp", timestamp)
                }
                val payloadStr = payloadJson.toString()

                // Race fast LAN socket and Web Relay concurrently
                val cleanIp = if (ip == "127.0.0.1" || ip.startsWith("127.") || ip.equals("localhost", ignoreCase = true)) "" else ip
                val socketJob = async(Dispatchers.IO) {
                    if (cleanIp.isNotBlank() && port > 0) {
                        try {
                            val socket = GlobalP2PMessagingManager.connectSocketWithFallback(cleanIp, port, 1500)
                            if (socket != null) {
                                val writer = PrintWriter(socket.getOutputStream(), true)
                                writer.println(payloadStr)
                                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                                val response = reader.readLine()
                                socket.close()
                                if (!response.isNullOrBlank() && response.startsWith("{")) {
                                    parseAckProfileExchange(context, response, cleanIp, port)
                                } else null
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                }

                val relayJob = async(Dispatchers.IO) {
                    val targetId = peerContactId
                    if (!targetId.isNullOrBlank()) {
                        GlobalP2PMessagingManager.sendPacketViaWebRelay(
                            targetId = targetId,
                            payloadJsonStr = payloadStr
                        )
                    } else false
                }

                val socketResult = socketJob.await()
                if (socketResult != null) {
                    peerProfileWithAvatar = socketResult
                    success = true
                } else {
                    success = relayJob.await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                onResponseReceived(success, peerProfileWithAvatar)
            }
        }
    }

    /**
     * Handles incoming profile exchange packets arriving over the Web Relay stream.
     */
    fun handleProfileExchangeViaRelay(context: Context, jsonStr: String) {
        scope.launch {
            try {
                val json = JSONObject(jsonStr)
                val type = json.optString("type")

                if (type == "P2P_PROFILE_EXCHANGE" || type == "P2P_PROFILE_UPDATE") {
                    val scannerId = json.optString("id")
                    val scannerName = json.optString("name")
                    val scannerAge = if (json.has("age") && json.getInt("age") > 0) json.getInt("age") else null
                    val scannerStatus = json.optString("status").takeIf { !it.isNullOrBlank() }
                    val scannerDeviceInfo = json.optString("deviceInfo").takeIf { !it.isNullOrBlank() }
                    val scannerTimestamp = if (json.has("timestamp")) json.getLong("timestamp") else System.currentTimeMillis()
                    val scannerIp = json.optString("ip").takeIf { !it.isNullOrBlank() }
                    val scannerPort = if (json.has("port") && json.getInt("port") > 0) json.getInt("port") else null
                    val scannerPublicKey = json.optString("publicKey").takeIf { !it.isNullOrBlank() }
                    val scannerSignature = json.optString("signature").takeIf { !it.isNullOrBlank() }
                    val avatarB64 = json.optString("avatarB64").takeIf { !it.isNullOrBlank() }
                    val savedAvatarPath = if (!avatarB64.isNullOrBlank()) {
                        ProfileQrManager.saveBase64Avatar(context, scannerId, avatarB64)
                    } else null

                    var isVerified = false
                    if (scannerPublicKey != null) {
                        CryptoUtils.deriveSharedKeyForContact(scannerId, scannerPublicKey)
                        if (scannerSignature != null) {
                            val canonical = ProfileQrManager.buildCanonicalPayloadString(
                                id = scannerId,
                                name = scannerName,
                                publicKey = scannerPublicKey,
                                timestamp = scannerTimestamp,
                                ip = scannerIp,
                                port = scannerPort
                            )
                            isVerified = CryptoUtils.verifySignature(scannerPublicKey, canonical, scannerSignature)
                        }
                    }

                    val scannerData = ScannedProfileData(
                        id = scannerId,
                        name = scannerName,
                        age = scannerAge,
                        description = scannerStatus,
                        avatarUri = savedAvatarPath,
                        deviceInfo = scannerDeviceInfo,
                        timestamp = scannerTimestamp,
                        ip = scannerIp,
                        port = scannerPort,
                        publicKey = scannerPublicKey,
                        signature = scannerSignature,
                        isVerified = isVerified
                    )

                    _localBroadcastFlow.emit(scannerData)
                    currentOnProfileReceived?.invoke(scannerData)

                    // Post ACK back to scanner via Web Relay so scanner receives host profile
                    val self = currentSelfProfileProvider?.invoke()
                    if (self != null && scannerId.isNotBlank() && type == "P2P_PROFILE_EXCHANGE") {
                        val selfPk = CryptoUtils.getSelfPublicKeyBase64()
                        val ackTimestamp = System.currentTimeMillis()
                        val ackCanonical = ProfileQrManager.buildCanonicalPayloadString(
                            id = self.id,
                            name = self.username,
                            publicKey = selfPk,
                            timestamp = ackTimestamp,
                            ip = GlobalP2PMessagingManager.getBestIpAddresses(),
                            port = activePort
                        )
                        val ackSignature = CryptoUtils.signPayload(ackCanonical)
                        val selfAvatarB64 = ProfileQrManager.encodeAvatarToBase64(context, self.avatarUri)

                        val ackJson = JSONObject().apply {
                            put("type", "ACK_PROFILE_EXCHANGE")
                            put("id", self.id)
                            put("name", self.username)
                            put("age", self.age ?: 0)
                            put("status", self.description ?: "")
                            if (!selfAvatarB64.isNullOrBlank()) put("avatarB64", selfAvatarB64)
                            put("publicKey", selfPk)
                            put("signature", ackSignature)
                            put("deviceInfo", ProfileQrManager.getDeviceInfo())
                            put("ip", GlobalP2PMessagingManager.getBestIpAddresses())
                            put("port", activePort)
                            put("timestamp", ackTimestamp)
                        }
                        GlobalP2PMessagingManager.sendPacketViaWebRelay(
                            targetId = scannerId,
                            payloadJsonStr = ackJson.toString()
                        )
                    }
                } else if (type == "ACK_PROFILE_EXCHANGE") {
                    val hostData = parseAckProfileExchange(context, jsonStr, null, null)
                    if (hostData != null) {
                        _localBroadcastFlow.emit(hostData)
                        currentOnProfileReceived?.invoke(hostData)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseAckProfileExchange(
        context: Context,
        jsonStr: String,
        fallbackIp: String?,
        fallbackPort: Int?
    ): ScannedProfileData? {
        return try {
            val json = JSONObject(jsonStr)
            val hostId = json.optString("id")
            val hostName = json.optString("name")
            val hostPublicKey = json.optString("publicKey").takeIf { !it.isNullOrBlank() }
            val hostSignature = json.optString("signature").takeIf { !it.isNullOrBlank() }
            val hostTimestamp = if (json.has("timestamp")) json.getLong("timestamp") else null
            val hostIp = json.optString("ip").takeIf { !it.isNullOrBlank() } ?: fallbackIp
            val hostPort = if (json.has("port") && json.getInt("port") > 0) json.getInt("port") else fallbackPort
            val hostAvatarB64 = json.optString("avatarB64").takeIf { !it.isNullOrBlank() }
            val savedHostAvatar = if (!hostAvatarB64.isNullOrBlank()) {
                ProfileQrManager.saveBase64Avatar(context, hostId, hostAvatarB64)
            } else null

            var isVerified = false
            if (hostPublicKey != null) {
                CryptoUtils.deriveSharedKeyForContact(hostId, hostPublicKey)
                if (hostSignature != null && hostTimestamp != null) {
                    val hostCanonical = ProfileQrManager.buildCanonicalPayloadString(
                        id = hostId,
                        name = hostName,
                        publicKey = hostPublicKey,
                        timestamp = hostTimestamp,
                        ip = hostIp,
                        port = hostPort
                    )
                    isVerified = CryptoUtils.verifySignature(hostPublicKey, hostCanonical, hostSignature)
                }
            }

            ScannedProfileData(
                id = hostId,
                name = hostName,
                age = if (json.has("age") && json.getInt("age") > 0) json.getInt("age") else null,
                description = json.optString("status").takeIf { !it.isNullOrBlank() },
                avatarUri = savedHostAvatar,
                deviceInfo = json.optString("deviceInfo").takeIf { !it.isNullOrBlank() },
                timestamp = hostTimestamp,
                ip = hostIp,
                port = hostPort,
                publicKey = hostPublicKey,
                signature = hostSignature,
                isVerified = isVerified
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Broadcasts profile update live to all active contact peers across local sockets and Web Relay.
     */
    fun broadcastProfileUpdateToPeers(
        context: Context,
        contacts: List<Profile>,
        selfProfile: Profile?
    ) {
        if (selfProfile == null) return
        scope.launch {
            try {
                val selfPk = CryptoUtils.getSelfPublicKeyBase64()
                val timestamp = System.currentTimeMillis()
                val bestIp = GlobalP2PMessagingManager.getBestIpAddresses()
                val canonical = ProfileQrManager.buildCanonicalPayloadString(
                    id = selfProfile.id,
                    name = selfProfile.username,
                    publicKey = selfPk,
                    timestamp = timestamp,
                    ip = bestIp,
                    port = activePort
                )
                val signature = CryptoUtils.signPayload(canonical)

                val updatePayload = JSONObject().apply {
                    put("type", "P2P_PROFILE_UPDATE")
                    put("id", selfProfile.id)
                    put("name", selfProfile.username)
                    put("age", selfProfile.age ?: 0)
                    put("status", selfProfile.description ?: "")
                    put("publicKey", selfPk)
                    put("signature", signature)
                    put("deviceInfo", ProfileQrManager.getDeviceInfo())
                    put("ip", bestIp)
                    put("port", activePort)
                    put("timestamp", timestamp)
                }
                val payloadStr = updatePayload.toString()

                contacts.forEach { contact ->
                    if (!contact.isSelf && !contact.isBlocked) {
                        launch {
                            if (!contact.lastKnownIp.isNullOrBlank() && contact.lastKnownPort != null && contact.lastKnownPort > 0) {
                                try {
                                    val socket = Socket(contact.lastKnownIp, contact.lastKnownPort)
                                    socket.soTimeout = 1500
                                    val writer = PrintWriter(socket.getOutputStream(), true)
                                    writer.println(payloadStr)
                                    socket.close()
                                } catch (_: Exception) {}
                            }
                            GlobalP2PMessagingManager.sendPacketViaWebRelay(contact.id, payloadStr)
                        }
                    }
                }

                val scannedData = ScannedProfileData(
                    id = selfProfile.id,
                    name = selfProfile.username,
                    age = selfProfile.age,
                    description = selfProfile.description,
                    avatarUri = selfProfile.avatarUri,
                    deviceInfo = ProfileQrManager.getDeviceInfo(),
                    timestamp = System.currentTimeMillis(),
                    publicKey = selfPk,
                    isVerified = true
                )
                _localBroadcastFlow.emit(scannedData)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Broadcasts profile locally across instances / emulators running on same workspace.
     */
    fun broadcastProfileLocally(scannedData: ScannedProfileData) {
        scope.launch {
            _localBroadcastFlow.emit(scannedData)
        }
    }

    private suspend fun handleIncomingConnection(
        context: Context,
        selfProfileProvider: () -> Profile?,
        client: Socket,
        onProfileReceived: (ScannedProfileData) -> Unit
    ) {
        try {
            client.soTimeout = 6000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val line = reader.readLine() ?: return

            val json = JSONObject(line)
            val msgType = json.optString("type")
            if (msgType.startsWith("P2P_") && msgType != "P2P_PROFILE_EXCHANGE" && msgType != "P2P_PROFILE_UPDATE") {
                scope.launch { GlobalP2PMessagingManager.handleIncomingSocketJson(context, line) }
                try { client.close() } catch (_: Exception) {}
                return
            }

            if (msgType == "P2P_PROFILE_EXCHANGE" || msgType == "P2P_PROFILE_UPDATE" || json.has("id")) {
                val id = json.optString("id")
                val name = json.optString("name")
                val age = if (json.has("age") && json.getInt("age") > 0) json.getInt("age") else null
                val status = json.optString("status").takeIf { !it.isNullOrBlank() }
                val deviceInfo = json.optString("deviceInfo").takeIf { !it.isNullOrBlank() }
                val timestamp = if (json.has("timestamp")) json.getLong("timestamp") else System.currentTimeMillis()
                val peerIp = json.optString("ip").takeIf { !it.isNullOrBlank() } ?: client.inetAddress?.hostAddress
                val peerPort = if (json.has("port")) json.getInt("port") else null
                val publicKey = json.optString("publicKey").takeIf { !it.isNullOrBlank() }
                val signature = json.optString("signature").takeIf { !it.isNullOrBlank() }

                val avatarB64 = json.optString("avatarB64").takeIf { !it.isNullOrBlank() }
                val savedAvatarPath = if (!avatarB64.isNullOrBlank()) {
                    ProfileQrManager.saveBase64Avatar(context, id, avatarB64)
                } else null

                var isVerified = false
                if (publicKey != null) {
                    CryptoUtils.deriveSharedKeyForContact(id, publicKey)
                    if (signature != null) {
                        val canonical = ProfileQrManager.buildCanonicalPayloadString(
                            id = id,
                            name = name,
                            publicKey = publicKey,
                            timestamp = timestamp,
                            ip = peerIp,
                            port = peerPort
                        )
                        isVerified = CryptoUtils.verifySignature(publicKey, canonical, signature)
                    }
                }

                val scannedData = ScannedProfileData(
                    id = id,
                    name = name,
                    age = age,
                    description = status,
                    avatarUri = savedAvatarPath,
                    deviceInfo = deviceInfo,
                    timestamp = timestamp,
                    ip = peerIp,
                    port = peerPort,
                    publicKey = publicKey,
                    signature = signature,
                    isVerified = isVerified
                )

                // Host sends Host's self profile back to Scanner device
                val self = selfProfileProvider()
                val bestIp = GlobalP2PMessagingManager.getBestIpAddresses()
                val selfPk = CryptoUtils.getSelfPublicKeyBase64()
                val ackTimestamp = System.currentTimeMillis()
                val ackCanonical = ProfileQrManager.buildCanonicalPayloadString(
                    id = self?.id ?: "",
                    name = self?.username ?: "",
                    publicKey = selfPk,
                    timestamp = ackTimestamp,
                    ip = bestIp,
                    port = activePort
                )
                val ackSignature = CryptoUtils.signPayload(ackCanonical)
                val selfAvatarB64 = if (self != null) ProfileQrManager.encodeAvatarToBase64(context, self.avatarUri) else null

                val responseJson = JSONObject().apply {
                    put("type", "ACK_PROFILE_EXCHANGE")
                    put("id", self?.id ?: "")
                    put("name", self?.username ?: "")
                    put("age", self?.age ?: 0)
                    put("status", self?.description ?: "")
                    if (!selfAvatarB64.isNullOrBlank()) put("avatarB64", selfAvatarB64)
                    put("publicKey", selfPk)
                    put("signature", ackSignature)
                    put("deviceInfo", ProfileQrManager.getDeviceInfo())
                    put("ip", bestIp)
                    put("port", activePort)
                    put("timestamp", ackTimestamp)
                }

                val writer = PrintWriter(client.getOutputStream(), true)
                writer.println(responseJson.toString())
                client.close()

                withContext(Dispatchers.Main) {
                    onProfileReceived(scannedData)
                }
            } else {
                client.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try { client.close() } catch (_: Exception) {}
        }
    }
}
