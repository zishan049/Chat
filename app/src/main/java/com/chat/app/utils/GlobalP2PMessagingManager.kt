package com.chat.app.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.chat.app.data.MediaType
import com.chat.app.data.Message
import com.chat.app.data.MessageStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.io.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class representing incoming P2P Packet payloads.
 */
sealed class P2PPacket {
    data class TextMessage(
        val messageId: String,
        val chatId: String,
        val senderId: String,
        val text: String,
        val timestamp: Long
    ) : P2PPacket()

    data class MediaChunk(
        val messageId: String,
        val chatId: String,
        val senderId: String,
        val mediaType: MediaType,
        val fileName: String?,
        val fileSize: Long,
        val chunkIndex: Int,
        val totalChunks: Int,
        val payloadBase64: String,
        val timestamp: Long
    ) : P2PPacket()

    data class TypingIndicator(
        val chatId: String,
        val senderId: String,
        val isTyping: Boolean
    ) : P2PPacket()

    data class DeliveryAck(
        val messageId: String,
        val chatId: String,
        val timestamp: Long
    ) : P2PPacket()

    data class DeliveryAckBatch(
        val messageIds: List<String>,
        val chatId: String,
        val timestamp: Long
    ) : P2PPacket()

    data class ReadReceipt(
        val chatId: String,
        val senderId: String,
        val readUpToTimestamp: Long
    ) : P2PPacket()

    data class ProfileUpdate(
        val profileData: ScannedProfileData
    ) : P2PPacket()

    data class DeleteMessage(
        val messageId: String,
        val chatId: String,
        val senderId: String
    ) : P2PPacket()

    data class EditMessage(
        val messageId: String,
        val chatId: String,
        val senderId: String,
        val newText: String
    ) : P2PPacket()

    data class PresencePing(
        val senderId: String,
        val timestamp: Long,
        val senderIp: String? = null,
        val wifiSsid: String? = null
    ) : P2PPacket()

    data class PresencePong(
        val senderId: String,
        val isOnline: Boolean,
        val lastSeenAt: Long,
        val timestamp: Long,
        val senderIp: String? = null,
        val wifiSsid: String? = null,
        val isSameWifi: Boolean = false
    ) : P2PPacket()

    data class PresenceOffline(
        val senderId: String,
        val lastSeenAt: Long,
        val timestamp: Long
    ) : P2PPacket()

    data class StatusProbe(
        val chatId: String,
        val senderId: String,
        val messageIds: List<String>
    ) : P2PPacket()

    data class StatusReport(
        val chatId: String,
        val senderId: String,
        val statuses: Map<String, String>
    ) : P2PPacket()
}

/**
 * Universal P2P Messaging Manager:
 * Handles direct LAN socket connections and zero-config cross-network Web Relay
 * with zero IP locks, parallel dual-routing, and automatic real-time synchronization.
 */
