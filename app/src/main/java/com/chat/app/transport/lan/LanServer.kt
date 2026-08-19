package com.chat.app.transport.lan

import com.chat.app.core.logging.AppLog
import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.protocol.EnvelopeSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanServer @Inject constructor() {

    companion object {
        private const val TAG = "LanServer"
        const val DEFAULT_PORT = 47832
    }

    private var serverSocket: ServerSocket? = null
    private var listeningJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _incomingEnvelopes = MutableSharedFlow<Envelope>(extraBufferCapacity = 100)
    val incomingEnvelopes: SharedFlow<Envelope> = _incomingEnvelopes.asSharedFlow()

    private var boundPort: Int = 0
    val activePort: Int get() = boundPort

    @Synchronized
    fun start(port: Int = DEFAULT_PORT): Int {
        if (serverSocket != null && serverSocket?.isClosed == false && boundPort > 0) {
            return boundPort
        }
        stop()

        try {
            val server = try {
                ServerSocket(port)
            } catch (_: Exception) {
                ServerSocket(0) // Fallback to available ephemeral port
            }
            serverSocket = server
            boundPort = server.localPort

            listeningJob = scope.launch {
                AppLog.i(TAG, "LAN TCP Server listening on port $boundPort")
                while (isActive && !server.isClosed) {
                    try {
                        val client = server.accept()
                        launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (!server.isClosed) {
                            AppLog.w(TAG, "ServerSocket accept exception", e)
                        }
                    }
                }
            }
            return boundPort
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to bind LAN ServerSocket", e)
            boundPort = 0
            return 0
        }
    }

    @Synchronized
    fun stop() {
        listeningJob?.cancel()
        listeningJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        boundPort = 0
        AppLog.d(TAG, "LAN TCP Server stopped")
    }

    fun isRunning(): Boolean = serverSocket != null && serverSocket?.isClosed == false

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val line = reader.readLine()
            socket.close()

            if (!line.isNullOrBlank()) {
                val envelope = EnvelopeSerializer.fromJson(line)
                if (envelope != null) {
                    AppLog.d(TAG, "Inbound LAN Envelope received: type=${envelope.type}, mid=${AppLog.truncatedId(envelope.messageId)}")
                    _incomingEnvelopes.emit(envelope)
                } else {
                    AppLog.w(TAG, "Received unparseable LAN payload: ${line.take(40)}…")
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Error handling client socket", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
