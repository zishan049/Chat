package com.chat.app.transport.relay

import com.chat.app.core.logging.AppLog
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.protocol.EnvelopeSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayListener @Inject constructor(
    private val identityRepository: IdentityRepository
) {

    companion object {
        private const val TAG = "RelayListener"
        private const val BASE_RELAY_URL = "https://ntfy.sh"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenerJob: Job? = null

    private val _incomingEnvelopes = MutableSharedFlow<Envelope>(extraBufferCapacity = 100)
    val incomingEnvelopes: SharedFlow<Envelope> = _incomingEnvelopes.asSharedFlow()

    // LRU deduplication cache for relay event IDs
    private val processedEventIds = Collections.synchronizedSet(
        Collections.newSetFromMap(
            object : LinkedHashMap<String, Boolean>(200, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 400
                }
            }
        )
    )

    @Synchronized
    fun start() {
        if (listenerJob?.isActive == true) return

        listenerJob = scope.launch {
            var retryDelayMs = 1500L
            while (isActive) {
                val identity = (identityRepository.getIdentity() as? com.chat.app.core.common.Result.Success)?.data
                if (identity == null) {
                    delay(2000)
                    continue
                }

                val topic = TopicHasher.hashTopic(identity.id)
                var conn: HttpURLConnection? = null

                try {
                    val url = URL("$BASE_RELAY_URL/$topic/json?since=10m")
                    AppLog.i(TAG, "Opening SSE Relay stream on topic $topic")

                    conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 60000

                    if (conn.responseCode == 200) {
                        retryDelayMs = 1500L // Reset backoff on success
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                        var line: String?

                        while (isActive && reader.readLine().also { line = it } != null) {
                            line?.let { processLine(it) }
                        }
                    } else if (conn.responseCode == 429) {
                        AppLog.w(TAG, "Relay SSE stream 429 rate limit, backing off 6s")
                        retryDelayMs = 6000L
                    } else {
                        AppLog.w(TAG, "Relay SSE stream received response code ${conn.responseCode}")
                        retryDelayMs = minOf(retryDelayMs * 2, 20000L)
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        AppLog.w(TAG, "Relay SSE stream disconnected (${e.message}), reconnecting in ${retryDelayMs}ms…")
                        retryDelayMs = minOf(retryDelayMs * 2, 20000L)
                    }
                } finally {
                    try { conn?.disconnect() } catch (_: Exception) {}
                }

                delay(retryDelayMs)
            }
        }
    }

    @Synchronized
    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
        AppLog.d(TAG, "RelayListener stopped")
    }

    fun isRunning(): Boolean = listenerJob?.isActive == true

    private suspend fun processLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("{")) return

        try {
            val json = JSONObject(trimmed)
            val event = json.optString("event")
            if (event == "message") {
                val eventId = json.optString("id")
                val messageContent = json.optString("message")

                if (eventId.isNotBlank()) {
                    val alreadySeen = synchronized(processedEventIds) {
                        if (processedEventIds.contains(eventId)) true
                        else {
                            processedEventIds.add(eventId)
                            false
                        }
                    }
                    if (alreadySeen) return
                }

                if (messageContent.isNotBlank()) {
                    val envelope = EnvelopeSerializer.fromJson(messageContent)
                    if (envelope != null) {
                        AppLog.d(TAG, "Inbound Relay Envelope received: type=${envelope.type}, mid=${AppLog.truncatedId(envelope.messageId)}")
                        _incomingEnvelopes.emit(envelope)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
