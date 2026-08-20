package com.chat.app.messaging.domain

import com.chat.app.core.network.PortProvider
import com.chat.app.core.common.AppError
import com.chat.app.core.common.Result
import com.chat.app.core.logging.AppLog
import com.chat.app.crypto.KeyManager
import com.chat.app.crypto.SessionManager
import com.chat.app.domain.model.Message
import com.chat.app.domain.model.MessageStatus
import com.chat.app.domain.repository.ContactRepository
import com.chat.app.domain.repository.MessageRepository
import com.chat.app.data.local.media.MediaFileManager
import com.chat.app.domain.model.Contact
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.pairing.domain.AcceptContactUseCase
import com.chat.app.pairing.domain.GenerateQrPayloadUseCase
import com.chat.app.pairing.domain.model.QrPayload
import com.chat.app.presence.HeartbeatManager
import com.chat.app.transport.SendResult
import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.protocol.PacketType
import com.chat.app.transport.routing.TransportRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates end-to-end cryptographic encryption and network dispatching for outgoing messages,
 * delivery acknowledgements (ACKs), read receipts, presence heartbeats, and peer pairing handshakes.
 */
@Singleton
class TransportDispatcher @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val identityRepository: IdentityRepository,
    private val sessionManager: SessionManager,
    private val keyManager: KeyManager,
    private val transportRouter: TransportRouter,
    private val receiveMessageUseCase: ReceiveMessageUseCase,
    private val acceptContactUseCase: AcceptContactUseCase,
    private val generateQrPayloadUseCase: GenerateQrPayloadUseCase,
    private val mediaFileManager: MediaFileManager,
    private val heartbeatManager: HeartbeatManager,
    private val portProvider: PortProvider
) {

    companion object {
        private const val TAG = "TransportDispatcher"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenerJob: Job? = null

    private val _pairingEvents = MutableSharedFlow<Contact>(extraBufferCapacity = 10)
    val pairingEvents: SharedFlow<Contact> = _pairingEvents.asSharedFlow()

    fun start() {
        if (listenerJob?.isActive == true) return

        scope.launch {
            transportRouter.start()
            heartbeatManager.start()
        }

        listenerJob = scope.launch {
            transportRouter.incomingEnvelopes().collect { envelope ->
                handleIncomingEnvelope(envelope)
            }
        }
        AppLog.d(TAG, "TransportDispatcher listening for inbound packets & heartbeats")
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
        scope.launch {
            heartbeatManager.stop()
            transportRouter.stop()
        }
    }

    /**
     * Encrypts and transmits an outgoing text message.
     */
    suspend fun dispatchMessage(message: Message): Result<Unit> = withContext(Dispatchers.IO) {
        val contact = contactRepository.getContact(message.conversationId)
            ?: return@withContext Result.Failure(AppError.Unknown("Recipient contact not found in database"))

        // 1. Mark as SENDING
        messageRepository.updateMessageStatus(message.id, MessageStatus.SENDING)

        // 2. Get or create CryptoSession for recipient
        val sessionResult = sessionManager.getOrCreateSession(contact.id)
        if (sessionResult is Result.Failure) {
            messageRepository.updateMessageStatus(message.id, MessageStatus.FAILED)
            return@withContext sessionResult
        }
        val session = (sessionResult as Result.Success).data

        // 3. Encrypt payload
        val plaintextBytes = message.text.toByteArray(Charsets.UTF_8)
        val encryptResult = session.encrypt(plaintextBytes)
        if (encryptResult is Result.Failure) {
            messageRepository.updateMessageStatus(message.id, MessageStatus.FAILED)
            return@withContext encryptResult
        }
        val ciphertextBytes = (encryptResult as Result.Success).data
        val ciphertextBase64 = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP)

        val selfPublicKey = keyManager.getPublicKeyBase64()

        val envelope = Envelope(
            messageId = message.id,
            senderId = message.senderId,
            recipientId = contact.id,
            type = PacketType.TEXT,
            timestamp = message.timestamp,
            ciphertextBase64 = ciphertextBase64,
            senderPublicKey = selfPublicKey
        )

        // 4. Dispatch over TransportRouter (LAN primary, Relay fallback)
        val sendResult = transportRouter.sendEnvelope(
            envelope = envelope,
            peerIp = contact.lastKnownIp,
            peerPort = contact.lastKnownPort
        )

        if (sendResult is SendResult.Success) {
            messageRepository.updateMessageStatus(message.id, MessageStatus.SENT)
            Result.Success(Unit)
        } else {
            messageRepository.updateMessageStatus(message.id, MessageStatus.FAILED)
            Result.Failure(AppError.TransportSendFailed((sendResult as SendResult.Failure).reason))
        }
    }

    /**
     * Retries sending a failed message.
     */
    fun retryMessage(messageId: String) {
        scope.launch {
            val message = messageRepository.getMessage(messageId) ?: return@launch
            if (message.status == MessageStatus.FAILED) {
                dispatchMessage(message)
            }
        }
    }

    /**
     * Sends Read Receipt envelope to the peer.
     */
    fun sendReadReceipt(conversationId: String) {
        scope.launch {
            val contact = contactRepository.getContact(conversationId) ?: return@launch
            val selfIdentityResult = identityRepository.getIdentity()
            val selfId = if (selfIdentityResult is Result.Success) selfIdentityResult.data.id else ""
            val selfPublicKey = keyManager.getPublicKeyBase64()

            val envelope = Envelope(
                messageId = UUID.randomUUID().toString(),
                senderId = selfId,
                recipientId = contact.id,
                type = PacketType.READ_RECEIPT,
                timestamp = System.currentTimeMillis(),
                senderPublicKey = selfPublicKey
            )

            transportRouter.sendEnvelope(
                envelope = envelope,
                peerIp = contact.lastKnownIp,
                peerPort = contact.lastKnownPort
            )
            AppLog.d(TAG, "Dispatched Read Receipt from $selfId to ${contact.displayName} (${contact.id})")
        }
    }

    private suspend fun sendDeliveryAck(toContactId: String, messageId: String) {
        scope.launch {
            val contact = contactRepository.getContact(toContactId) ?: return@launch
            val selfIdentityResult = identityRepository.getIdentity()
            val selfId = if (selfIdentityResult is Result.Success) selfIdentityResult.data.id else ""

            val ackEnvelope = Envelope(
                messageId = messageId,
                senderId = selfId,
                recipientId = contact.id,
                type = PacketType.DELIVERY_ACK,
                timestamp = System.currentTimeMillis()
            )
            transportRouter.sendEnvelope(
                envelope = ackEnvelope,
                peerIp = contact.lastKnownIp,
                peerPort = contact.lastKnownPort
            )
            AppLog.d(TAG, "Dispatched DeliveryAck for message ${AppLog.truncatedId(messageId)} from $selfId to ${contact.displayName}")
        }
    }

    private suspend fun handleIncomingEnvelope(envelope: Envelope) {
        try {
            when (envelope.type) {
            PacketType.TEXT -> {
                try {
                    val senderId = envelope.senderId

                    // 1. Get or create CryptoSession
                    val sessionResult = sessionManager.getOrCreateSession(senderId)
                    if (sessionResult is Result.Failure) {
                        AppLog.w(TAG, "Cannot decrypt incoming message ${AppLog.truncatedId(envelope.messageId)}: No session with $senderId")
                        return@handleIncomingEnvelope
                    }
                    val session = (sessionResult as Result.Success).data

                    // 2. Decrypt ciphertext payload
                    val rawCiphertext = try {
                        Base64.decode(envelope.ciphertextBase64, Base64.DEFAULT)
                    } catch (_: Exception) {
                        AppLog.w(TAG, "Malformed Base64 payload in envelope ${AppLog.truncatedId(envelope.envelopeId)}")
                        return@handleIncomingEnvelope
                    }

                    val decryptResult = session.decrypt(rawCiphertext)
                    if (decryptResult is Result.Failure) {
                        AppLog.e(TAG, "Decryption/Authentication tag failed for envelope ${AppLog.truncatedId(envelope.envelopeId)}. Packet discarded.")
                        return@handleIncomingEnvelope
                    }

                    val decryptedText = String((decryptResult as Result.Success).data, Charsets.UTF_8)

                    val isChatActive = (heartbeatManager.getActiveConversationPeer() == senderId)

                    // 3. Idempotently write to Room Database
                    receiveMessageUseCase(
                        messageId = envelope.messageId,
                        conversationId = senderId,
                        senderId = senderId,
                        text = decryptedText,
                        timestamp = envelope.timestamp,
                        isChatActive = isChatActive
                    )

                    // 4. Update contact last seen
                    contactRepository.updateLastSeen(senderId, envelope.timestamp)

                    // 5. Send DELIVERY_ACK back to sender
                    sendDeliveryAck(toContactId = senderId, messageId = envelope.messageId)

                    // 6. If user is actively inside this conversation, send READ_RECEIPT immediately
                    if (isChatActive) {
                        sendReadReceipt(senderId)
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error processing TEXT message", e)
                }
            }

            PacketType.DELIVERY_ACK -> {
                try {
                    AppLog.d(TAG, "Received DeliveryAck for message ${AppLog.truncatedId(envelope.messageId)}")
                    messageRepository.markOutgoingMessageAsDelivered(envelope.messageId)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error processing DELIVERY_ACK", e)
                }
            }

            PacketType.READ_RECEIPT -> {
                try {
                    AppLog.d(TAG, "Received ReadReceipt from ${envelope.senderId}")
                    messageRepository.markOutgoingMessagesAsRead(envelope.senderId)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error processing READ_RECEIPT", e)
                }
            }

            PacketType.PRESENCE_PING, PacketType.PRESENCE_PONG -> {
                try {
                    heartbeatManager.handlePresencePacket(envelope)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error processing PRESENCE packet", e)
                }
            }

            PacketType.PAIRING_HANDSHAKE -> {
                AppLog.i(TAG, "Received PAIRING_HANDSHAKE from sender ${envelope.senderId}")
                try {
                    val json = try { org.json.JSONObject(envelope.ciphertextBase64) } catch (_: Exception) { null }
                    val peerPayload = QrPayload.fromJson(envelope.ciphertextBase64)
                    if (peerPayload != null) {
                        val computedFingerprint = try {
                            val pubKeyBytes = Base64.decode(peerPayload.publicKeyBase64, Base64.DEFAULT)
                            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
                            digest.joinToString(":") { "%02X".format(it) }
                        } catch (_: Exception) {
                            peerPayload.fingerprint
                        }

                        // Decode and save peer's avatar transferred over network handshake
                        val avatarBase64 = json?.optString("av")?.takeIf { it.isNotBlank() }
                        val localAvatarPath = if (!avatarBase64.isNullOrBlank()) {
                            mediaFileManager.saveAvatarFromBase64(peerPayload.id, avatarBase64)
                        } else null

                        val contact = Contact(
                            id = peerPayload.id,
                            displayName = peerPayload.displayName,
                            avatarUri = localAvatarPath,
                            publicKeyBase64 = peerPayload.publicKeyBase64,
                            fingerprint = computedFingerprint.ifBlank { peerPayload.fingerprint },
                            isBlocked = false,
                            isVerified = true,
                            lastKnownIp = peerPayload.lanIp,
                            lastKnownPort = peerPayload.port,
                            lastSeenAt = System.currentTimeMillis(),
                            pairedAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )

                        val acceptResult = acceptContactUseCase(contact)
                        when (acceptResult) {
                            is Result.Success -> {
                                _pairingEvents.emit(contact)
                                AppLog.i(TAG, "Successfully processed PAIRING_HANDSHAKE and saved contact ${contact.displayName}")

                                // Send PAIRING_ACK back with self avatar
                                sendPairingAck(contact)
                            }
                            is Result.Failure -> {
                                AppLog.e(TAG, "Failed to accept contact from PAIRING_HANDSHAKE: ${acceptResult.error}")
                            }
                        }
                    } else {
                        AppLog.w(TAG, "PAIRING_HANDSHAKE payload parsing failed: invalid QR payload")
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error processing PAIRING_HANDSHAKE", e)
                }
            }

            PacketType.PAIRING_ACK -> {
                AppLog.i(TAG, "Received PAIRING_ACK from ${envelope.senderId}")
                try {
                    contactRepository.updateLastSeen(envelope.senderId, envelope.timestamp)
                    if (envelope.ciphertextBase64.isNotBlank()) {
                        val localAvatarPath = mediaFileManager.saveAvatarFromBase64(envelope.senderId, envelope.ciphertextBase64)
                        if (localAvatarPath != null) {
                            val existing = contactRepository.getContact(envelope.senderId)
                            if (existing != null) {
                                contactRepository.saveContact(existing.copy(avatarUri = localAvatarPath))
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.e(TAG, "Error processing PAIRING_ACK", e)
                }
            }

            else -> {
                AppLog.d(TAG, "Received packet ${envelope.type}")
            }
        }
    } catch (e: Exception) {
        AppLog.e(TAG, "Error handling incoming envelope", e)
    }
}

    /**
     * Dispatches a bi-directional pairing handshake containing local profile & public key to the peer device.
     */
    fun sendPairingHandshake(contact: Contact) {
        scope.launch {
            val selfIdentityResult = identityRepository.getIdentity()
            if (selfIdentityResult is Result.Success) {
                val self = selfIdentityResult.data
                val selfAvatarBase64 = mediaFileManager.getAvatarThumbnailBase64(self.avatarUri)
                val selfQrResult = generateQrPayloadUseCase()
                val baseJson = if (selfQrResult is Result.Success) {
                    selfQrResult.data
                } else {
                    QrPayload(
                        version = 1,
                        id = self.id,
                        displayName = self.displayName,
                        publicKeyBase64 = self.publicKeyBase64,
                        fingerprint = self.fingerprint,
                        port = portProvider.getActivePort()
                    ).toJson()
                }

                // Add avatarBase64 to network handshake payload
                val handshakeJson = try {
                    org.json.JSONObject(baseJson).apply {
                        if (!selfAvatarBase64.isNullOrBlank()) {
                            put("av", selfAvatarBase64)
                        }
                    }.toString()
                } catch (_: Exception) {
                    baseJson
                }

                val envelope = Envelope(
                    messageId = UUID.randomUUID().toString(),
                    senderId = self.id,
                    recipientId = contact.id,
                    type = PacketType.PAIRING_HANDSHAKE,
                    timestamp = System.currentTimeMillis(),
                    ciphertextBase64 = handshakeJson,
                    senderPublicKey = self.publicKeyBase64
                )

                val sendResult = transportRouter.sendEnvelope(
                    envelope = envelope,
                    peerIp = contact.lastKnownIp,
                    peerPort = contact.lastKnownPort
                )
                AppLog.i(TAG, "Pairing Handshake dispatched to ${contact.displayName} at ${contact.lastKnownIp}:${contact.lastKnownPort}, result=$sendResult")
            }
        }
    }

    private fun sendPairingAck(contact: Contact) {
        scope.launch {
            val selfIdentityResult = identityRepository.getIdentity()
            val (selfId, selfAvatar) = if (selfIdentityResult is Result.Success) {
                selfIdentityResult.data.id to selfIdentityResult.data.avatarUri
            } else "" to null

            val selfAvatarBase64 = mediaFileManager.getAvatarThumbnailBase64(selfAvatar)

            val ackEnvelope = Envelope(
                messageId = UUID.randomUUID().toString(),
                senderId = selfId,
                recipientId = contact.id,
                type = PacketType.PAIRING_ACK,
                timestamp = System.currentTimeMillis(),
                ciphertextBase64 = selfAvatarBase64 ?: ""
            )
            transportRouter.sendEnvelope(
                envelope = ackEnvelope,
                peerIp = contact.lastKnownIp,
                peerPort = contact.lastKnownPort
            )
            AppLog.d(TAG, "Dispatched PAIRING_ACK to ${contact.displayName}")
        }
    }
}
