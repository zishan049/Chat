package com.chat.app.transport.relay

import com.chat.app.core.logging.AppLog
import com.chat.app.transport.SendResult
import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.protocol.EnvelopeSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayClient @Inject constructor() {

    companion object {
        private const val TAG = "RelayClient"
        private const val BASE_RELAY_URL = "https://ntfy.sh"
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 10000
        private const val MAX_RETRIES = 3
    }

    suspend fun publishEnvelope(
        envelope: Envelope,
        recipientId: String
    ): SendResult = withContext(Dispatchers.IO) {
        val topic = TopicHasher.hashTopic(recipientId)
        val jsonPayload = EnvelopeSerializer.toJson(envelope)
        val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)

        if (payloadBytes.size > 4000) {
            AppLog.w(TAG, "Payload size ${payloadBytes.size} bytes exceeds relay single packet limit")
        }

        val url = URL("$BASE_RELAY_URL/$topic")
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            var conn: HttpURLConnection? = null
            try {
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                conn.setFixedLengthStreamingMode(payloadBytes.size)

                conn.outputStream.use { out ->
                    out.write(payloadBytes)
                    out.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    AppLog.i(TAG, "Successfully published Envelope ${AppLog.truncatedId(envelope.envelopeId)} to Relay topic $topic")
                    return@withContext SendResult.Success
                } else if (responseCode == 429) {
                    AppLog.w(TAG, "Relay HTTP 429 rate limit on attempt $attempt, backing off…")
                    delay(1000L * attempt)
                } else {
                    AppLog.w(TAG, "Relay HTTP error $responseCode on attempt $attempt")
                }
            } catch (e: Exception) {
                lastException = e
                AppLog.w(TAG, "Relay network failure on attempt $attempt: ${e.message}")
                if (attempt < MAX_RETRIES) {
                    delay(500L * attempt)
                }
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
        }

        SendResult.Failure(
            reason = "Relay publication failed after $MAX_RETRIES attempts: ${lastException?.message ?: "HTTP error"}",
            isRetriable = true
        )
    }
}
