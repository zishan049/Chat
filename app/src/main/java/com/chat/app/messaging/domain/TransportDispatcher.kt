package com.chat.app.messaging.domain

import android.util.Base64
import com.chat.app.core.common.AppError
import com.chat.app.core.common.Result
import com.chat.app.core.logging.AppLog
import com.chat.app.crypto.KeyManager
import com.chat.app.crypto.SessionManager
import com.chat.app.domain.model.Message
import com.chat.app.domain.model.MessageStatus
import com.chat.app.domain.repository.ContactRepository
import com.chat.app.domain.repository.MessageRepository
import com.chat.app.presence.HeartbeatManager
import com.chat.app.transport.SendResult
import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.protocol.PacketType
import com.chat.app.transport.routing.TransportRouter
import kotlinx.coroutines.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates end-to-end cryptographic encryption and network dispatching for outgoing messages,
 * delivery acknowledgements (ACKs), read receipts, and presence heartbeats.
 */
@Singleton
class TransportDispatcher @Inject constructor(
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val sessionManager: SessionManager,
    private val keyManager: KeyManager,
    private val transportRouter: TransportRouter,
    private val receiveMessageUseCase: ReceiveMessageUseCase,
    private val heartbeatManager: HeartbeatManager
) {

    companion object {
        private const val TAG = "TransportDispatcher"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listenerJob: Job? = null

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
        AppLog.i(TAG, "TransportDispatcher started with TransportRouter & HeartbeatManager")
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
        scope.launch {
            heartbeatManager.stop()
            transportRouter.stop()
        }
        AppLog.d(TAG, "TransportDispatcher stopped")
    }

    /**
     * Encrypts and transmits a local message to the peer.
     * HARD FAILURE POLICY: If encryption fails, the message is marked as FAILED and NEVER sent as plaintext.
     */
    suspend fun dispatchMessage(message: Message): Result<Unit> = withContext(Dispatchers.IO) {
        val contact = contactRepository.getContact(message.conversationId)
            ?: return@withContext Result.Failure(AppError.Unknown("Recipient contact not found in database"))

        // Update status to SENDING
        messageRepository.updateMessageStatus(message.id, MessageStatus.SENDING)

        // 1. Establish / retrieve active CryptoSession
        val sessionResult = sessionManager.getOrCreateSession(contact.id)
        if (sessionResult is Result.Failure) {
            AppLog.e(TAG, "Failed to establish CryptoSession for contact ${AppLog.truncatedId(contact.id)}")
            messageRepository.updateMessageStatus(message.id, MessageStatus.FAILED)
            return@withContext sessionResult
        }
        val session = (sessionResult as Result.Success).data

        // 2. Encrypt plaintext payload with AES-256-GCM
        val plaintextBytes = message.text.toByteArray(Charsets.UTF_8)
        val encryptResult = session.encrypt(plaintextBytes)
        if (encryptResult is Result.Failure) {
            AppLog.e(TAG, "AES-GCM encryption failed for message ${AppLog.truncatedId(message.id)}. Message marked FAILED.")
            messageRepository.updateMessageStatus(message.id, MessageStatus.FAILED)
            return@withContext encryptResult
        }
        val ciphertextBytes = (encryptResult as Result.Success).data
        val ciphertextBase64 = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP)

        // 3. Construct Envelope
        val envelope = Envelope(
            messageId = message.id,
            senderId = message.senderId,
            recipientId = contact.id,
            type = PacketType.TEXT,
            timestamp = message.timestamp,
            ciphertextBase64 = ciphertextBase64,
            senderPublicKey = keyManager.getPublicKeyBase64()
        )

        // 4. Dispatch over TransportRouter (LAN primary, Relay fallback)
        val sendResult = transportRouter.sendEnvelope(
            envelope = envelope,
            peerIp = contact.lastKnownIp,
            peerPort = contact.lastKnownPort
        )

        when (sendResult) {
            is SendResult.Success -> {
                AppLog.i(TAG, "Message ${AppLog.truncatedId(message.id)} dispatched successfully via router")
                messageRepository.updateMessageStatus(message.id, MessageStatus.SENT)
                Result.Success(Unit)
            }
            is SendResult.Failure -> {
                AppLog.w(TAG, "Message ${AppLog.truncatedId(message.id)} failed transport: ${sendResult.reason}")
                messageRepository.updateMessageStatus(message.id, MessageStatus.FAILED)
                Result.Failure(AppError.TransportSendFailed(sendResult.reason))
            }
        }
    }

    /**
     * Sends Read Receipt envelope to the peer.
     */
    fun sendReadReceipt(conversationId: String) {
        scope.launch {
            val contact = contactRepository.getContact(conversationId) ?: return@launch
            val selfPublicKey = keyManager.getPublicKeyBase64()

            val envelope = Envelope(
                messageId = UUID.randomUUID().toString(),
                senderId = conversationId,
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
            AppLog.d(TAG, "Dispatched Read Receipt to ${contact.displayName}")
        }
    }

    private suspend fun sendDeliveryAck(toContactId: String, messageId: String) {
        scope.launch {
            val contact = contactRepository.getContact(toContactId) ?: return@launch
            val ackEnvelope = Envelope(
                messageId = messageId,
                senderId = toContactId,
                recipientId = contact.id,
                type = PacketType.DELIVERY_ACK,
                timestamp = System.currentTimeMillis()
            )
            transportRouter.sendEnvelope(
                envelope = ackEnvelope,
                peerIp = contact.lastKnownIp,
                peerPort = contact.lastKnownPort
            )
            AppLog.d(TAG, "Dispatched DeliveryAck for message ${AppLog.truncatedId(messageId)} to ${contact.displayName}")
        }
    }

    private suspend fun handleIncomingEnvelope(envelope: Envelope) {
        when (envelope.type) {
            PacketType.TEXT -> {
                val senderId = envelope.senderId

                // 1. Get or create CryptoSession
                val sessionResult = sessionManager.getOrCreateSession(senderId)
                if (sessionResult is Result.Failure) {
                    AppLog.w(TAG, "Cannot decrypt incoming message ${AppLog.truncatedId(envelope.messageId)}: No session with $senderId")
                    return
                }
                val session = (sessionResult as Result.Success).data

                // 2. Decrypt ciphertext payload
                val rawCiphertext = try {
                    Base64.decode(envelope.ciphertextBase64, Base64.DEFAULT)
                } catch (_: Exception) {
                    AppLog.w(TAG, "Malformed Base64 payload in envelope ${AppLog.truncatedId(envelope.envelopeId)}")
                    return
                }

                val decryptResult = session.decrypt(rawCiphertext)
                if (decryptResult is Result.Failure) {
                    AppLog.e(TAG, "Decryption/Authentication tag failed for envelope ${AppLog.truncatedId(envelope.envelopeId)}. Packet discarded.")
                    return
                }

                val decryptedText = String((decryptResult as Result.Success).data, Charsets.UTF_8)

                // 3. Idempotently write to Room Database
                receiveMessageUseCase(
                    messageId = envelope.messageId,
                    conversationId = senderId,
                    senderId = senderId,
                    text = decryptedText,
                    timestamp = envelope.timestamp
                )

                // 4. Update contact last seen
                contactRepository.updateLastSeen(senderId, envelope.timestamp)

                // 5. Send DELIVERY_ACK back to sender
                sendDeliveryAck(toContactId = senderId, messageId = envelope.messageId)
            }

            PacketType.DELIVERY_ACK -> {
                AppLog.d(TAG, "Received DeliveryAck for message ${AppLog.truncatedId(envelope.messageId)}")
                messageRepository.markOutgoingMessageAsDelivered(envelope.messageId)
            }

            PacketType.READ_RECEIPT -> {
                AppLog.d(TAG, "Received ReadReceipt from ${envelope.senderId}")
                messageRepository.markOutgoingMessagesAsRead(envelope.senderId)
            }

            PacketType.PRESENCE_PING, PacketType.PRESENCE_PONG -> {
                heartbeatManager.handlePresencePacket(envelope)
            }

            else -> {
                AppLog.d(TAG, "Received packet ${envelope.type}")
            }
        }
    }
}
