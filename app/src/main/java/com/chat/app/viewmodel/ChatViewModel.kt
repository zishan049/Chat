package com.chat.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.graphics.Color
import com.chat.app.MainActivity
import java.io.File
import java.util.UUID

data class InAppNotification(
    val chatId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val storageManager = StorageManager.getInstance(application)

    // ── Chats list ─────────────────────────────────────────────────────────────
    val chats: StateFlow<List<Chat>> = storageManager.chats
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Active chat messages ───────────────────────────────────────────────────
    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    val messages: StateFlow<List<Message>> = _activeChatId
        .flatMapLatest { id -> if (id != null) storageManager.getMessagesForChat(id) else flowOf(emptyList()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── App Settings ──────────────────────────────────────────────
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(
        if (prefs.contains("is_dark_mode")) {
            storageManager.isDarkMode()
        } else {
            true
        }
    )
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isHaptics = MutableStateFlow(storageManager.isHapticsEnabled())
    val isHaptics: StateFlow<Boolean> = _isHaptics.asStateFlow()

    private val _isSound = MutableStateFlow(storageManager.isSoundEnabled())
    val isSound: StateFlow<Boolean> = _isSound.asStateFlow()

    fun setSound(enabled: Boolean) {
        storageManager.setSoundEnabled(enabled)
        _isSound.value = enabled
    }

    private val _isAutoDownload = MutableStateFlow(storageManager.isAutoDownloadMedia())
    val isAutoDownload: StateFlow<Boolean> = _isAutoDownload.asStateFlow()

    fun setAutoDownloadMedia(enabled: Boolean) {
        storageManager.setAutoDownloadMedia(enabled)
        _isAutoDownload.value = enabled
    }

    private val _isMessagePreview = MutableStateFlow(storageManager.isMessagePreviewEnabled())
    val isMessagePreview: StateFlow<Boolean> = _isMessagePreview.asStateFlow()

    fun setMessagePreview(enabled: Boolean) {
        storageManager.setMessagePreviewEnabled(enabled)
        _isMessagePreview.value = enabled
    }

    // ── Onboarding State ────────────────────────────────────────────────────────
    private val _isOnboardingCompleted = MutableStateFlow(storageManager.isOnboardingCompleted())
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    // ── Self profile ───────────────────────────────────────────────────────────
    private val _selfProfile = MutableStateFlow<Profile?>(null)
    val selfProfile: StateFlow<Profile?> = _selfProfile.asStateFlow()

    // ── Contacts ───────────────────────────────────────────────────────────────
    val contacts: StateFlow<List<Profile>> = storageManager.contacts
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Storage stats ──────────────────────────────────────────────────────────
    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private val _storageBreakdown = MutableStateFlow(MediaStorageBreakdown())
    val storageBreakdown: StateFlow<MediaStorageBreakdown> = _storageBreakdown.asStateFlow()

    /**
     * Debounce trigger for storage stats refresh.
     * All refreshStorage() calls emit here; the collector debounces to at most
     * once per 800 ms, eliminating redundant back-to-back filesystem walks.
     */
    private val _refreshStorageTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // ── Memory stats (RAM + bitmap cache) — provided by AppMemoryManager ───────
    val memoryStats: StateFlow<com.chat.app.utils.MemoryStats> = com.chat.app.utils.AppMemoryManager.memoryStats

    // ── In-app Notifications ──────────────────────────────────────────────────
    private val _incomingNotification = MutableSharedFlow<InAppNotification>()
    val incomingNotification = _incomingNotification.asSharedFlow()

    // ── Typing States ─────────────────────────────────────────────────────────
    private val _isPeerTyping = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isPeerTyping: StateFlow<Map<String, Boolean>> = _isPeerTyping.asStateFlow()

    // ── Peer Online Status Badge (OSB) Presence State ─────────────────────────
    val peerPresence: StateFlow<Map<String, com.chat.app.utils.PeerPresence>> =
        com.chat.app.utils.P2POsbApiManager.peerPresenceMap

    // ── Media messages ────────────────────────────────────────────────────────
    val allMediaMessages: StateFlow<List<Message>> = storageManager.allMediaMessages
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun getMediaMessagesForChat(chatId: String): Flow<List<Message>> =
        storageManager.getMediaMessagesForChat(chatId)

    // ── Scanned Peer Profile Modal State ──────────────────────────────────────
    private val _scannedPeerProfile = MutableStateFlow<com.chat.app.utils.ScannedProfileData?>(null)
    val scannedPeerProfile: StateFlow<com.chat.app.utils.ScannedProfileData?> = _scannedPeerProfile.asStateFlow()

    // Track IDs of contacts that were added during the current scan session so the
    // localBroadcastFlow / onProfileReceived callbacks can correctly show the modal
    // instead of emitting a "profile updated" notification.
    private val _recentlyScannedIds = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun clearScannedPeerProfile() {
        _scannedPeerProfile.value = null
    }

    fun onQrScanned(scannedData: com.chat.app.utils.ScannedProfileData) {
        viewModelScope.launch {
            // Derive shared E2EE symmetric key if peer public key is provided
            if (!scannedData.publicKey.isNullOrBlank()) {
                com.chat.app.utils.CryptoUtils.deriveSharedKeyForContact(scannedData.id, scannedData.publicKey)
                logP2P("[E2EE Handshake] Derived mutual AES-256-GCM secret key for peer ${scannedData.id}. Verified=${scannedData.isVerified}")
            }

            // Immediately mark peer as online since their QR was just actively scanned
            com.chat.app.utils.P2POsbApiManager.markPeerOnline(scannedData.id, peerIp = scannedData.ip)

            // 1. Save contact with IP & Port and mark it as freshly-scanned so the
            //    ACK callback can distinguish a new scan from a background profile update.
            _recentlyScannedIds[scannedData.id] = System.currentTimeMillis()
            addContact(
                scannedData.id,
                scannedData.name,
                scannedData.avatarUri,
                scannedData.age,
                scannedData.description,
                scannedData.ip,
                scannedData.port
            )

            // 2. Present profile modal on this scanner device immediately
            _scannedPeerProfile.value = scannedData

            // 2b. Once addContact finishes generating the avatar, refresh the modal
            //     so the user sees a proper profile image instead of a blank circle.
            launch {
                var attempts = 0
                while (attempts < 20) {
                    delay(150)
                    attempts++
                    val saved = storageManager.getContactById(scannedData.id)
                    if (saved != null && !saved.avatarUri.isNullOrBlank()) {
                        _scannedPeerProfile.value = scannedData.copy(avatarUri = saved.avatarUri)
                        break
                    }
                }
            }

            // 3. Transmit self profile back to peer host over P2P socket / Web Relay.
            //    Wait up to 2 s for selfProfile to be available (it may still be loading on first launch).
            val app = getApplication<Application>()
            if (scannedData.id.isNotBlank()) {
                var self = selfProfile.value
                if (self == null) {
                    // Poll briefly — profile is typically loaded within milliseconds
                    var waited = 0
                    while (self == null && waited < 2000) {
                        delay(100)
                        waited += 100
                        self = selfProfile.value
                    }
                }
                if (self != null) {
                    com.chat.app.utils.P2PQrExchangeManager.sendProfileToPeer(
                        context = app,
                        ip = scannedData.ip ?: "",
                        port = scannedData.port ?: 0,
                        selfProfile = self,
                        peerContactId = scannedData.id
                    ) { success, peerProfileWithAvatar ->
                        logP2P("[P2P QR Exchange] Profile payload sent to host ${scannedData.id} (${scannedData.ip}:${scannedData.port}). Success=$success")
                        if (peerProfileWithAvatar != null) {
                            addContact(
                                peerProfileWithAvatar.id,
                                peerProfileWithAvatar.name,
                                peerProfileWithAvatar.avatarUri ?: scannedData.avatarUri,
                                peerProfileWithAvatar.age ?: scannedData.age,
                                peerProfileWithAvatar.description ?: scannedData.description,
                                peerProfileWithAvatar.ip ?: scannedData.ip,
                                peerProfileWithAvatar.port ?: scannedData.port
                            )
                            // Update the modal with the enriched host profile data
                            _scannedPeerProfile.value = peerProfileWithAvatar
                        }
                    }
                } else {
                    logP2P("[P2P QR Exchange] Self profile unavailable – skipped sending profile to ${scannedData.id}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _selfProfile.value?.let { self ->
            com.chat.app.utils.P2POsbApiManager.sendPresenceOffline(self.id, contacts.value)
        }
        com.chat.app.utils.P2POsbApiManager.stopPresenceHeartbeat()
        com.chat.app.utils.AppMemoryManager.cancelIdleSweep()
    }

    init {
        // Eagerly load/persist device EC identity KeyPair
        com.chat.app.utils.CryptoUtils.init(getApplication())

        viewModelScope.launch {
            // Only seed self profile if onboarding was already completed
            val self = storageManager.getSelfProfile()
            if (self != null) {
                _selfProfile.value = self
            } else if (storageManager.isOnboardingCompleted()) {
                val newSelf = storageManager.updateSelfProfile("User")
                _selfProfile.value = newSelf
            }
            refreshStorage()
            _selfProfile.value?.id?.let {
                com.chat.app.utils.GlobalP2PMessagingManager.restartWebRelayListener(getApplication()) { _selfProfile.value?.id }
            }
        }

        // Start persistent TCP listener & cross-network Web Relay listener for incoming P2P chat messages
        com.chat.app.utils.GlobalP2PMessagingManager.startMessagingListener(getApplication())
        com.chat.app.utils.GlobalP2PMessagingManager.startWebRelayListener(getApplication()) { _selfProfile.value?.id }

        // Register instant zero-delay network transition monitor
        com.chat.app.utils.P2POsbApiManager.registerNetworkMonitor(getApplication()) {
            _selfProfile.value?.id?.let {
                com.chat.app.utils.GlobalP2PMessagingManager.restartWebRelayListener(getApplication()) { _selfProfile.value?.id }
            }
        }

        // Listen for dynamic IP roaming from peer packets and update contact records
        com.chat.app.utils.P2POsbApiManager.onPeerIpDiscovered = { peerId, newIp ->
            viewModelScope.launch(Dispatchers.IO) {
                val contact = storageManager.getContactById(peerId)
                if (contact != null && contact.lastKnownIp != newIp) {
                    storageManager.saveContact(contact.copy(lastKnownIp = newIp))
                }
            }
        }

        // Start OSB Presence Heartbeat & Sweeper with Active Chat priority tier
        com.chat.app.utils.P2POsbApiManager.startPresenceHeartbeat(
            selfProfileProvider = { _selfProfile.value },
            contactsProvider = { storageManager.getAllContactsList() },
            activeChatIdProvider = { _activeChatId.value },
            onLastSeenPersist = { peerId, lastSeenAt ->
                viewModelScope.launch {
                    storageManager.updateContactLastSeen(peerId, lastSeenAt)
                }
            }
        )

        // Seed initial persisted lastSeenAt timestamps into P2POsbApiManager
        viewModelScope.launch {
            contacts.collect { list ->
                list.forEach { contact ->
                    com.chat.app.utils.P2POsbApiManager.seedPeerLastSeen(contact.id, contact.lastSeenAt)
                }
            }
        }

        // Auto-flush pending/failed messages when a peer transitions to Online
        viewModelScope.launch(Dispatchers.IO) {
            var previousPresenceMap = emptyMap<String, com.chat.app.utils.PeerPresence>()
            peerPresence.collect { currentMap ->
                for ((peerId, presence) in currentMap) {
                    val wasOnline = previousPresenceMap[peerId]?.isOnline == true
                    if (presence.isOnline && !wasOnline) {
                        val pending = storageManager.getPendingMessagesForChat(peerId)
                        for (msg in pending) {
                            retryFailedMessage(msg)
                        }
                    }
                }
                previousPresenceMap = currentMap
            }
        }

        // Start background memory manager idle sweep
        com.chat.app.utils.AppMemoryManager.startIdleSweep(storageManager)
        com.chat.app.utils.AppMemoryManager.refreshStats()

        // Debounced storage stats refresh — coalesces burst calls to at most once per 800 ms
        viewModelScope.launch {
            _refreshStorageTrigger
                .debounce(800L)
                .collect {
                    _storageBytes.value = storageManager.getTotalStorageBytes()
                    _storageBreakdown.value = storageManager.getStorageBreakdown()
                }
        }

        // Start persistent P2P global app listener for incoming profile updates & exchange
        viewModelScope.launch {
            com.chat.app.utils.P2PQrExchangeManager.startListener(
                context = getApplication(),
                selfProfileProvider = { _selfProfile.value },
                onProfileReceived = { peerData ->
                    viewModelScope.launch {
                        com.chat.app.utils.P2POsbApiManager.markPeerOnline(peerData.id, peerIp = peerData.ip)
                        val existingContact = storageManager.getContactById(peerData.id)
                        addContact(
                            peerData.id,
                            peerData.name,
                            peerData.avatarUri,
                            peerData.age,
                            peerData.description,
                            peerData.ip,
                            peerData.port
                        )

                        // Show the modal if this is a brand-new contact OR if it was
                        // freshly scanned in this session (host-side: peer just scanned us).
                        val scannedAt = _recentlyScannedIds[peerData.id]
                        val isFreshScan = scannedAt != null && (System.currentTimeMillis() - scannedAt) < 30_000L

                        if (isFreshScan || existingContact == null) {
                            _scannedPeerProfile.value = peerData
                        } else {
                            _incomingNotification.emit(
                                InAppNotification(
                                    chatId = peerData.id,
                                    senderName = peerData.name,
                                    text = "✨ ${peerData.name} updated their profile!"
                                )
                            )
                        }
                    }
                }
            )
        }

        // Listen for P2P profile broadcast signals (from socket or local fallback)
        viewModelScope.launch {
            com.chat.app.utils.P2PQrExchangeManager.localBroadcastFlow.collect { peerData ->
                val self = _selfProfile.value
                if (self != null && peerData.id != self.id) {
                    com.chat.app.utils.P2POsbApiManager.markPeerOnline(peerData.id, peerIp = peerData.ip)
                    val existingContact = storageManager.getContactById(peerData.id)
                    addContact(
                        peerData.id,
                        peerData.name,
                        peerData.avatarUri,
                        peerData.age,
                        peerData.description,
                        peerData.ip,
                        peerData.port
                    )

                    // If this peer was freshly scanned (within the last 30 s) always show / refresh
                    // the modal instead of a notification — the ACK enriches the displayed data.
                    val scannedAt = _recentlyScannedIds[peerData.id]
                    val isFreshScan = scannedAt != null && (System.currentTimeMillis() - scannedAt) < 30_000L

                    if (isFreshScan || existingContact == null) {
                        _scannedPeerProfile.value = peerData
                    } else {
                        _incomingNotification.emit(
                            InAppNotification(
                                chatId = peerData.id,
                                senderName = peerData.name,
                                text = "✨ ${peerData.name} updated their profile!"
                            )
                        )
                    }
                }
            }
        }

        // Listen for incoming P2P messaging packets (text, media chunks, typing, ACKs, read receipts, presence, probes)
        viewModelScope.launch {
            com.chat.app.utils.GlobalP2PMessagingManager.incomingPacketFlow.collect { packet ->
                when (packet) {
                    is com.chat.app.utils.P2PPacket.TextMessage -> {
                        val senderContact = storageManager.getContactById(packet.senderId)
                        val senderName = senderContact?.displayName ?: "User ${packet.senderId.take(4)}"
                        
                        if (senderContact == null) {
                            addContact(
                                id = packet.senderId,
                                username = senderName
                            )
                        }

                        val msg = storageManager.sendTextMessage(
                            chatId = packet.senderId,
                            senderId = packet.senderId,
                            text = packet.text,
                            isMine = false,
                            status = MessageStatus.DELIVERED,
                            timestamp = packet.timestamp,
                            messageId = packet.messageId
                        )
                        refreshStorage()

                        // Mark peer online on activity
                        com.chat.app.utils.P2POsbApiManager.markPeerOnline(packet.senderId, packet.timestamp)

                        // Send Delivery ACK back to peer via dedicated MBS API
                        val selfId = _selfProfile.value?.id ?: ""
                        com.chat.app.utils.P2PMbsApiManager.sendDeliveryAck(
                            targetIp = senderContact?.lastKnownIp ?: "",
                            targetPort = senderContact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                            targetId = packet.senderId,
                            messageId = packet.messageId,
                            chatId = selfId
                        )

                        // Emit in-app notification if not actively in this chat
                        if (_activeChatId.value != packet.senderId) {
                            _incomingNotification.emit(
                                InAppNotification(
                                    chatId = packet.senderId,
                                    senderName = senderName,
                                    text = packet.text
                                )
                            )
                        } else {
                            // If user is actively viewing this chat, auto-send read receipt via dedicated MBS API
                            com.chat.app.utils.P2PMbsApiManager.sendReadReceipt(
                                targetIp = senderContact?.lastKnownIp ?: "",
                                targetPort = senderContact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                                targetId = packet.senderId,
                                senderId = selfId,
                                chatId = packet.senderId,
                                readUpToTimestamp = System.currentTimeMillis()
                            )
                        }
                    }

                    is com.chat.app.utils.P2PPacket.MediaChunk -> {
                        // Only assemble & save once ALL chunks have arrived (on the last chunk)
                        if (packet.chunkIndex == packet.totalChunks - 1) {
                            val appCtx = getApplication<Application>()
                            val assembledPath = com.chat.app.utils.GlobalP2PMessagingManager.assembleAndSaveMediaFile(
                                context = appCtx,
                                messageId = packet.messageId,
                                mediaType = packet.mediaType,
                                originalFileName = packet.fileName
                            )

                            if (assembledPath != null) {
                                val senderContact = storageManager.getContactById(packet.senderId)
                                val senderName = senderContact?.displayName ?: "Contact"
                                val uri = Uri.fromFile(File(assembledPath))

                                storageManager.sendMediaMessage(
                                    chatId = packet.senderId,
                                    senderId = packet.senderId,
                                    uri = uri,
                                    mediaType = packet.mediaType,
                                    originalFileName = packet.fileName,
                                    isMine = false,
                                    status = MessageStatus.DELIVERED,
                                    initialProgress = 1.0f,
                                    messageId = packet.messageId
                                )
                                refreshStorage()

                                // Mark peer online
                                com.chat.app.utils.P2POsbApiManager.markPeerOnline(packet.senderId, packet.timestamp)

                                // Send Delivery ACK back to peer via dedicated MBS API
                                val selfId = _selfProfile.value?.id ?: ""
                                com.chat.app.utils.P2PMbsApiManager.sendDeliveryAck(
                                    targetIp = senderContact?.lastKnownIp ?: "",
                                    targetPort = senderContact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                                    targetId = packet.senderId,
                                    messageId = packet.messageId,
                                    chatId = selfId
                                )

                                if (_activeChatId.value != packet.senderId) {
                                    _incomingNotification.emit(
                                        InAppNotification(
                                            chatId = packet.senderId,
                                            senderName = senderName,
                                            text = "Sent a ${packet.mediaType.name.lowercase()} attachment"
                                        )
                                    )
                                } else {
                                    com.chat.app.utils.P2PMbsApiManager.sendReadReceipt(
                                        targetIp = senderContact?.lastKnownIp ?: "",
                                        targetPort = senderContact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                                        targetId = packet.senderId,
                                        senderId = selfId,
                                        chatId = packet.senderId,
                                        readUpToTimestamp = System.currentTimeMillis()
                                    )
                                }
                            }
                        }
                    }

                    is com.chat.app.utils.P2PPacket.DeliveryAck -> {
                        viewModelScope.launch {
                            storageManager.updateMessageStatus(packet.messageId, MessageStatus.DELIVERED)
                            val myLocalIp = com.chat.app.utils.ProfileQrManager.getLocalIpAddress()
                            val contact = storageManager.getContactById(packet.chatId)
                            val isSameWifi = com.chat.app.utils.P2POsbApiManager.isSameSubnet(myLocalIp, contact?.lastKnownIp)
                            com.chat.app.utils.P2POsbApiManager.markPeerOnline(packet.chatId, packet.timestamp, isSameWifi = isSameWifi)
                            val tripMs = System.currentTimeMillis() - packet.timestamp
                            com.chat.app.telemetry.AppTelemetry.logMbsAck(
                                messageId = packet.messageId,
                                chatId = packet.chatId,
                                ackType = "DELIVERY_ACK",
                                latencyMs = if (tripMs in 1..60000) tripMs else null
                            )
                        }
                    }

                    is com.chat.app.utils.P2PPacket.DeliveryAckBatch -> {
                        viewModelScope.launch {
                            for (msgId in packet.messageIds) {
                                storageManager.updateMessageStatus(msgId, MessageStatus.DELIVERED)
                            }
                            val myLocalIp = com.chat.app.utils.ProfileQrManager.getLocalIpAddress()
                            val contact = storageManager.getContactById(packet.chatId)
                            val isSameWifi = com.chat.app.utils.P2POsbApiManager.isSameSubnet(myLocalIp, contact?.lastKnownIp)
                            com.chat.app.utils.P2POsbApiManager.markPeerOnline(packet.chatId, packet.timestamp, isSameWifi = isSameWifi)
                            refreshStorage()
                            com.chat.app.telemetry.AppTelemetry.logMbsAck(
                                messageId = "batch_${packet.messageIds.size}",
                                chatId = packet.chatId,
                                ackType = "DELIVERY_ACK_BATCH",
                                latencyMs = System.currentTimeMillis() - packet.timestamp
                            )
                        }
                    }

                    is com.chat.app.utils.P2PPacket.ReadReceipt -> {
                        viewModelScope.launch {
                            val targetChatId = if (storageManager.getContactById(packet.senderId) != null) packet.senderId else packet.chatId
                            storageManager.markSentMessagesAsRead(targetChatId)
                            val myLocalIp = com.chat.app.utils.ProfileQrManager.getLocalIpAddress()
                            val contact = storageManager.getContactById(packet.senderId)
                            val isSameWifi = com.chat.app.utils.P2POsbApiManager.isSameSubnet(myLocalIp, contact?.lastKnownIp)
                            com.chat.app.utils.P2POsbApiManager.markPeerOnline(packet.senderId, packet.readUpToTimestamp, isSameWifi = isSameWifi)
                            com.chat.app.telemetry.AppTelemetry.logMbsAck(
                                messageId = "all_in_chat",
                                chatId = targetChatId,
                                ackType = "READ_RECEIPT",
                                latencyMs = System.currentTimeMillis() - packet.readUpToTimestamp
                            )
                        }
                    }

                    is com.chat.app.utils.P2PPacket.TypingIndicator -> {
                        _isPeerTyping.update { map ->
                            map + (packet.chatId to packet.isTyping)
                        }
                        val myLocalIp = com.chat.app.utils.ProfileQrManager.getLocalIpAddress()
                        val contact = storageManager.getContactById(packet.senderId)
                        val isSameWifi = com.chat.app.utils.P2POsbApiManager.isSameSubnet(myLocalIp, contact?.lastKnownIp)
                        com.chat.app.utils.P2POsbApiManager.markPeerOnline(packet.senderId, isSameWifi = isSameWifi)
                    }

                    is com.chat.app.utils.P2PPacket.PresencePing -> {
                        val myLocalIp = com.chat.app.utils.ProfileQrManager.getLocalIpAddress()
                        val contact = storageManager.getContactById(packet.senderId)
                        val peerEffectiveIp = packet.senderIp ?: contact?.lastKnownIp
                        val isSameWifi = com.chat.app.utils.P2POsbApiManager.isSameSubnet(myLocalIp, peerEffectiveIp)
                        
                        com.chat.app.utils.P2POsbApiManager.markPeerOnline(
                            peerId = packet.senderId,
                            timestamp = packet.timestamp,
                            isSameWifi = isSameWifi,
                            wifiSsid = packet.wifiSsid,
                            peerIp = peerEffectiveIp
                        )
                        // Send Pong back
                        val selfId = _selfProfile.value?.id ?: ""
                        com.chat.app.utils.P2POsbApiManager.sendPresencePong(
                            targetIp = contact?.lastKnownIp ?: "",
                            targetPort = contact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                            targetId = packet.senderId,
                            senderId = selfId,
                            isOnline = true,
                            lastSeenAt = System.currentTimeMillis(),
                            senderIp = myLocalIp,
                            wifiSsid = packet.wifiSsid,
                            isSameWifi = isSameWifi
                        )
                    }

                    is com.chat.app.utils.P2PPacket.PresencePong -> {
                        val myLocalIp = com.chat.app.utils.ProfileQrManager.getLocalIpAddress()
                        val contact = storageManager.getContactById(packet.senderId)
                        val peerEffectiveIp = packet.senderIp ?: contact?.lastKnownIp
                        val isSameWifi = packet.isSameWifi || com.chat.app.utils.P2POsbApiManager.isSameSubnet(myLocalIp, peerEffectiveIp)

                        if (packet.isOnline) {
                            com.chat.app.utils.P2POsbApiManager.markPeerOnline(
                                peerId = packet.senderId,
                                timestamp = packet.lastSeenAt,
                                isSameWifi = isSameWifi,
                                wifiSsid = packet.wifiSsid,
                                peerIp = peerEffectiveIp
                            )
                        } else {
                            com.chat.app.utils.P2POsbApiManager.markPeerOffline(packet.senderId, packet.lastSeenAt)
                        }
                        storageManager.updateContactLastSeen(packet.senderId, packet.lastSeenAt)
                    }

                    is com.chat.app.utils.P2PPacket.PresenceOffline -> {
                        com.chat.app.utils.P2POsbApiManager.markPeerOffline(packet.senderId, packet.lastSeenAt)
                        storageManager.updateContactLastSeen(packet.senderId, packet.lastSeenAt)
                    }

                    is com.chat.app.utils.P2PPacket.StatusProbe -> {
                        com.chat.app.utils.P2POsbApiManager.markPeerOnline(packet.senderId)
                        val messages = storageManager.getMessagesByIds(packet.messageIds)
                        val statusMap = messages.associate { it.id to it.status.name }
                        if (statusMap.isNotEmpty()) {
                            val selfId = _selfProfile.value?.id ?: ""
                            val contact = storageManager.getContactById(packet.senderId)
                            com.chat.app.utils.P2PMbsApiManager.sendSyncStatusReport(
                                targetIp = contact?.lastKnownIp ?: "",
                                targetPort = contact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                                targetId = packet.senderId,
                                chatId = packet.chatId,
                                senderId = selfId,
                                statusMap = statusMap
                            )
                        }
                    }

                    is com.chat.app.utils.P2PPacket.StatusReport -> {
                        com.chat.app.utils.P2POsbApiManager.markPeerOnline(packet.senderId)
                        for ((msgId, statusStr) in packet.statuses) {
                            val status = try { MessageStatus.valueOf(statusStr) } catch (_: Exception) { null }
                            if (status != null) {
                                storageManager.updateMessageStatus(msgId, status)
                            }
                        }
                        refreshStorage()
                    }

                    is com.chat.app.utils.P2PPacket.ProfileUpdate -> {
                        val peer = packet.profileData
                        addContact(
                            peer.id,
                            peer.name,
                            peer.avatarUri,
                            peer.age,
                            peer.description,
                            peer.ip,
                            peer.port
                        )
                        com.chat.app.utils.P2POsbApiManager.markPeerOnline(peer.id)
                        if (_scannedPeerProfile.value?.id == peer.id) {
                            _scannedPeerProfile.value = peer
                        }
                    }

                    is com.chat.app.utils.P2PPacket.DeleteMessage -> {
                        val msgToDelete = storageManager.getMessageById(packet.messageId)
                        if (msgToDelete != null) {
                            storageManager.deleteMessage(msgToDelete)
                            refreshStorage()
                        }
                    }

                    is com.chat.app.utils.P2PPacket.EditMessage -> {
                        storageManager.updateMessageText(packet.messageId, packet.newText)
                        refreshStorage()
                    }
                }
            }
        }
    }

    fun completeOnboarding(username: String, avatarUri: Uri?, description: String?, age: Int?) {
        viewModelScope.launch {
            val updated = storageManager.updateSelfProfile(
                username = username.ifBlank { "User" },
                avatarUri = avatarUri,
                description = description?.ifBlank { null },
                age = age
            )
            _selfProfile.value = updated
            storageManager.setOnboardingCompleted(true)
            _isOnboardingCompleted.value = true
            com.chat.app.utils.GlobalP2PMessagingManager.restartWebRelayListener(getApplication()) { _selfProfile.value?.id }
        }
    }

    private var activePresenceJob: Job? = null

    fun resetOnboarding() {
        storageManager.setOnboardingCompleted(false)
        _isOnboardingCompleted.value = false
    }

    fun probePeer(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = storageManager.getContactById(peerId)
            val selfId = _selfProfile.value?.id ?: return@launch
            val targetIp = contact?.lastKnownIp ?: ""
            val targetPort = contact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT
            val myLocalIp = com.chat.app.utils.ProfileQrManager.getLocalIpAddress()

            com.chat.app.utils.P2POsbApiManager.sendPresencePing(
                targetIp = targetIp,
                targetPort = targetPort,
                targetId = peerId,
                senderId = selfId,
                senderIp = myLocalIp
            )
        }
    }

    fun openChat(chatId: String) {
        _activeChatId.value = chatId
        activePresenceJob?.cancel()
        activePresenceJob = viewModelScope.launch(Dispatchers.IO) {
            storageManager.markChatAsRead(chatId)
            
            val contact = storageManager.getContactById(chatId)
            val selfId = _selfProfile.value?.id ?: return@launch
            val targetIp = contact?.lastKnownIp ?: ""
            val targetPort = contact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT
            val myLocalIp = com.chat.app.utils.ProfileQrManager.getLocalIpAddress()

            // 1. Send Read Receipt to peer via dedicated MBS API
            com.chat.app.utils.P2PMbsApiManager.sendReadReceipt(
                targetIp = targetIp,
                targetPort = targetPort,
                targetId = chatId,
                senderId = selfId,
                chatId = chatId,
                readUpToTimestamp = System.currentTimeMillis()
            )

            // 2. Reconcile any unacknowledged outgoing messages
            val unconfirmed = storageManager.getOutgoingUnconfirmedMessages(chatId)
            if (unconfirmed.isNotEmpty()) {
                com.chat.app.utils.P2PMbsApiManager.sendSyncStatusProbe(
                    targetIp = targetIp,
                    targetPort = targetPort,
                    targetId = chatId,
                    chatId = chatId,
                    senderId = selfId,
                    messageIds = unconfirmed.map { it.id }
                )
            }

            // 3. Proactive live OSB presence probe loop while chat is open
            while (coroutineContext.isActive && _activeChatId.value == chatId) {
                com.chat.app.utils.P2POsbApiManager.sendPresencePing(
                    targetIp = targetIp,
                    targetPort = targetPort,
                    targetId = chatId,
                    senderId = selfId,
                    senderIp = myLocalIp
                )
                delay(6_000L) // Continuous live check every 6s while active in chat
            }
        }
    }

    fun closeChat() {
        activePresenceJob?.cancel()
        activePresenceJob = null
        _activeChatId.value = null
    }

    fun sendTypingStatus(isTyping: Boolean) {
        val chatId = _activeChatId.value ?: return
        val selfId = _selfProfile.value?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val contact = storageManager.getContactById(chatId)
            if (contact?.lastKnownIp != null) {
                com.chat.app.utils.GlobalP2PMessagingManager.sendTypingIndicatorToPeer(
                    ip = contact.lastKnownIp,
                    port = com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                    senderId = selfId,
                    chatId = chatId,
                    isTyping = isTyping
                )
            }
        }
    }

    fun sendTextMessage(text: String) {
        val chatId = _activeChatId.value ?: return
        val selfId = _selfProfile.value?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val msg = storageManager.sendTextMessage(
                chatId = chatId,
                senderId = selfId,
                text = text,
                isMine = true,
                status = MessageStatus.SENDING
            )
            refreshStorage()

            val contact = storageManager.getContactById(chatId)
            com.chat.app.utils.GlobalP2PMessagingManager.sendTextMessageToPeer(
                ip = contact?.lastKnownIp ?: "",
                port = com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                message = msg
            ) { success ->
                val status = if (success) MessageStatus.SENT else MessageStatus.FAILED
                viewModelScope.launch(Dispatchers.IO) {
                    storageManager.updateMessageStatus(msg.id, status)
                }
            }
        }
    }

    fun sendMediaMessage(
        uri: Uri,
        mediaType: MediaType,
        originalFileName: String? = null,
    ) {
        val chatId = _activeChatId.value ?: return
        val selfId = _selfProfile.value?.id ?: return
        val appCtx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val msg = storageManager.sendMediaMessage(
                chatId = chatId,
                senderId = selfId,
                uri = uri,
                mediaType = mediaType,
                originalFileName = originalFileName,
                isMine = true,
                status = MessageStatus.SENDING,
                initialProgress = 0.0f
            )
            refreshStorage()

            val contact = storageManager.getContactById(chatId)
            com.chat.app.utils.GlobalP2PMessagingManager.sendMediaMessageToPeer(
                context = appCtx,
                ip = contact?.lastKnownIp ?: "",
                port = contact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                message = msg,
                onProgress = { progress ->
                    viewModelScope.launch(Dispatchers.IO) {
                        storageManager.updateTransferProgress(msg.id, progress)
                    }
                },
                onResult = { success ->
                    val status = if (success) MessageStatus.SENT else MessageStatus.FAILED
                    viewModelScope.launch(Dispatchers.IO) {
                        storageManager.updateMessageStatus(msg.id, status)
                        storageManager.updateTransferProgress(msg.id, 1.0f)
                        refreshStorage()
                    }
                }
            )
        }
    }

    fun retryFailedMessage(msg: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = storageManager.getContactById(msg.chatId)
            storageManager.updateMessageStatus(msg.id, MessageStatus.SENDING)
            refreshStorage()

            val targetIp = contact?.lastKnownIp ?: ""
            val targetPort = contact?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT

            if (msg.mediaType == MediaType.NONE) {
                com.chat.app.utils.GlobalP2PMessagingManager.sendTextMessageToPeer(
                    ip = targetIp,
                    port = targetPort,
                    message = msg
                ) { success ->
                    val status = if (success) MessageStatus.SENT else MessageStatus.FAILED
                    viewModelScope.launch(Dispatchers.IO) {
                        storageManager.updateMessageStatus(msg.id, status)
                        refreshStorage()
                    }
                }
            } else {
                com.chat.app.utils.GlobalP2PMessagingManager.sendMediaMessageToPeer(
                    context = getApplication(),
                    ip = targetIp,
                    port = targetPort,
                    message = msg,
                    onProgress = { progress ->
                        viewModelScope.launch(Dispatchers.IO) { storageManager.updateTransferProgress(msg.id, progress) }
                    },
                    onResult = { success ->
                        val status = if (success) MessageStatus.SENT else MessageStatus.FAILED
                        viewModelScope.launch(Dispatchers.IO) {
                            storageManager.updateMessageStatus(msg.id, status)
                            storageManager.updateTransferProgress(msg.id, 1.0f)
                            refreshStorage()
                        }
                    }
                )
            }
        }
    }

    fun createChat(name: String, avatarUri: String? = null): String {
        val id = UUID.randomUUID().toString()
        viewModelScope.launch(Dispatchers.IO) { storageManager.createOrUpdateChat(id = id, name = name, avatarUri = avatarUri) }
        return id
    }

    fun createChat(id: String, name: String, avatarUri: String? = null): String {
        viewModelScope.launch(Dispatchers.IO) { storageManager.createOrUpdateChat(id = id, name = name, avatarUri = avatarUri) }
        return id
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.deleteChat(chatId)
            if (_activeChatId.value == chatId) {
                _activeChatId.value = null
            }
            refreshStorage()
        }
    }

    fun deleteMessage(msg: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            if (msg.isMine) {
                // If sender deletes their sent message, send a delete signal to the receiver over P2P
                val contact = storageManager.getContactById(msg.chatId)
                val selfId = _selfProfile.value?.id ?: ""
                if (contact?.lastKnownIp != null) {
                    com.chat.app.utils.GlobalP2PMessagingManager.sendDeleteMessageToPeer(
                        ip = contact.lastKnownIp,
                        port = com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                        messageId = msg.id,
                        chatId = msg.chatId,
                        senderId = selfId
                    )
                }
            }
            // Delete locally for sender/receiver
            storageManager.deleteMessage(msg)
            refreshStorage()
        }
    }

    fun editMessage(msg: Message, newText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.updateMessageText(msg.id, newText)
            refreshStorage()
            if (msg.isMine) {
                val contact = storageManager.getContactById(msg.chatId)
                val selfId = _selfProfile.value?.id ?: ""
                if (contact?.lastKnownIp != null) {
                    com.chat.app.utils.GlobalP2PMessagingManager.sendEditMessageToPeer(
                        ip = contact.lastKnownIp,
                        port = com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT,
                        messageId = msg.id,
                        chatId = msg.chatId,
                        senderId = selfId,
                        newText = newText
                    )
                }
            }
        }
    }


    fun updateSelfProfile(username: String, avatarUri: String? = null, age: Int? = null, description: String? = null) {
        viewModelScope.launch {
            val self = _selfProfile.value
            val parsedUri = if (avatarUri != null) Uri.parse(avatarUri) else null
            val updated = storageManager.updateSelfProfile(
                username = username,
                avatarUri = parsedUri,
                age = age,
                description = description
            )
            _selfProfile.value = updated
            com.chat.app.utils.GlobalP2PMessagingManager.restartWebRelayListener(getApplication()) { _selfProfile.value?.id }
            logP2P("My profile updated. Username: $username, Age: $age, Description: $description, Avatar Version: ${updated.avatarVersion}")
            com.chat.app.utils.P2PQrExchangeManager.broadcastProfileUpdateToPeers(getApplication(), contacts.value, updated)
            broadcastProfileVersionToPeers(updated.id, updated.avatarVersion ?: "")
            refreshStorage()
        }
    }

    fun toggleBlockChat(chatId: String) {
        viewModelScope.launch {
            val chat = storageManager.getChatById(chatId) ?: return@launch
            storageManager.setContactBlocked(chatId, !chat.isBlocked)
        }
    }

    fun renameChat(chatId: String, newName: String) {
        viewModelScope.launch {
            storageManager.createOrUpdateChat(id = chatId, name = newName)
        }
    }

    fun setContactNickname(contactId: String, nickname: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.setContactNickname(contactId, nickname)
            refreshStorage()
        }
    }

    fun addContact(
        id: String,
        username: String,
        avatarUri: String? = null,
        age: Int? = null,
        description: String? = null,
        ip: String? = null,
        port: Int? = null
    ) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            logP2P("[P2P Handshake] Initiated handshake exchange with peer ID: $id ($username)")
            val versionHash = "profile_v1_" + UUID.randomUUID().toString().take(8)
            logP2P("[P2P Handshake] Received peer profile metadata: version=$versionHash")

            val existing = storageManager.getContactById(id)

            // Check if a real decoded avatar was passed in and exists on disk
            val validAvatar = if (!avatarUri.isNullOrBlank() && (avatarUri.startsWith("content://") || avatarUri.startsWith("http") || File(avatarUri).exists() || File(ctx.filesDir, avatarUri).exists())) {
                avatarUri
            } else if (!existing?.avatarUri.isNullOrBlank() && (File(existing!!.avatarUri!!).exists() || File(ctx.filesDir, existing!!.avatarUri!!).exists())) {
                existing.avatarUri!!
            } else {
                generateAndSaveSimulatedAvatar(ctx, id, username, versionHash)
            }
            logP2P("[P2P Cache] Saved avatar locally: $validAvatar")
            
            val localNickname = storageManager.getContactNickname(id) ?: existing?.nickname
            val newProfile = Profile(
                id = id,
                username = username,
                nickname = localNickname,
                avatarUri = validAvatar,
                avatarVersion = versionHash,
                age = age,
                description = description,
                isSelf = false,
                lastKnownIp = ip ?: existing?.lastKnownIp,
                lastKnownPort = port ?: existing?.lastKnownPort
            )
            storageManager.saveContact(newProfile)
            
            val chatDisplayName = localNickname ?: username
            storageManager.createOrUpdateChat(id = id, name = chatDisplayName, avatarUri = validAvatar)
            logP2P("[UI] Updated chat room for $username (displayName=$chatDisplayName) with avatar $validAvatar")
            refreshStorage()

            // Immediately probe the new peer so they appear online without waiting
            // for the next heartbeat cycle.
            val selfId = _selfProfile.value?.id
            val peerIp = ip ?: existing?.lastKnownIp ?: ""
            val peerPort = port ?: existing?.lastKnownPort ?: com.chat.app.utils.GlobalP2PMessagingManager.MESSAGING_PORT
            if (!selfId.isNullOrBlank()) {
                com.chat.app.utils.P2POsbApiManager.sendPresencePing(
                    targetIp = peerIp,
                    targetPort = peerPort,
                    targetId = id,
                    senderId = selfId
                )
            }
        }
    }

    fun deleteContact(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            storageManager.deleteContact(profile)
            val chat = chats.value.firstOrNull { it.id == profile.id || it.name == profile.username }
            if (chat != null) {
                storageManager.deleteChat(chat.id)
            } else {
                storageManager.deleteChat(profile.id)
            }
            if (_activeChatId.value == profile.id || _activeChatId.value == chat?.id) {
                _activeChatId.value = null
            }
            refreshStorage()
        }
    }

    fun deleteContact(profileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = storageManager.getContactById(profileId)
            if (contact != null) {
                storageManager.deleteContact(contact)
            } else {
                storageManager.deleteContactById(profileId)
            }
            val chat = chats.value.firstOrNull { it.id == profileId || (contact != null && it.name == contact.username) }
            if (chat != null) {
                storageManager.deleteChat(chat.id)
            } else {
                storageManager.deleteChat(profileId)
            }
            if (_activeChatId.value == profileId || _activeChatId.value == chat?.id) {
                _activeChatId.value = null
            }
            refreshStorage()
        }
    }

    fun cleanOrphanMedia() {
        viewModelScope.launch {
            storageManager.cleanOrphanMedia()
            refreshStorage()
        }
    }

    fun clearCategoryMedia(subfolder: String) {
        viewModelScope.launch {
            storageManager.clearCategoryMedia(subfolder)
            refreshStorage()
        }
    }

    private fun getMockReply(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") -> "Hello there! How's it going? 😊"
            lower.contains("how are you") -> "I'm doing great, thank you! How are you? 🚀"
            lower.contains("image") || lower.contains("photo") -> "I love sharing photos! 📷"
            lower.contains("bye") -> "Goodbye! Have a wonderful day! 👋"
            else -> "That sounds interesting! Tell me more. 💡"
        }
    }

    fun refreshStorage() {
        _refreshStorageTrigger.tryEmit(Unit)
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean("is_dark_mode", enabled).apply()
    }

    fun setHaptics(enabled: Boolean) {
        _isHaptics.value = enabled
        storageManager.setHapticsEnabled(enabled)
    }


    // P2P Sync log state
    private val _p2pLogs = MutableStateFlow<List<String>>(emptyList())
    val p2pLogs: StateFlow<List<String>> = _p2pLogs.asStateFlow()

    // Navigation trigger flow for notification clicks
    private val _navigationTarget = MutableSharedFlow<com.chat.app.Screen>()
    val navigationTarget = _navigationTarget.asSharedFlow()

    fun navigateTo(target: com.chat.app.Screen) {
        viewModelScope.launch {
            _navigationTarget.emit(target)
        }
    }

    fun logP2P(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        _p2pLogs.update { list -> (list + "[$time] $message").takeLast(50) }
    }

    fun clearP2PLogs() {
        _p2pLogs.value = emptyList()
    }

    private fun generateNewProfileVersion(currentVersion: String?): String {
        val nextNum = if (currentVersion != null && currentVersion.startsWith("profile_v")) {
            val parts = currentVersion.split("_")
            val numStr = parts.getOrNull(1)?.removePrefix("v")
            (numStr?.toIntOrNull() ?: 0) + 1
        } else {
            1
        }
        val hash = UUID.randomUUID().toString().take(8)
        return "profile_v${nextNum}_$hash"
    }

    private fun generateAndSaveSimulatedAvatar(context: Context, contactId: String, name: String, versionHash: String): String {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        val colors = listOf("#FF6B6B", "#4D96FF", "#6BCB77", "#FFD93D", "#B20600", "#1572A1", "#9B72AA", "#FF8E9E", "#596157")
        val colorIndex = Math.abs(versionHash.hashCode()) % colors.size
        val bgColor = Color.parseColor(colors[colorIndex])
        
        paint.color = bgColor
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = size * 0.35f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val fontMetrics = paint.fontMetrics
        val yOffset = size / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
        canvas.drawText(initials.ifEmpty { "?" }, size / 2f, yOffset, paint)
        
        val fileName = "contact_${contactId}_${versionHash}.jpg"
        val file = File(context.filesDir, fileName)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fileName
    }

    private fun sendSystemNotification(chatId: String, senderName: String, text: String, avatarUri: String?) {
        val ctx = getApplication<Application>()
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "p2p_chat_messages"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "P2P Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming P2P Chat Messages"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val avatarBitmap = if (!avatarUri.isNullOrEmpty()) {
            try {
                val file = if (avatarUri.startsWith("/")) File(avatarUri) else File(ctx.filesDir, avatarUri)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } catch (e: Exception) {
                null
            }
        } else null

        val selfPerson = Person.Builder()
            .setName("Me")
            .build()

        val senderPersonBuilder = Person.Builder()
            .setName(senderName)
        if (avatarBitmap != null) {
            senderPersonBuilder.setIcon(IconCompat.createWithBitmap(avatarBitmap))
        }
        val senderPerson = senderPersonBuilder.build()

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("chatId", chatId)
            putExtra("chatName", senderName)
            putExtra("avatarUri", avatarUri)
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messagingStyle = NotificationCompat.MessagingStyle(selfPerson)
            .addMessage(text, System.currentTimeMillis(), senderPerson)

        val builder = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setStyle(messagingStyle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap)
        }

        try {
            notificationManager.notify(chatId.hashCode(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playNotificationSound() {
        val ctx = getApplication<Application>()
        try {
            val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = android.media.RingtoneManager.getRingtone(ctx, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun broadcastProfileVersionToPeers(selfId: String, version: String) {
        logP2P("[P2P Mesh] Broadcasting presence beacon: peer_id=$selfId, avatar_version=$version")
        chats.value.forEach { chat ->
            if (!chat.isBlocked) {
                logP2P("[P2P transport] Sent profile announcement to ${chat.name}: version=$version")
            }
        }
    }

    fun simulatePeerProfileUpdate(contactId: String, simulateFailure: Boolean = false) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val contact = storageManager.getContactById(contactId)
            if (contact == null) {
                logP2P("[Error] Contact ID $contactId not found in DB.")
                return@launch
            }
            
            val currentVersion = contact.avatarVersion
            val nextNum = if (currentVersion != null && currentVersion.startsWith("profile_v")) {
                val parts = currentVersion.split("_")
                val numStr = parts.getOrNull(1)?.removePrefix("v")
                (numStr?.toIntOrNull() ?: 0) + 1
            } else {
                2
            }
            val newHash = UUID.randomUUID().toString().take(8)
            val announcedVersion = "profile_v${nextNum}_$newHash"
            
            logP2P("[P2P Mesh] Received passive presence beacon from ${contact.username}: current_avatar_version=$announcedVersion")
            
            if (announcedVersion != currentVersion) {
                logP2P("[P2P Cache] Version mismatch detected! Local: $currentVersion, Remote: $announcedVersion")
                
                if (simulateFailure) {
                    logP2P("[P2P Transport] Attempting to connect to ${contact.username} to download new avatar...")
                    logP2P("[P2P Transport] Connection timed out! Peer is offline/unreachable.")
                    logP2P("[P2P Cache] Best effort sync: keeping last cached image: ${contact.avatarUri}")
                    return@launch
                }
                
                logP2P("[P2P Transport] Requesting new avatar image over P2P connection...")
                kotlinx.coroutines.delay(1000)
                
                // Delete old cached file if present
                contact.avatarUri?.let { oldPath ->
                    val oldFile = if (oldPath.startsWith("/")) File(oldPath) else File(ctx.filesDir, oldPath)
                    if (oldFile.exists()) {
                        oldFile.delete()
                        logP2P("[P2P Cache] Deleted old cached avatar file: ${oldFile.name}")
                    }
                }
                
                val newCachedAvatar = generateAndSaveSimulatedAvatar(ctx, contactId, contact.username, announcedVersion)
                logP2P("[P2P Cache] Cached new avatar as: $newCachedAvatar")
                
                val coilImageLoader = coil.ImageLoader(ctx)
                contact.avatarUri?.let { oldPath ->
                    val file = if (oldPath.startsWith("/")) File(oldPath) else File(ctx.filesDir, oldPath)
                    coilImageLoader.diskCache?.remove(file.absolutePath)
                    coilImageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(file.absolutePath))
                }
                
                val updatedProfile = contact.copy(avatarUri = newCachedAvatar, avatarVersion = announcedVersion)
                storageManager.saveContact(updatedProfile)
                storageManager.createOrUpdateChat(id = contactId, name = contact.username, avatarUri = newCachedAvatar)
                
                logP2P("[UI] Invalidation triggered. Refreshed screens and headers.")
                refreshStorage()
            } else {
                logP2P("[P2P Cache] No change. Local version matches peer version.")
            }
        }
    }

    fun simulateOpportunisticReconnectCheck(contactId: String) {
        viewModelScope.launch {
            val contact = storageManager.getContactById(contactId) ?: return@launch
            logP2P("[P2P Handshake] Reconnected to ${contact.username}. Performing opportunistic version check...")
            
            val hasUpdate = Math.random() > 0.5
            if (hasUpdate) {
                logP2P("[P2P Handshake] Peer has updated profile! Triggering sync...")
                simulatePeerProfileUpdate(contactId, simulateFailure = false)
            } else {
                logP2P("[P2P Handshake] Peer profile is up-to-date. Sync complete.")
            }
        }
    }

    fun deleteProfileAndResetApp() {
        viewModelScope.launch {
            storageManager.clearAllDataAndReset()
            _selfProfile.value = null
            _activeChatId.value = null
            _isOnboardingCompleted.value = false
            refreshStorage()
        }
    }
}
