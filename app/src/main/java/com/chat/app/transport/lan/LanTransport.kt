package com.chat.app.transport.lan

import com.chat.app.transport.SendResult
import com.chat.app.transport.Transport
import com.chat.app.transport.protocol.Envelope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanTransport @Inject constructor(
    private val server: LanServer,
    private val client: LanClient
) : Transport {

    override val name: String = "LAN_TCP"

    override suspend fun send(
        envelope: Envelope,
        targetIp: String?,
        targetPort: Int?
    ): SendResult {
        val ip = targetIp?.takeIf { it.isNotBlank() }
            ?: return SendResult.Failure("No LAN target IP provided", isRetriable = false)
        val port = targetPort?.takeIf { it > 0 } ?: LanServer.DEFAULT_PORT

        return client.sendEnvelope(envelope, ip, port)
    }

    override fun incoming(): Flow<Envelope> = server.incomingEnvelopes

    override suspend fun start() {
        server.start()
    }

    override suspend fun stop() {
        server.stop()
    }

    override fun isRunning(): Boolean = server.isRunning()
}
