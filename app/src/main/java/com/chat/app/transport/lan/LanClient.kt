package com.chat.app.transport.lan

import com.chat.app.core.logging.AppLog
import com.chat.app.transport.SendResult
import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.protocol.EnvelopeSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanClient @Inject constructor() {

    companion object {
        private const val TAG = "LanClient"
        private const val CONNECT_TIMEOUT_MS = 2500
        private const val SOCKET_TIMEOUT_MS = 3000
    }

    suspend fun sendEnvelope(
        envelope: Envelope,
        targetIp: String,
        targetPort: Int
    ): SendResult = withContext(Dispatchers.IO) {
        if (targetIp.isBlank() || targetPort <= 0) {
            return@withContext SendResult.Failure("Invalid LAN target address ($targetIp:$targetPort)", isRetriable = false)
        }

        val jsonPayload = EnvelopeSerializer.toJson(envelope)
        var socket: Socket? = null

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(targetIp, targetPort), CONNECT_TIMEOUT_MS)
            socket.soTimeout = SOCKET_TIMEOUT_MS

            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
            writer.println(jsonPayload)
            writer.flush()
            socket.close()

            AppLog.i(TAG, "Successfully delivered LAN Envelope ${AppLog.truncatedId(envelope.envelopeId)} to $targetIp:$targetPort")
            SendResult.Success
        } catch (e: Exception) {
            AppLog.w(TAG, "LAN transmission failed to $targetIp:$targetPort (${e.message})")
            SendResult.Failure("LAN socket transmission failed: ${e.message}", isRetriable = true)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }
}
