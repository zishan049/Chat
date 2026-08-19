package com.chat.app.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated In-App Backend API for:
 * Message Bubble Status (MBS) & Dual-Network Reliability (Wi-Fi & Cellular):
 * - Fast Delivery ACK with Debounced Batching Queue
 * - Read receipts
 * - Status sync probes and status reports
 * - Instant fallback between direct TCP socket and Web Relay
 */
object P2PMbsApiManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pending Delivery ACK batching queue: targetId -> List of AckItems
    private data class AckItem(
        val messageId: String,
        val targetIp: String,
        val targetPort: Int,
        val targetId: String,
        val chatId: String
    )

    private val pendingAcks = ConcurrentHashMap<String, MutableList<AckItem>>()
    private val ackBatchTrigger = Channel<Unit>(Channel.CONFLATED)

    init {
        // Background worker that drains and flushes ACK batches every 60ms
        scope.launch {
            while (isActive) {
                try {
                    delay(60L)
                    flushPendingAckBatches()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Queues a Delivery ACK for debounced batch dispatch.
     */
    fun sendDeliveryAck(targetIp: String, targetPort: Int, targetId: String, messageId: String, chatId: String) {
        if (targetId.isBlank() || messageId.isBlank()) return
        val list = pendingAcks.computeIfAbsent(targetId) { mutableListOf() }
        synchronized(list) {
            list.add(AckItem(messageId, targetIp, targetPort, targetId, chatId))
        }
        ackBatchTrigger.trySend(Unit)
    }

    private fun flushPendingAckBatches() {
        if (pendingAcks.isEmpty()) return

        for ((targetId, list) in pendingAcks) {
            val itemsToFlush = synchronized(list) {
                if (list.isEmpty()) {
                    emptyList()
                } else {
                    val copy = ArrayList(list)
                    list.clear()
                    copy
                }
            }
            if (itemsToFlush.isEmpty()) continue

            scope.launch {
                try {
                    val first = itemsToFlush.first()
                    if (itemsToFlush.size == 1) {
                        // Single ACK payload for backward compatibility
                        val payload = JSONObject().apply {
                            put("type", "P2P_DELIVERY_ACK")
                            put("messageId", first.messageId)
                            put("chatId", first.chatId)
                            put("timestamp", System.currentTimeMillis())
                        }
                        dispatchPacket(first.targetIp, first.targetPort, first.targetId, payload.toString())
                    } else {
                        // Batched ACK payload
                        val payload = JSONObject().apply {
                            put("type", "P2P_DELIVERY_ACK_BATCH")
                            put("messageIds", JSONArray(itemsToFlush.map { it.messageId }))
                            put("chatId", first.chatId)
                            put("timestamp", System.currentTimeMillis())
                        }
                        dispatchPacket(first.targetIp, first.targetPort, first.targetId, payload.toString())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Sends Read Receipt confirmation indicating messages up to timestamp have been seen.
     */
    fun sendReadReceipt(
        targetIp: String,
        targetPort: Int,
        targetId: String,
        senderId: String,
        chatId: String,
        readUpToTimestamp: Long = System.currentTimeMillis(),
        specificMessageIds: List<String> = emptyList()
    ) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "P2P_READ_RECEIPT")
                    put("senderId", senderId)
                    put("chatId", chatId)
                    put("readUpToTimestamp", readUpToTimestamp)
                    if (specificMessageIds.isNotEmpty()) {
                        put("messageIds", JSONArray(specificMessageIds))
                    }
                }
                dispatchPacket(targetIp, targetPort, targetId, payload.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Probes peer for the latest status of unacknowledged messages (recovering any dropped ACKs).
     */
    fun sendSyncStatusProbe(
        targetIp: String,
        targetPort: Int,
        targetId: String,
        chatId: String,
        senderId: String,
        messageIds: List<String>
    ) {
        if (messageIds.isEmpty()) return
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("type", "P2P_STATUS_PROBE")
                    put("chatId", chatId)
                    put("senderId", senderId)
                    put("messageIds", JSONArray(messageIds))
                }
                dispatchPacket(targetIp, targetPort, targetId, payload.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Responds to status probe with exact delivery/read state of each queried message.
     */
    fun sendSyncStatusReport(
        targetIp: String,
        targetPort: Int,
        targetId: String,
        chatId: String,
        senderId: String,
        statusMap: Map<String, String> // messageId -> "READ" or "DELIVERED"
    ) {
        if (statusMap.isEmpty()) return
        scope.launch {
            try {
                val statusObj = JSONObject()
                for ((id, status) in statusMap) {
                    statusObj.put(id, status)
                }
                val payload = JSONObject().apply {
                    put("type", "P2P_STATUS_REPORT")
                    put("chatId", chatId)
                    put("senderId", senderId)
                    put("statuses", statusObj)
                }
                dispatchPacket(targetIp, targetPort, targetId, payload.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Internal Multi-Network Dispatcher (Direct TCP Socket + Web Relay Fallback)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun dispatchPacket(targetIp: String, targetPort: Int, targetId: String, payloadStr: String, allowRelay: Boolean = true) {
        val myLocalIp = ProfileQrManager.getLocalIpAddress()
        val isSameWifi = P2POsbApiManager.isSameSubnet(myLocalIp, targetIp)

        if (isSameWifi && targetIp.isNotBlank() && targetPort > 0) {
            val socket = GlobalP2PMessagingManager.connectSocketWithFallback(targetIp, targetPort, 1200)
            if (socket != null) {
                try {
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(payloadStr)
                    socket.close()
                    com.chat.app.telemetry.AppTelemetry.logNetworkTraffic("OUTBOUND", "P2P_TCP", "STATUS_ACK", "$targetIp:$targetPort", payloadStr.length.toLong())
                    return
                } catch (_: Exception) {
                    try { socket.close() } catch (_: Exception) {}
                }
            }
        }

        // Fast fallback to cross-network Web Relay only if allowed
        if (allowRelay && targetId.isNotBlank()) {
            GlobalP2PMessagingManager.sendPacketViaWebRelay(targetId, payloadStr)
            com.chat.app.telemetry.AppTelemetry.logNetworkTraffic("OUTBOUND", "WEB_RELAY", "STATUS_ACK", "relay_${targetId.take(6)}", payloadStr.length.toLong())
        }
    }
}
