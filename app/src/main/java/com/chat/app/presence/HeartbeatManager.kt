package com.chat.app.presence

import com.chat.app.core.logging.AppLog
import com.chat.app.domain.repository.ContactRepository
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.presence.domain.PresenceRepository
import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.protocol.PacketType
import com.chat.app.transport.routing.TransportRouter
import kotlinx.coroutines.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartbeatManager @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val contactRepository: ContactRepository,
    private val presenceRepository: PresenceRepository,
    private val transportRouter: TransportRouter
) {

    companion object {
        private const val TAG = "HeartbeatManager"
        private const val PING_INTERVAL_MS = 10000L
        private const val SWEEP_INTERVAL_MS = 12000L
        private const val OFFLINE_TIMEOUT_MS = 30000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null
    private var sweepJob: Job? = null

    private var activeChatPeerId: String? = null

    fun setActiveConversationPeer(peerId: String?) {
        activeChatPeerId = peerId
    }

    fun start() {
        if (pingJob?.isActive == true) return

        pingJob = scope.launch {
            while (isActive) {
                sendHeartbeats()
                delay(PING_INTERVAL_MS)
            }
        }

        sweepJob = scope.launch {
            while (isActive) {
                presenceRepository.sweepStalePresence(OFFLINE_TIMEOUT_MS)
                delay(SWEEP_INTERVAL_MS)
            }
        }
        AppLog.i(TAG, "HeartbeatManager started")
    }

    fun stop() {
        pingJob?.cancel()
        sweepJob?.cancel()
        pingJob = null
        sweepJob = null
        AppLog.d(TAG, "HeartbeatManager stopped")
    }

    private suspend fun sendHeartbeats() {
        val identity = (identityRepository.getIdentity() as? com.chat.app.core.common.Result.Success)?.data ?: return
        val peerId = activeChatPeerId
        if (peerId != null) {
            val contact = contactRepository.getContact(peerId) ?: return
            val pingEnvelope = Envelope(
                messageId = UUID.randomUUID().toString(),
                senderId = identity.id,
                recipientId = contact.id,
                type = PacketType.PRESENCE_PING,
                timestamp = System.currentTimeMillis()
            )
            transportRouter.sendEnvelope(
                envelope = pingEnvelope,
                peerIp = contact.lastKnownIp,
                peerPort = contact.lastKnownPort
            )
        }
    }

    suspend fun handlePresencePacket(envelope: Envelope) {
        when (envelope.type) {
            PacketType.PRESENCE_PING -> {
                presenceRepository.updatePresence(
                    peerId = envelope.senderId,
                    isOnline = true,
                    timestamp = envelope.timestamp
                )

                // Respond with PONG
                val identity = (identityRepository.getIdentity() as? com.chat.app.core.common.Result.Success)?.data ?: return
                val contact = contactRepository.getContact(envelope.senderId) ?: return
                val pongEnvelope = Envelope(
                    messageId = UUID.randomUUID().toString(),
                    senderId = identity.id,
                    recipientId = contact.id,
                    type = PacketType.PRESENCE_PONG,
                    timestamp = System.currentTimeMillis()
                )
                transportRouter.sendEnvelope(
                    envelope = pongEnvelope,
                    peerIp = contact.lastKnownIp,
                    peerPort = contact.lastKnownPort
                )
            }

            PacketType.PRESENCE_PONG -> {
                presenceRepository.updatePresence(
                    peerId = envelope.senderId,
                    isOnline = true,
                    timestamp = envelope.timestamp
                )
            }

            else -> {}
        }
    }
}
