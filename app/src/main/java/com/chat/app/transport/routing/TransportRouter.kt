package com.chat.app.transport.routing

import com.chat.app.core.logging.AppLog
import com.chat.app.transport.SendResult
import com.chat.app.transport.lan.LanTransport
import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.relay.RelayTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent Transport Router:
 * Selects the optimal network route (LAN TCP primary, Relay SSE fallback).
 * Prevents redundant dual-dispatch packet storms while guaranteeing delivery.
 */
@Singleton
class TransportRouter @Inject constructor(
    private val lanTransport: LanTransport,
    private val relayTransport: RelayTransport
) {

    companion object {
        private const val TAG = "TransportRouter"
    }

    suspend fun start() {
        lanTransport.start()
        relayTransport.start()
        AppLog.i(TAG, "TransportRouter started (LAN + Relay)")
    }

    suspend fun stop() {
        lanTransport.stop()
        relayTransport.stop()
        AppLog.d(TAG, "TransportRouter stopped")
    }

    /**
     * Dispatches an envelope using the most efficient available transport route.
     */
    suspend fun sendEnvelope(
        envelope: Envelope,
        peerIp: String? = null,
        peerPort: Int? = null
    ): SendResult {
        // 1. Attempt LAN direct socket if IP and port are known
        if (!peerIp.isNullOrBlank() && peerPort != null && peerPort > 0) {
            AppLog.d(TAG, "Attempting direct LAN transmission to $peerIp:$peerPort")
            val lanResult = lanTransport.send(envelope, peerIp, peerPort)
            if (lanResult is SendResult.Success) {
                AppLog.i(TAG, "Envelope ${AppLog.truncatedId(envelope.envelopeId)} delivered via LAN route")
                return lanResult
            }
            AppLog.d(TAG, "LAN delivery unavailable. Falling back cleanly to Relay route…")
        }

        // 2. Fallback to Cross-Network Web Relay
        val relayResult = relayTransport.send(envelope)
        if (relayResult is SendResult.Success) {
            AppLog.i(TAG, "Envelope ${AppLog.truncatedId(envelope.envelopeId)} delivered via Relay route")
        } else {
            AppLog.w(TAG, "Envelope delivery failed on all transport routes")
        }
        return relayResult
    }

    /**
     * Merged incoming packet stream from all active network transports.
     */
    fun incomingEnvelopes(): Flow<Envelope> {
        return merge(
            lanTransport.incoming(),
            relayTransport.incoming()
        )
    }
}
