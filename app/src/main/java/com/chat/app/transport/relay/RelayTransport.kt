package com.chat.app.transport.relay

import com.chat.app.transport.SendResult
import com.chat.app.transport.Transport
import com.chat.app.transport.protocol.Envelope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelayTransport @Inject constructor(
    private val client: RelayClient,
    private val listener: RelayListener
) : Transport {

    override val name: String = "RELAY_SSE"

    override suspend fun send(
        envelope: Envelope,
        targetIp: String?,
        targetPort: Int?
    ): SendResult {
        return client.publishEnvelope(envelope, envelope.recipientId)
    }

    override fun incoming(): Flow<Envelope> = listener.incomingEnvelopes

    override suspend fun start() {
        listener.start()
    }

    override suspend fun stop() {
        listener.stop()
    }

    override fun isRunning(): Boolean = listener.isRunning()
}