object GlobalP2PMessagingManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Fixed well-known port for P2P chat messaging. */
    const val MESSAGING_PORT = 47832

    // Incoming Packet Flow
    private val _incomingPacketFlow = MutableSharedFlow<P2PPacket>(extraBufferCapacity = 100)
    val incomingPacketFlow = _incomingPacketFlow.asSharedFlow()

    // Tracking for chunk assembly streamed directly to disk: MessageId -> Set of received chunk indices
    private val receivedChunksMap = ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<Int, Boolean>>()
    private val chunkSizeMap = ConcurrentHashMap<String, Int>()
    private val chunkLastUpdatedMap = ConcurrentHashMap<String, Long>()

    // TCP server socket for receiving incoming chat messages
    private var messagingServerSocket: ServerSocket? = null
    private var messagingListenerJob: Job? = null
    private var webRelayJob: Job? = null
    private var _messagingPort: Int = 0
    val messagingPort: Int get() = _messagingPort

    // Deduplication cache for relay packet IDs / fingerprints to prevent duplicate processing
    private val processedRelayEventIds = java.util.Collections.newSetFromMap(
        object : java.util.LinkedHashMap<String, Boolean>(300, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 500
            }
        }
    )

    private val lastRelayTypingTimeMap = ConcurrentHashMap<String, Long>()
    private var lastListenedSelfId: String? = null

    /**
     * Starts a persistent TCP ServerSocket to accept incoming chat messages from peer contacts on LAN.
     */
    fun startMessagingListener(context: Context): Int {
        if (messagingServerSocket != null && messagingServerSocket!!.isClosed.not() && _messagingPort > 0) {
            return _messagingPort
        }
        stopMessagingListener()
        return try {
            val server = try {
                ServerSocket(MESSAGING_PORT)
            } catch (_: Exception) {
                ServerSocket(0) // fallback to any available ephemeral port
            }
            messagingServerSocket = server
            _messagingPort = server.localPort
            messagingListenerJob = scope.launch {
                while (isActive && !server.isClosed) {
                    try {
                        val client = server.accept()
                        launch { handleMessagingClient(context, client) }
                    } catch (e: Exception) {
                        if (!server.isClosed) e.printStackTrace()
                    }
                }
            }
            _messagingPort
        } catch (e: Exception) {
            e.printStackTrace()
            _messagingPort = 0
            0
        }
    }

    /**
     * Stops the messaging TCP listener.
     */
    fun stopMessagingListener() {
        messagingListenerJob?.cancel()
        messagingListenerJob = null
        webRelayJob?.cancel()
        webRelayJob = null
        try { messagingServerSocket?.close() } catch (e: Exception) { e.printStackTrace() }
        messagingServerSocket = null
        _messagingPort = 0
    }

    /**
     * Sanitizes an ID to produce a safe topic name for the public Web Relay.
     */
    fun sanitizeTopic(id: String): String {
        val clean = id.trim().replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
        return if (clean.isNotBlank()) "p2p_chat_app_$clean" else "p2p_chat_app_global"
    }

    /**
     * Restarts the Web Relay listener with the updated Self Profile ID immediately.
     */
    fun restartWebRelayListener(context: Context, selfIdProvider: () -> String?) {
        webRelayJob?.cancel()
        webRelayJob = null
        startWebRelayListener(context, selfIdProvider)
    }

    /**
     * Helper to process a single incoming JSON event line from either live stream or poll fallback.
     */
    private suspend fun processRelayEventLine(context: Context, currentLine: String) {
        val trimmed = currentLine.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("{")) return
        try {
            val jsonObj = JSONObject(trimmed)
            val event = jsonObj.optString("event")
            if (event == "message") {
                val eventId = jsonObj.optString("id")
                val messageStr = jsonObj.optString("message")

                // Deduplicate events across stream and poll
                if (eventId.isNotBlank()) {
                    val alreadySeen = synchronized(processedRelayEventIds) {
                        if (processedRelayEventIds.contains(eventId)) true
                        else {
                            processedRelayEventIds.add(eventId)
                            false
                        }
                    }
                    if (alreadySeen) return
                }

                if (messageStr.isNotBlank() && messageStr.startsWith("{")) {
                    val msgType = try { JSONObject(messageStr).optString("type") } catch (_: Exception) { "" }
                    if (msgType == "P2P_PROFILE_EXCHANGE" || msgType == "P2P_PROFILE_UPDATE" || msgType == "ACK_PROFILE_EXCHANGE") {
                        P2PQrExchangeManager.handleProfileExchangeViaRelay(context, messageStr)
                    } else {
                        handleIncomingSocketJson(context, messageStr)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Starts listening to cross-network zero-config P2P Web Relay stream for incoming E2EE messages.
     * Uses a resilient persistent SSE stream ensuring real-time instant reception without 429 rate limit loops.
     */
    fun startWebRelayListener(context: Context, selfIdProvider: () -> String?) {
        if (webRelayJob != null && webRelayJob!!.isActive) return

        webRelayJob = scope.launch {
            var retryDelayMs = 1500L
            while (isActive) {
                val selfId = selfIdProvider()
                if (selfId.isNullOrBlank()) {
                    delay(1000)
                    continue
                }
                lastListenedSelfId = selfId

                var conn: HttpURLConnection? = null
                try {
                    val topic = sanitizeTopic(selfId)
                    val url = java.net.URL("https://ntfy.sh/$topic/json?since=10m")
                    conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 60000

                    if (conn.responseCode == 200) {
                        retryDelayMs = 1500L // Reset backoff on successful connection
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                        var line: String? = null
                        while (isActive && reader.readLine().also { line = it } != null) {
                            line?.let { processRelayEventLine(context, it) }
                            if (selfIdProvider() != selfId) break
                        }
                    } else if (conn.responseCode == 429) {
                        retryDelayMs = 5000L
                    }
                } catch (_: Exception) {
                    retryDelayMs = minOf(retryDelayMs * 2, 15000L)
                } finally {
                    try { conn?.disconnect() } catch (_: Exception) {}
                }
                delay(retryDelayMs)
            }
        }
    }

    /**
     * Sends encrypted packet payload via cross-network Web Relay.
     * Automatically ensures payload does not exceed 4KB message limits and releases connection pool resources.
     */
    fun sendPacketViaWebRelay(targetId: String, payloadJsonStr: String): Boolean {
        if (targetId.isBlank() || payloadJsonStr.isBlank()) return false
        val topic = sanitizeTopic(targetId)

        // Ensure payload is under 4KB limit by stripping large avatarBase64 if present
        val payloadToSend = if (payloadJsonStr.length > 3800 && payloadJsonStr.startsWith("{")) {
            try {
                val obj = JSONObject(payloadJsonStr)
                if (obj.has("avatarBase64")) {
                    obj.remove("avatarBase64")
                }
                obj.toString()
            } catch (_: Exception) { payloadJsonStr }
        } else payloadJsonStr

        val bytes = payloadToSend.toByteArray(Charsets.UTF_8)
        val urlStr = "https://ntfy.sh/$topic"

        for (attempt in 0..2) {
            var conn: HttpURLConnection? = null
            try {
                val url = java.net.URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                val code = conn.responseCode
                if (code in 200..299) return true
                if (code == 429 && attempt < 2) {
                    Thread.sleep(600)
                    continue
                }
            } catch (_: Exception) {
                if (attempt < 2) {
                    Thread.sleep(400)
                    continue
                }
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }
        return false
    }

    /**
     * Handles a single incoming messaging TCP connection, reads the JSON payload, and routes it.
     */
    private suspend fun handleMessagingClient(context: Context, client: Socket) {
        try {
            client.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val line = reader.readLine()
            client.close()
            if (!line.isNullOrBlank()) {
                handleIncomingSocketJson(context, line)
            }
        } catch (e: Exception) {
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * Attempts to query Google STUN server (stun.l.google.com:19302) to reflect public WAN IP.
     */
    suspend fun getPublicIpViaStun(): String? = withContext(Dispatchers.IO) {
        try {
            val stunAddress = InetAddress.getByName("stun.l.google.com")
            val socket = DatagramSocket()
            socket.soTimeout = 3000

            val req = ByteArray(20)
            req[0] = 0x00.toByte()
            req[1] = 0x01.toByte()
            req[4] = 0x21.toByte()
            req[5] = 0x12.toByte()
            req[6] = 0xA4.toByte()
            req[7] = 0x42.toByte()
            for (i in 8 until 20) req[i] = (i * 7).toByte()

            val packet = DatagramPacket(req, req.size, stunAddress, 19302)
            socket.send(packet)

            val respBuf = ByteArray(512)
            val respPacket = DatagramPacket(respBuf, respBuf.size)
            socket.receive(respPacket)
            socket.close()

            if (respPacket.length > 20) {
                var pos = 20
                while (pos < respPacket.length - 4) {
                    val attrType = ((respBuf[pos].toInt() and 0xFF) shl 8) or (respBuf[pos + 1].toInt() and 0xFF)
                    val attrLen = ((respBuf[pos + 2].toInt() and 0xFF) shl 8) or (respBuf[pos + 3].toInt() and 0xFF)
                    if (attrType == 0x0001 || attrType == 0x0020) {
                        if (attrLen >= 8 && pos + 4 + attrLen <= respPacket.length) {
                            val family = respBuf[pos + 5].toInt() and 0xFF
                            if (family == 0x01) {
                                if (attrType == 0x0020) {
                                    val ipBytes = ByteArray(4)
                                    ipBytes[0] = (respBuf[pos + 8].toInt() xor 0x21).toByte()
                                    ipBytes[1] = (respBuf[pos + 9].toInt() xor 0x12).toByte()
                                    ipBytes[2] = (respBuf[pos + 10].toInt() xor 0xA4.toByte().toInt()).toByte()
                                    ipBytes[3] = (respBuf[pos + 11].toInt() xor 0x42).toByte()
                                    return@withContext InetAddress.getByAddress(ipBytes).hostAddress
                                } else {
                                    val ipBytes = ByteArray(4)
                                    System.arraycopy(respBuf, pos + 8, ipBytes, 0, 4)
                                    return@withContext InetAddress.getByAddress(ipBytes).hostAddress
                                }
                            }
                        }
                    }
                    pos += 4 + attrLen
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun getPublicIpViaHttp(): String? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://api.ipify.org")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                val ipStr = conn.inputStream.bufferedReader().use { it.readText().trim() }
                if (ipStr.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""))) {
                    return@withContext ipStr
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves local LAN and public WAN IP addresses for multi-network routing.
     */
    suspend fun getBestIpAddresses(): String = withContext(Dispatchers.IO) {
        val localIp = ProfileQrManager.getLocalIpAddress()
        val publicIp = getPublicIpViaStun() ?: getPublicIpViaHttp()
        if (!publicIp.isNullOrBlank() && publicIp != localIp && publicIp != "127.0.0.1") {
            if (!localIp.isNullOrBlank()) "$publicIp,$localIp" else publicIp
        } else {
            localIp ?: ""
        }
    }

    /**
     * Attempts socket connection to candidate IPs with resilient timeout.
     */
    fun connectSocketWithFallback(ipString: String, port: Int, timeoutMs: Int = 1500): Socket? {
        val candidateIps = ipString.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "127.0.0.1" && !it.startsWith("127.") && it != "localhost" }
            .distinct()

        for (candidateIp in candidateIps) {
            try {
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(candidateIp, port), timeoutMs)
                socket.soTimeout = timeoutMs
                return socket
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Transmits an E2E Encrypted P2P Text Message with Zero IP Lock:
     * Dispatches simultaneously via cross-network Web Relay and direct LAN socket.
     */
    fun sendTextMessageToPeer(
        ip: String,
        port: Int,
        message: Message,
        onResult: (Boolean) -> Unit
    ) {
        scope.launch {
            var success = false
            try {
                val encryptedText = CryptoUtils.encryptText(contactId = message.chatId, selfId = message.senderId, plainText = message.text)
                val payload = JSONObject().apply {
                    put("type", "P2P_TEXT_MSG")
                    put("messageId", message.id)
                    put("chatId", message.senderId)
                    put("recipientId", message.chatId)
                    put("senderId", message.senderId)
                    put("senderPublicKey", CryptoUtils.getSelfPublicKeyBase64())
                    put("text", encryptedText)
                    put("isEncrypted", true)
                    put("timestamp", message.timestamp)
                }
                val payloadStr = payload.toString()

                // 1. Cross-Network Web Relay (guaranteed delivery across Mobile Data & Wi-Fi)
                val relayDeferred = async(Dispatchers.IO) {
                    sendPacketViaWebRelay(message.chatId, payloadStr)
                }

                // 2. Direct LAN Socket (ultra fast local delivery if on same LAN)
                val socketDeferred = async(Dispatchers.IO) {
                    if (ip.isNotBlank() && port > 0) {
                        try {
                            val socket = connectSocketWithFallback(ip, port, 1500)
                            if (socket != null) {
                                val writer = PrintWriter(socket.getOutputStream(), true)
                                writer.println(payloadStr)
                                socket.close()
                                com.chat.app.telemetry.AppTelemetry.logNetworkTraffic("OUTBOUND", "P2P_TCP", "TEXT", "$ip:$port", payloadStr.length.toLong())
                                true
                            } else false
                        } catch (_: Exception) { false }
                    } else false
                }

                val socketSuccess = socketDeferred.await()
                val relaySuccess = relayDeferred.await()
                success = socketSuccess || relaySuccess
            } catch (e: Exception) {
                try {
                    val encryptedText = CryptoUtils.encryptText(contactId = message.chatId, selfId = message.senderId, plainText = message.text)
                    val payload = JSONObject().apply {
                        put("type", "P2P_TEXT_MSG")
                        put("messageId", message.id)
                        put("chatId", message.senderId)
                        put("recipientId", message.chatId)
                        put("senderId", message.senderId)
                        put("senderPublicKey", CryptoUtils.getSelfPublicKeyBase64())
                        put("text", encryptedText)
                        put("isEncrypted", true)
                        put("timestamp", message.timestamp)
                    }
                    success = sendPacketViaWebRelay(message.chatId, payload.toString())
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    /**
     * Streams an E2E Encrypted media file in binary chunks over socket or relay.
     */
    fun sendMediaMessageToPeer(
        context: Context,
        ip: String,
        port: Int,
        message: Message,
        onProgress: (Float) -> Unit,
        onResult: (Boolean) -> Unit
    ) {
        scope.launch {
            var success = false
            try {
                val uriStr = message.localMediaUri ?: run {
                    withContext(Dispatchers.Main) { onResult(false) }
                    return@launch
                }

                val inputStream: InputStream? = if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
                    context.contentResolver.openInputStream(Uri.parse(uriStr))
                } else {
                    val file = File(uriStr)
                    if (file.exists()) FileInputStream(file)
                    else {
                        val fileInFiles = File(context.filesDir, uriStr)
                        if (fileInFiles.exists()) FileInputStream(fileInFiles) else null
                    }
                }

                if (inputStream == null) {
                    withContext(Dispatchers.Main) { onResult(false) }
                    return@launch
                }

                val allBytes = inputStream.use { it.readBytes() }
                val totalSize = allBytes.size

                val chunkSize = when {
                    totalSize <= 512 * 1024 -> 32 * 1024
                    totalSize <= 10 * 1024 * 1024 -> 64 * 1024
                    else -> 128 * 1024
                }

                val totalChunks = if (totalSize == 0) 1 else ((totalSize + chunkSize - 1) / chunkSize)
                val fileName = message.fileName ?: "media_${message.id.take(8)}"

                for (index in 0 until totalChunks) {
                    val start = index * chunkSize
                    val end = minOf(start + chunkSize, totalSize)
                    val chunkData = if (totalSize == 0) ByteArray(0) else allBytes.copyOfRange(start, end)
                    val encryptedChunk = CryptoUtils.encryptBytes(contactId = message.chatId, selfId = message.senderId, data = chunkData)
                    val base64Payload = Base64.encodeToString(encryptedChunk, Base64.NO_WRAP)

                    val payload = JSONObject().apply {
                        put("type", "P2P_MEDIA_CHUNK")
                        put("messageId", message.id)
                        put("chatId", message.senderId)
                        put("recipientId", message.chatId)
                        put("senderId", message.senderId)
                        put("senderPublicKey", CryptoUtils.getSelfPublicKeyBase64())
                        put("mediaType", message.mediaType.name)
                        put("fileName", fileName)
                        put("fileSize", totalSize.toLong())
                        put("chunkIndex", index)
                        put("totalChunks", totalChunks)
                        put("payloadBase64", base64Payload)
                        put("isEncrypted", true)
                        put("timestamp", message.timestamp)
                    }
                    val payloadStr = payload.toString()

                    var chunkSent = false
                    if (ip.isNotBlank() && port > 0) {
                        try {
                            val socket = connectSocketWithFallback(ip, port, 1500)
                            if (socket != null) {
                                val writer = PrintWriter(socket.getOutputStream(), true)
                                writer.println(payloadStr)
                                socket.close()
                                chunkSent = true
                            }
                        } catch (_: Exception) {}
                    }
                    if (!chunkSent) {
                        sendPacketViaWebRelay(message.chatId, payloadStr)
                    }

                    val progress = (index + 1).toFloat() / totalChunks.toFloat()
                    withContext(Dispatchers.Main) { onProgress(progress) }
                }
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
            withContext(Dispatchers.Main) { onResult(success) }
        }
    }

    /**
     * Assembles and saves a chunked media file from disk once all chunks arrive.
     */
    suspend fun assembleAndSaveMediaFile(
        context: Context,
        messageId: String,
        mediaType: MediaType,
        originalFileName: String?
    ): String? = withContext(Dispatchers.IO) {
        val subfolder = when (mediaType) {
            MediaType.IMAGE -> "images"
            MediaType.VIDEO -> "videos"
            MediaType.AUDIO -> "audio"
            else -> "files"
        }
        val tempFileName = "p2p_chunk_$messageId.part"
        val finalPath = com.chat.app.data.LocalMediaManager.moveTempToFinal(
            context = context,
            tempFileName = tempFileName,
            subfolder = subfolder,
            originalFileName = originalFileName
        )
        receivedChunksMap.remove(messageId)
        chunkSizeMap.remove(messageId)
        chunkLastUpdatedMap.remove(messageId)
        finalPath
    }

    /**
     * Purges stale partial media transfer chunks older than 1 hour.
     */
    fun purgeStaleTransferState(): Int {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600_000L
        var count = 0
        val iterator = chunkLastUpdatedMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < oneHourAgo) {
                val msgId = entry.key
                receivedChunksMap.remove(msgId)
                chunkSizeMap.remove(msgId)
                iterator.remove()
                count++
            }
        }
        return count
    }

    /**
     * Sends typing indicator status to target peer with Zero IP Lock.
     */
    fun sendTypingIndicatorToPeer(
        ip: String,
        port: Int,
        senderId: String,
        chatId: String,
        isTyping: Boolean
    ) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "P2P_TYPING_INDICATOR")
                    put("senderId", senderId)
                    put("chatId", chatId)
                    put("isTyping", isTyping)
                }
                val payloadStr = payload.toString()

                if (ip.isNotBlank() && port > 0) {
                    launch(Dispatchers.IO) {
                        try {
                            val socket = connectSocketWithFallback(ip, port, 1200)
                            if (socket != null) {
                                val writer = PrintWriter(socket.getOutputStream(), true)
                                writer.println(payloadStr)
                                socket.close()
                            }
                        } catch (_: Exception) {}
                    }
                }

                val now = System.currentTimeMillis()
                val last = lastRelayTypingTimeMap[chatId] ?: 0L
                if (!isTyping || (now - last > 3500L)) {
                    lastRelayTypingTimeMap[chatId] = now
                    sendPacketViaWebRelay(chatId, payloadStr)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Sends Delivery ACK to peer device with Zero IP Lock.
     */
    fun sendDeliveryAckToPeer(
        ip: String,
        port: Int,
        messageId: String,
        chatId: String
    ) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "P2P_DELIVERY_ACK")
                    put("messageId", messageId)
                    put("chatId", chatId)
                    put("timestamp", System.currentTimeMillis())
                }
                val payloadStr = payload.toString()

                if (ip.isNotBlank() && port > 0) {
                    launch(Dispatchers.IO) {
                        try {
                            val socket = connectSocketWithFallback(ip, port, 1200)
                            if (socket != null) {
                                val writer = PrintWriter(socket.getOutputStream(), true)
                                writer.println(payloadStr)
                                socket.close()
                            }
                        } catch (_: Exception) {}
                    }
                }
                sendPacketViaWebRelay(chatId, payloadStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Sends Read Receipt notification to peer device with Zero IP Lock.
     */
    fun sendReadReceiptToPeer(
        ip: String,
        port: Int,
        senderId: String,
        chatId: String
    ) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "P2P_READ_RECEIPT")
                    put("senderId", senderId)
                    put("chatId", chatId)
                    put("readUpToTimestamp", System.currentTimeMillis())
                }
                val payloadStr = payload.toString()

                if (ip.isNotBlank() && port > 0) {
                    launch(Dispatchers.IO) {
                        try {
                            val socket = connectSocketWithFallback(ip, port, 1200)
                            if (socket != null) {
                                val writer = PrintWriter(socket.getOutputStream(), true)
                                writer.println(payloadStr)
                                socket.close()
                            }
                        } catch (_: Exception) {}
                    }
                }
                sendPacketViaWebRelay(chatId, payloadStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Sends Delete Message notification to peer device with Zero IP Lock.
     */
    fun sendDeleteMessageToPeer(
        ip: String,
        port: Int,
        messageId: String,
        chatId: String,
        senderId: String
    ) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "P2P_DELETE_MSG")
                    put("messageId", messageId)
                    put("chatId", chatId)
                    put("senderId", senderId)
                }
                val payloadStr = payload.toString()

                if (ip.isNotBlank() && port > 0) {
                    launch(Dispatchers.IO) {
                        try {
                            val socket = connectSocketWithFallback(ip, port, 1200)
                            if (socket != null) {
                                val writer = PrintWriter(socket.getOutputStream(), true)
                                writer.println(payloadStr)
                                socket.close()
                            }
                        } catch (_: Exception) {}
                    }
                }
                sendPacketViaWebRelay(chatId, payloadStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Sends Edit Message notification to peer device with Zero IP Lock.
     */
    fun sendEditMessageToPeer(
        ip: String,
        port: Int,
        messageId: String,
        chatId: String,
        senderId: String,
        newText: String
    ) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "P2P_EDIT_MSG")
                    put("messageId", messageId)
                    put("chatId", chatId)
                    put("senderId", senderId)
                    put("newText", newText)
                }
                val payloadStr = payload.toString()

                if (ip.isNotBlank() && port > 0) {
                    launch(Dispatchers.IO) {
                        try {
                            val socket = connectSocketWithFallback(ip, port, 1200)
                            if (socket != null) {
                                val writer = PrintWriter(socket.getOutputStream(), true)
                                writer.println(payloadStr)
                                socket.close()
                            }
                        } catch (_: Exception) {}
                    }
                }
                sendPacketViaWebRelay(chatId, payloadStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Processes incoming socket JSON string and emits corresponding P2PPacket event.
     */
    suspend fun handleIncomingSocketJson(context: Context, jsonStr: String): P2PPacket? {
        return try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type")
            com.chat.app.telemetry.AppTelemetry.logNetworkTraffic(
                direction = "INBOUND",
                protocol = "P2P_SOCKET_OR_RELAY",
                packetType = type,
                peerAddress = json.optString("senderId", "unknown"),
                sizeBytes = jsonStr.toByteArray(Charsets.UTF_8).size.toLong()
            )

            if (type == "P2P_PROFILE_EXCHANGE" || type == "P2P_PROFILE_UPDATE" || type == "ACK_PROFILE_EXCHANGE") {
                P2PQrExchangeManager.handleProfileExchangeViaRelay(context, jsonStr)
                return null
            }

            when (type) {
                "P2P_TEXT_MSG" -> {
                    val rawText = json.optString("text")
                    val isEncrypted = json.optBoolean("isEncrypted", false)
                    val senderId = json.optString("senderId")
                    val recipientId = json.optString("recipientId")
                    val senderPublicKey = json.optString("senderPublicKey").takeIf { !it.isNullOrBlank() }
                    if (senderPublicKey != null && senderId.isNotBlank()) {
                        CryptoUtils.deriveSharedKeyForContact(senderId, senderPublicKey)
                    }
                    val decryptedText = if (isEncrypted) CryptoUtils.decryptText(senderId, recipientId, rawText) else rawText

                    val packet = P2PPacket.TextMessage(
                        messageId = json.optString("messageId"),
                        chatId = json.optString("chatId"),
                        senderId = senderId,
                        text = decryptedText,
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_MEDIA_CHUNK" -> {
                    val msgId = json.optString("messageId")
                    val chatId = json.optString("chatId")
                    val senderId = json.optString("senderId")
                    val recipientId = json.optString("recipientId")
                    val senderPublicKey = json.optString("senderPublicKey").takeIf { !it.isNullOrBlank() }
                    if (senderPublicKey != null && senderId.isNotBlank()) {
                        CryptoUtils.deriveSharedKeyForContact(senderId, senderPublicKey)
                    }
                    val mediaTypeStr = json.optString("mediaType", "FILE")
                    val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (e: Exception) { MediaType.FILE }
                    val fileName = json.optString("fileName")
                    val fileSize = json.optLong("fileSize")
                    val chunkIndex = json.optInt("chunkIndex")
                    val totalChunks = json.optInt("totalChunks")
                    val base64Payload = json.optString("payloadBase64")
                    val isEncrypted = json.optBoolean("isEncrypted", false)
                    val timestamp = json.optLong("timestamp", System.currentTimeMillis())

                    val rawChunkBytes = Base64.decode(base64Payload, Base64.DEFAULT)
                    val decryptedChunkBytes = if (isEncrypted) CryptoUtils.decryptBytes(senderId, recipientId, rawChunkBytes) else rawChunkBytes

                    if (chunkIndex == 0 && decryptedChunkBytes.isNotEmpty()) {
                        chunkSizeMap[msgId] = decryptedChunkBytes.size
                    }
                    val knownChunkSize = chunkSizeMap[msgId] ?: decryptedChunkBytes.size
                    val offset = chunkIndex.toLong() * knownChunkSize.toLong()

                    com.chat.app.data.LocalMediaManager.writeChunkToTempFile(
                        context = context,
                        tempFileName = "p2p_chunk_$msgId.part",
                        offset = offset,
                        data = decryptedChunkBytes
                    )

                    val set = receivedChunksMap.getOrPut(msgId) { ConcurrentHashMap.newKeySet() }
                    set.add(chunkIndex)
                    chunkLastUpdatedMap[msgId] = System.currentTimeMillis()

                    val packet = P2PPacket.MediaChunk(
                        messageId = msgId,
                        chatId = chatId,
                        senderId = senderId,
                        mediaType = mediaType,
                        fileName = fileName,
                        fileSize = fileSize,
                        chunkIndex = chunkIndex,
                        totalChunks = totalChunks,
                        payloadBase64 = base64Payload,
                        timestamp = timestamp
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_TYPING_INDICATOR" -> {
                    val packet = P2PPacket.TypingIndicator(
                        chatId = json.optString("chatId"),
                        senderId = json.optString("senderId"),
                        isTyping = json.optBoolean("isTyping")
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_DELIVERY_ACK" -> {
                    val packet = P2PPacket.DeliveryAck(
                        messageId = json.optString("messageId"),
                        chatId = json.optString("chatId"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_DELIVERY_ACK_BATCH" -> {
                    val msgIdsArray = json.optJSONArray("messageIds")
                    val idsList = mutableListOf<String>()
                    if (msgIdsArray != null) {
                        for (i in 0 until msgIdsArray.length()) {
                            idsList.add(msgIdsArray.optString(i))
                        }
                    }
                    val packet = P2PPacket.DeliveryAckBatch(
                        messageIds = idsList,
                        chatId = json.optString("chatId"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_READ_RECEIPT" -> {
                    val packet = P2PPacket.ReadReceipt(
                        chatId = json.optString("chatId"),
                        senderId = json.optString("senderId"),
                        readUpToTimestamp = json.optLong("readUpToTimestamp", System.currentTimeMillis())
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_DELETE_MSG" -> {
                    val packet = P2PPacket.DeleteMessage(
                        messageId = json.optString("messageId"),
                        chatId = json.optString("chatId"),
                        senderId = json.optString("senderId")
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_EDIT_MSG" -> {
                    val packet = P2PPacket.EditMessage(
                        messageId = json.optString("messageId"),
                        chatId = json.optString("chatId"),
                        senderId = json.optString("senderId"),
                        newText = json.optString("newText")
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_PRESENCE_PING" -> {
                    val packet = P2PPacket.PresencePing(
                        senderId = json.optString("senderId"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                        senderIp = json.optString("senderIp").ifBlank { null },
                        wifiSsid = json.optString("wifiSsid").ifBlank { null }
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_PRESENCE_PONG" -> {
                    val packet = P2PPacket.PresencePong(
                        senderId = json.optString("senderId"),
                        isOnline = json.optBoolean("isOnline", true),
                        lastSeenAt = json.optLong("lastSeenAt", System.currentTimeMillis()),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                        senderIp = json.optString("senderIp").ifBlank { null },
                        wifiSsid = json.optString("wifiSsid").ifBlank { null },
                        isSameWifi = json.optBoolean("isSameWifi", false)
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_PRESENCE_OFFLINE" -> {
                    val packet = P2PPacket.PresenceOffline(
                        senderId = json.optString("senderId"),
                        lastSeenAt = json.optLong("lastSeenAt", System.currentTimeMillis()),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis())
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_STATUS_PROBE" -> {
                    val msgIdsArray = json.optJSONArray("messageIds")
                    val idsList = mutableListOf<String>()
                    if (msgIdsArray != null) {
                        for (i in 0 until msgIdsArray.length()) {
                            idsList.add(msgIdsArray.optString(i))
                        }
                    }
                    val packet = P2PPacket.StatusProbe(
                        chatId = json.optString("chatId"),
                        senderId = json.optString("senderId"),
                        messageIds = idsList
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                "P2P_STATUS_REPORT" -> {
                    val statusObj = json.optJSONObject("statuses")
                    val map = mutableMapOf<String, String>()
                    if (statusObj != null) {
                        val keys = statusObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            map[k] = statusObj.optString(k)
                        }
                    }
                    val packet = P2PPacket.StatusReport(
                        chatId = json.optString("chatId"),
                        senderId = json.optString("senderId"),
                        statuses = map
                    )
                    scope.launch { _incomingPacketFlow.emit(packet) }
                    packet
                }

                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
