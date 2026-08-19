package com.chat.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.chat.app.data.Profile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Real-time Peer Presence Data Model
 */
data class PeerPresence(
    val peerId: String,
    val isOnline: Boolean,
    val isSameWifi: Boolean = false,
    val wifiSsid: String? = null,
    val lastSeenAt: Long? = null,
    val lastHeartbeatReceivedAt: Long = System.currentTimeMillis()
)

/**
 * Dedicated In-App Backend API for:
 * User Online Status Badge (OSB) & Wi-Fi / Cellular Dual-Engine Presence:
 * - Tiered presence heartbeats, Ping/Pong, Offline announcements, and Last Seen tracking.
 * - Zero-delay instant network transition detection (Wi-Fi ⇄ Cellular Data).
 * - Real-time discovery of peers sharing the same local Wi-Fi / LAN network.
 */
object P2POsbApiManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Real-time peer presence state table (PeerId -> PeerPresence)
    private val _peerPresenceMap = MutableStateFlow<Map<String, PeerPresence>>(emptyMap())
    val peerPresenceMap: StateFlow<Map<String, PeerPresence>> = _peerPresenceMap.asStateFlow()

    private var heartbeatJob: Job? = null
    private var presenceSweepJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Adaptive Heartbeat Intervals
    const val HEARTBEAT_ACTIVE_CHAT_MS = 5_000L      // High priority: active conversation peer
    const val HEARTBEAT_RECENT_CHATS_MS = 25_000L    // Medium priority: background recent contacts
    const val HEARTBEAT_IDLE_MS = 90_000L            // Low priority: distant contacts

    // Sweeper Timeouts
    const val LAN_PRESENCE_TIMEOUT_MS = 16_000L      // Direct local LAN Wi-Fi timeout
    const val RELAY_PRESENCE_TIMEOUT_MS = 35_000L    // Cross-network / Cellular data timeout

    // Dynamic IP change listener callback
    var onPeerIpDiscovered: ((peerId: String, newIp: String) -> Unit)? = null

    /**
     * Checks if two IPv4 addresses belong to the same local subnet (e.g. 192.168.1.xxx)
     */
    fun isSameSubnet(ip1: String?, ip2: String?): Boolean {
        if (ip1.isNullOrBlank() || ip2.isNullOrBlank()) return false
        if (ip1 == "127.0.0.1" || ip2 == "127.0.0.1") return false
        val parts1 = ip1.split(".")
        val parts2 = ip2.split(".")
        if (parts1.size == 4 && parts2.size == 4) {
            return parts1[0] == parts2[0] && parts1[1] == parts2[1] && parts1[2] == parts2[2]
        }
        return false
    }

    /**
     * Registers a zero-delay Android network listener for instant Wi-Fi ⇄ Cellular transitions.
     */
    fun registerNetworkMonitor(context: Context, onNetworkSwitched: (() -> Unit)? = null) {
        if (networkCallback != null) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handleNetworkTransition(cm, onNetworkSwitched)
                }

                override fun onLost(network: Network) {
                    handleNetworkTransition(cm, onNetworkSwitched)
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    handleNetworkTransition(cm, onNetworkSwitched)
                }
            }
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleNetworkTransition(cm: ConnectivityManager, onNetworkSwitched: (() -> Unit)?) {
        scope.launch {
            val newLocalIp = ProfileQrManager.getLocalIpAddress()
            val isWifi = cm.activeNetwork?.let { nw ->
                cm.getNetworkCapabilities(nw)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            } ?: false

            // Instant 0ms presence state table update
            _peerPresenceMap.update { currentMap ->
                val updated = currentMap.toMutableMap()
                for ((peerId, presence) in currentMap) {
                    val isSame = isWifi && isSameSubnet(newLocalIp, presence.wifiSsid)
                    if (presence.isSameWifi != isSame) {
                        updated[peerId] = presence.copy(isSameWifi = isSame)
                    }
                }
                updated
            }
            onNetworkSwitched?.invoke()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  OSB (Online Status Badge) & Wi-Fi Proximity Lifecycle & Heartbeat
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Starts periodic background presence beacon with adaptive tiers and timeout sweeper.
     */
    fun startPresenceHeartbeat(
        selfProfileProvider: () -> Profile?,
        contactsProvider: suspend () -> List<Profile>,
        activeChatIdProvider: () -> String? = { null },
        onLastSeenPersist: ((peerId: String, lastSeenAt: Long) -> Unit)? = null
    ) {
        if (heartbeatJob?.isActive == true) return

        heartbeatJob = scope.launch {
            var cycleCounter = 0
            while (isActive) {
                val self = selfProfileProvider()
                val contacts = contactsProvider()
                val activeChatId = activeChatIdProvider()

                if (self != null && contacts.isNotEmpty()) {
                    val myLocalIp = ProfileQrManager.getLocalIpAddress()
                    cycleCounter++

                    // 1. High priority: ping active chat peer immediately (every cycle = 5s)
                    if (!activeChatId.isNullOrBlank()) {
                        val activeContact = contacts.find { it.id == activeChatId && !it.isBlocked }
                        if (activeContact != null) {
                            sendPresencePing(
                                targetIp = activeContact.lastKnownIp ?: "",
                                targetPort = activeContact.lastKnownPort ?: GlobalP2PMessagingManager.MESSAGING_PORT,
                                targetId = activeContact.id,
                                senderId = self.id,
                                senderIp = myLocalIp,
                                allowRelay = true
                            )
                        }
                    }

                    // 2. Medium priority (every 5th cycle = ~25s): ping recent / background contacts
                    val isMediumTierRound = (cycleCounter % 5 == 0)
                    if (isMediumTierRound) {
                        for (contact in contacts) {
                            if (!contact.isBlocked && contact.id != self.id && contact.id != activeChatId) {
                                val isSameWifi = isSameSubnet(myLocalIp, contact.lastKnownIp)
                                sendPresencePing(
                                    targetIp = contact.lastKnownIp ?: "",
                                    targetPort = contact.lastKnownPort ?: GlobalP2PMessagingManager.MESSAGING_PORT,
                                    targetId = contact.id,
                                    senderId = self.id,
                                    senderIp = myLocalIp,
                                    allowRelay = true
                                )
                            }
                        }
                    }
                }
                delay(HEARTBEAT_ACTIVE_CHAT_MS)
            }
        }

        // Presence sweeper: Auto-marks peers as offline if no ping/pong was heard within timeout
        presenceSweepJob = scope.launch {
            while (isActive) {
                delay(2_500L)
                val now = System.currentTimeMillis()
                _peerPresenceMap.update { currentMap ->
                    var changed = false
                    val updatedMap = currentMap.toMutableMap()
                    for ((peerId, presence) in currentMap) {
                        val timeout = if (presence.isSameWifi) LAN_PRESENCE_TIMEOUT_MS else RELAY_PRESENCE_TIMEOUT_MS
                        if (presence.isOnline && (now - presence.lastHeartbeatReceivedAt > timeout)) {
                            val lastSeen = presence.lastHeartbeatReceivedAt
                            updatedMap[peerId] = presence.copy(
                                isOnline = false,
                                isSameWifi = false,
                                lastSeenAt = lastSeen
                            )
                            changed = true
                            com.chat.app.telemetry.AppTelemetry.logOsbPresence(
                                peerId = peerId,
                                peerName = null,
                                isOnline = false,
                                lastSeen = lastSeen,
                                action = "OFFLINE_TIMEOUT"
                            )
                            onLastSeenPersist?.invoke(peerId, lastSeen)
                        }
                    }
                    if (changed) updatedMap else currentMap
                }
            }
        }
    }

    /**
     * Stops the heartbeat loop.
     */
    fun stopPresenceHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        presenceSweepJob?.cancel()
        presenceSweepJob = null
    }

    /**
     * Dispatches graceful offline farewell packet to contacts when app goes to background.
     */
    fun sendPresenceOffline(selfId: String, contacts: List<Profile>) {
        scope.launch {
            val now = System.currentTimeMillis()
            for (contact in contacts) {
                if (!contact.isBlocked && contact.id != selfId) {
                    sendPresenceOfflinePacket(
                        targetIp = contact.lastKnownIp ?: "",
                        targetPort = contact.lastKnownPort ?: GlobalP2PMessagingManager.MESSAGING_PORT,
                        targetId = contact.id,
                        senderId = selfId,
                        lastSeenAt = now
                    )
                }
            }
        }
    }

    fun markPeerOnline(
        peerId: String,
        timestamp: Long = System.currentTimeMillis(),
        isSameWifi: Boolean = false,
        wifiSsid: String? = null,
        peerIp: String? = null
    ) {
        _peerPresenceMap.update { map ->
            val existing = map[peerId]
            val updated = existing?.copy(
                isOnline = true,
                isSameWifi = isSameWifi || (existing.isSameWifi && (timestamp - existing.lastHeartbeatReceivedAt < LAN_PRESENCE_TIMEOUT_MS)),
                wifiSsid = wifiSsid ?: existing.wifiSsid,
                lastSeenAt = timestamp,
                lastHeartbeatReceivedAt = timestamp
            ) ?: PeerPresence(
                peerId = peerId,
                isOnline = true,
                isSameWifi = isSameWifi,
                wifiSsid = wifiSsid,
                lastSeenAt = timestamp,
                lastHeartbeatReceivedAt = timestamp
            )
            map + (peerId to updated)
        }

        if (!peerIp.isNullOrBlank() && peerIp != "127.0.0.1") {
            onPeerIpDiscovered?.invoke(peerId, peerIp)
        }

        com.chat.app.telemetry.AppTelemetry.logOsbPresence(
            peerId = peerId,
            peerName = null,
            isOnline = true,
            lastSeen = timestamp,
            isSameWifi = isSameWifi,
            wifiSsid = wifiSsid,
            action = "ONLINE_DETECTED"
        )
    }

    fun markPeerOffline(peerId: String, lastSeenAt: Long = System.currentTimeMillis()) {
        _peerPresenceMap.update { map ->
            val existing = map[peerId]
            val updated = existing?.copy(
                isOnline = false,
                isSameWifi = false,
                lastSeenAt = lastSeenAt,
                lastHeartbeatReceivedAt = lastSeenAt
            ) ?: PeerPresence(
                peerId = peerId,
                isOnline = false,
                isSameWifi = false,
                lastSeenAt = lastSeenAt,
                lastHeartbeatReceivedAt = lastSeenAt
            )
            map + (peerId to updated)
        }
        com.chat.app.telemetry.AppTelemetry.logOsbPresence(
            peerId = peerId,
            peerName = null,
            isOnline = false,
            lastSeen = lastSeenAt,
            action = "OFFLINE_SIGNAL"
        )
    }

    fun seedPeerLastSeen(peerId: String, lastSeenAt: Long?) {
        if (lastSeenAt == null) return
        _peerPresenceMap.update { map ->
            if (!map.containsKey(peerId)) {
                map + (peerId to PeerPresence(
                    peerId = peerId,
                    isOnline = false,
                    isSameWifi = false,
                    lastSeenAt = lastSeenAt,
                    lastHeartbeatReceivedAt = 0L
                ))
            } else {
                map
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Humanized Presence & Last Seen Formatter
    // ─────────────────────────────────────────────────────────────────────────────

    fun formatPresenceStatus(presence: PeerPresence?): String {
        if (presence == null) return "Offline"
        if (presence.isOnline) {
            return if (presence.isSameWifi) "Online · Same Wi-Fi ⚡" else "Online"
        }
        val lastSeen = presence.lastSeenAt ?: return "Offline"
        val diffMs = System.currentTimeMillis() - lastSeen
        if (diffMs < 60_000L) return "Last seen just now"
        if (diffMs < 3_600_000L) return "Last seen ${diffMs / 60_000L}m ago"

        val msgCal = Calendar.getInstance().apply { timeInMillis = lastSeen }
        val nowCal = Calendar.getInstance()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

        return if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
        ) {
            "Last seen today at ${timeFmt.format(Date(lastSeen))}"
        } else if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) - 1
        ) {
            "Last seen yesterday at ${timeFmt.format(Date(lastSeen))}"
        } else {
            val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            "Last seen ${dateFmt.format(Date(lastSeen))}"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  OSB Protocol Network Transmitters
    // ─────────────────────────────────────────────────────────────────────────────

    fun sendPresencePing(
        targetIp: String,
        targetPort: Int,
        targetId: String,
        senderId: String,
        senderIp: String? = null,
        wifiSsid: String? = null,
        allowRelay: Boolean = true
    ) {
        scope.launch {
            try {
                val myLocalIp = senderIp ?: ProfileQrManager.getLocalIpAddress()
                val payload = JSONObject().apply {
                    put("type", "P2P_PRESENCE_PING")
                    put("senderId", senderId)
                    if (!myLocalIp.isNullOrBlank() && myLocalIp != "127.0.0.1") {
                        put("senderIp", myLocalIp)
                    }
                    if (!wifiSsid.isNullOrBlank()) {
                        put("wifiSsid", wifiSsid)
                    }
                    put("timestamp", System.currentTimeMillis())
                }
                dispatchPacket(targetIp, targetPort, targetId, payload.toString(), allowRelay = allowRelay)
            } catch (_: Exception) {}
        }
    }

    fun sendPresencePong(
        targetIp: String,
        targetPort: Int,
        targetId: String,
        senderId: String,
        isOnline: Boolean,
        lastSeenAt: Long,
        senderIp: String? = null,
        wifiSsid: String? = null,
        isSameWifi: Boolean = false
    ) {
        scope.launch {
            try {
                val myLocalIp = senderIp ?: ProfileQrManager.getLocalIpAddress()
                val payload = JSONObject().apply {
                    put("type", "P2P_PRESENCE_PONG")
                    put("senderId", senderId)
                    put("isOnline", isOnline)
                    put("lastSeenAt", lastSeenAt)
                    put("isSameWifi", isSameWifi)
                    if (!myLocalIp.isNullOrBlank() && myLocalIp != "127.0.0.1") {
                        put("senderIp", myLocalIp)
                    }
                    if (!wifiSsid.isNullOrBlank()) {
                        put("wifiSsid", wifiSsid)
                    }
                    put("timestamp", System.currentTimeMillis())
                }
                dispatchPacket(targetIp, targetPort, targetId, payload.toString())
            } catch (_: Exception) {}
        }
    }

    fun sendPresenceOfflinePacket(targetIp: String, targetPort: Int, targetId: String, senderId: String, lastSeenAt: Long) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "P2P_PRESENCE_OFFLINE")
                    put("senderId", senderId)
                    put("lastSeenAt", lastSeenAt)
                    put("timestamp", System.currentTimeMillis())
                }
                dispatchPacket(targetIp, targetPort, targetId, payload.toString())
            } catch (_: Exception) {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Internal Multi-Network Dispatcher (Direct TCP Socket + Web Relay Fallback)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun dispatchPacket(targetIp: String, targetPort: Int, targetId: String, payloadStr: String, allowRelay: Boolean = true) {
        val myLocalIp = ProfileQrManager.getLocalIpAddress()
        val isSameWifi = isSameSubnet(myLocalIp, targetIp)

        if (isSameWifi && targetIp.isNotBlank() && targetPort > 0) {
            val socket = GlobalP2PMessagingManager.connectSocketWithFallback(targetIp, targetPort, 1200)
            if (socket != null) {
                try {
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(payloadStr)
                    socket.close()
                    com.chat.app.telemetry.AppTelemetry.logNetworkTraffic("OUTBOUND", "P2P_TCP", "OSB_PRESENCE", "$targetIp:$targetPort", payloadStr.length.toLong())
                    return
                } catch (_: Exception) {
                    try { socket.close() } catch (_: Exception) {}
                }
            }
        }

        // Fast fallback to cross-network Web Relay only if allowed
        if (allowRelay && targetId.isNotBlank()) {
            GlobalP2PMessagingManager.sendPacketViaWebRelay(targetId, payloadStr)
            com.chat.app.telemetry.AppTelemetry.logNetworkTraffic("OUTBOUND", "WEB_RELAY", "OSB_PRESENCE", "relay_${targetId.take(6)}", payloadStr.length.toLong())
        }
    }
}
