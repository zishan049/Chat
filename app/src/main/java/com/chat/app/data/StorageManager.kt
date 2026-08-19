package com.chat.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Central Backend Storage Management System for the Chat application.
 * Manages local database entities (Profiles, Contacts, Chats, Messages),
 * internal media file storage (Images, Videos, Audio, Files, Avatars),
 * and app preferences.
 */
class StorageManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = ChatDatabase.getInstance(appContext)
    private val profileDao = db.profileDao()
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()
    private val contactNicknameDao = db.contactNicknameDao()

    private val prefs = appContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // ─────────────────────────────────────────────────────────────────────────────
    //  1. Profile & Contacts Management
    // ─────────────────────────────────────────────────────────────────────────────

    val contacts: Flow<List<Profile>> = profileDao.getAllContacts()

    suspend fun getAllContactsList(): List<Profile> = withContext(Dispatchers.IO) {
        profileDao.getAllContactsList()
    }

    suspend fun getSelfProfile(): Profile? = withContext(Dispatchers.IO) {
        profileDao.getSelf()
    }

    suspend fun getContactById(profileId: String): Profile? = withContext(Dispatchers.IO) {
        profileDao.getById(profileId)
    }

    suspend fun updateSelfProfile(
        username: String,
        avatarUri: Uri? = null,
        age: Int? = null,
        description: String? = null,
    ): Profile = withContext(Dispatchers.IO) {
        val currentSelf = profileDao.getSelf()
        val localAvatarPath = if (avatarUri != null) {
            LocalMediaManager.saveMedia(appContext, avatarUri, subfolder = "avatars")
        } else {
            currentSelf?.avatarUri
        }

        val updatedSelf = (currentSelf ?: Profile(username = username, isSelf = true)).copy(
            username = username,
            avatarUri = localAvatarPath,
            avatarVersion = System.currentTimeMillis().toString(),
            age = age,
            description = description,
        )

        val t0 = System.currentTimeMillis()
        profileDao.upsert(updatedSelf)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("UPSERT", "profiles", elapsed, 1, "Self profile: ${username}")
        updatedSelf
    }

    suspend fun saveContact(profile: Profile) = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val localNickname = contactNicknameDao.getNickname(profile.id) ?: profileDao.getById(profile.id)?.nickname
        val finalProfile = if (localNickname != null) profile.copy(nickname = localNickname) else profile
        profileDao.upsert(finalProfile)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("UPSERT", "profiles", elapsed, 1, "Contact: ${profile.username}")
    }

    suspend fun setContactNickname(contactId: String, nickname: String?) = withContext(Dispatchers.IO) {
        val trimmed = nickname?.trim()?.ifBlank { null }
        val t0 = System.currentTimeMillis()
        if (trimmed != null) {
            contactNicknameDao.upsert(ContactNickname(contactId = contactId, nickname = trimmed, updatedAt = System.currentTimeMillis()))
            profileDao.updateNickname(contactId, trimmed)
        } else {
            contactNicknameDao.deleteByContactId(contactId)
            profileDao.updateNickname(contactId, null)
        }
        val contact = profileDao.getById(contactId)
        val finalDisplayName = trimmed ?: contact?.username ?: "User"
        
        val chat = chatDao.getById(contactId)
        if (chat != null) {
            chatDao.upsert(chat.copy(name = finalDisplayName))
        }
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("UPDATE", "contact_nicknames", elapsed, 1, "Nickname for $contactId -> $trimmed")
    }

    suspend fun getContactNickname(contactId: String): String? = withContext(Dispatchers.IO) {
        contactNicknameDao.getNickname(contactId)
    }

    suspend fun deleteContact(profile: Profile) = withContext(Dispatchers.IO) {
        // Also delete avatar image if stored in local avatars dir
        LocalMediaManager.deleteFile(profile.avatarUri)
        com.chat.app.utils.CryptoUtils.invalidateContactKey(profile.id)
        contactNicknameDao.deleteByContactId(profile.id)
        val t0 = System.currentTimeMillis()
        profileDao.delete(profile)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("DELETE", "profiles", elapsed, 1, "Contact ${profile.username} deleted")
    }

    suspend fun deleteContactById(profileId: String) = withContext(Dispatchers.IO) {
        val contact = profileDao.getById(profileId)
        if (contact != null) {
            LocalMediaManager.deleteFile(contact.avatarUri)
        }
        com.chat.app.utils.CryptoUtils.invalidateContactKey(profileId)
        contactNicknameDao.deleteByContactId(profileId)
        val t0 = System.currentTimeMillis()
        profileDao.deleteById(profileId)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("DELETE", "profiles", elapsed, 1, "Contact ID $profileId deleted")
    }

    suspend fun setContactBlocked(profileId: String, isBlocked: Boolean) = withContext(Dispatchers.IO) {
        if (isBlocked) {
            com.chat.app.utils.CryptoUtils.invalidateContactKey(profileId)
        }
        val t0 = System.currentTimeMillis()
        profileDao.setBlockedState(profileId, isBlocked)
        chatDao.setBlockedState(profileId, isBlocked)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("UPDATE", "profiles", elapsed, 1, "Blocked state -> $isBlocked")
    }

    suspend fun searchContacts(query: String): List<Profile> = withContext(Dispatchers.IO) {
        profileDao.searchContacts(query)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  2. Chats & Messages Local Storage
    // ─────────────────────────────────────────────────────────────────────────────

    val chats: Flow<List<Chat>> = chatDao.getAllChats()
    val allMediaMessages: Flow<List<Message>> = messageDao.getAllMediaMessages()

    fun getMessagesForChat(chatId: String): Flow<List<Message>> = messageDao.getMessages(chatId)

    fun getMediaMessagesForChat(chatId: String): Flow<List<Message>> = messageDao.getMediaMessagesForChat(chatId)

    suspend fun getChatById(chatId: String): Chat? = withContext(Dispatchers.IO) {
        chatDao.getById(chatId)
    }

    suspend fun createOrUpdateChat(
        id: String? = null,
        name: String,
        avatarUri: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val chatId = id ?: UUID.randomUUID().toString()
        val existing = chatDao.getById(chatId)
        val chat = existing?.copy(name = name, avatarUri = avatarUri ?: existing.avatarUri)
            ?: Chat(id = chatId, name = name, avatarUri = avatarUri)
        val t0 = System.currentTimeMillis()
        chatDao.upsert(chat)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("UPSERT", "chats", elapsed, 1, "Chat: $name")
        chatId
    }

    suspend fun markChatAsRead(chatId: String) = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        chatDao.markRead(chatId)
        messageDao.markAllRead(chatId)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("UPDATE", "chats", elapsed, 1, "Marked read")
    }

    suspend fun markSentMessagesAsRead(chatId: String) = withContext(Dispatchers.IO) {
        messageDao.markSentMessagesAsRead(chatId)
    }

    suspend fun sendTextMessage(
        chatId: String,
        senderId: String,
        text: String,
        isMine: Boolean = true,
        status: MessageStatus = if (isMine) MessageStatus.SENDING else MessageStatus.DELIVERED,
        timestamp: Long = System.currentTimeMillis(),
        messageId: String? = null
    ): Message = withContext(Dispatchers.IO) {
        // Prevent duplicate insertions if received repeatedly over relay stream reconnects
        if (messageId != null) {
            val existing = messageDao.getById(messageId)
            if (existing != null) return@withContext existing
        }

        val t0 = System.currentTimeMillis()
        val msg = Message(
            id = messageId ?: UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = senderId,
            text = text,
            timestamp = timestamp,
            isMine = isMine,
            status = status,
            transferProgress = 1.0f
        )
        messageDao.insertMessageAndUpdateChat(
            chatDao = chatDao,
            message = msg,
            snippet = text,
            timestamp = timestamp,
            incrementUnread = !isMine
        )
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation(
            operation = "INSERT",
            table = "messages",
            durationMs = elapsed,
            rowsAffected = 1,
            details = "Text message inserted and chat updated (id: ${msg.id.take(8)})"
        )
        com.chat.app.telemetry.AppTelemetry.logMbsStatusChange(
            messageId = msg.id,
            chatId = chatId,
            status = status.name,
            durationMs = elapsed,
            isMine = isMine
        )
        msg
    }

    suspend fun sendMediaMessage(
        chatId: String,
        senderId: String,
        uri: Uri,
        mediaType: MediaType,
        originalFileName: String? = null,
        isMine: Boolean = true,
        status: MessageStatus = if (isMine) MessageStatus.SENDING else MessageStatus.DELIVERED,
        initialProgress: Float = if (isMine) 0.0f else 1.0f,
        messageId: String? = null
    ): Message = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val subfolder = when (mediaType) {
            MediaType.IMAGE -> "images"
            MediaType.VIDEO -> "videos"
            MediaType.AUDIO -> "audio"
            else            -> "files"
        }
        val localPath = LocalMediaManager.saveMedia(appContext, uri, subfolder, originalFileName)
        val thumbPath = if (mediaType == MediaType.IMAGE && localPath != null) {
            LocalMediaManager.generateThumbnail(appContext, localPath)
        } else {
            null
        }

        val snippet = when (mediaType) {
            MediaType.IMAGE -> "📷 Photo"
            MediaType.VIDEO -> "🎥 Video"
            MediaType.AUDIO -> "🎵 Audio"
            else            -> "📎 ${originalFileName ?: "File"}"
        }

        val msg = Message(
            id = messageId ?: UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = senderId,
            text = if (mediaType == MediaType.NONE) snippet else "",
            mediaType = mediaType,
            localMediaUri = localPath,
            thumbnailUri = thumbPath,
            fileName = originalFileName,
            isMine = isMine,
            status = status,
            transferProgress = initialProgress
        )
        messageDao.insertMessageAndUpdateChat(
            chatDao = chatDao,
            message = msg,
            snippet = snippet,
            timestamp = msg.timestamp,
            incrementUnread = !isMine
        )
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation(
            operation = "INSERT",
            table = "messages",
            durationMs = elapsed,
            rowsAffected = 1,
            details = "Media message (${mediaType.name}) inserted (id: ${msg.id.take(8)})"
        )
        com.chat.app.telemetry.AppTelemetry.logMbsStatusChange(
            messageId = msg.id,
            chatId = chatId,
            status = status.name,
            durationMs = elapsed,
            isMine = isMine
        )
        com.chat.app.telemetry.AppTelemetry.logStorageOperation(
            action = "SAVE_MEDIA",
            filePath = localPath,
            bytes = localPath?.let { java.io.File(it).length() } ?: 0L,
            durationMs = elapsed
        )
        msg
    }

    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        messageDao.updateStatus(messageId, status)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("UPDATE", "messages", elapsed, 1, "Status -> ${status.name}")
        com.chat.app.telemetry.AppTelemetry.logMbsStatusChange(
            messageId = messageId,
            chatId = "",
            status = status.name,
            durationMs = elapsed,
            isMine = true
        )
    }

    suspend fun updateMessageText(messageId: String, newText: String) = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        messageDao.updateMessageText(messageId, newText)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("UPDATE", "messages", elapsed, 1, "Text updated")
    }

    suspend fun updateTransferProgress(messageId: String, progress: Float) = withContext(Dispatchers.IO) {
        messageDao.updateTransferProgress(messageId, progress)
    }

    suspend fun getMessageById(messageId: String): Message? = withContext(Dispatchers.IO) {
        messageDao.getById(messageId)
    }

    suspend fun updateContactLastSeen(profileId: String, timestamp: Long) = withContext(Dispatchers.IO) {
        profileDao.updateLastSeen(profileId, timestamp)
    }

    suspend fun getPendingMessagesForChat(chatId: String): List<Message> = withContext(Dispatchers.IO) {
        messageDao.getPendingMessagesForChat(chatId)
    }

    suspend fun getOutgoingUnconfirmedMessages(chatId: String): List<Message> = withContext(Dispatchers.IO) {
        messageDao.getOutgoingUnconfirmedMessages(chatId)
    }

    suspend fun getMessagesByIds(messageIds: List<String>): List<Message> = withContext(Dispatchers.IO) {
        messageDao.getMessagesByIds(messageIds)
    }


    suspend fun deleteMessage(message: Message) = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        // Delete associated local media file and thumbnail if present
        LocalMediaManager.deleteFile(message.localMediaUri)
        LocalMediaManager.deleteFile(message.thumbnailUri)
        messageDao.deleteMessageAndUpdateChat(chatDao, message)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("DELETE", "messages", elapsed, 1, "Message ${message.id.take(8)} deleted")
        if (message.localMediaUri != null) {
            com.chat.app.telemetry.AppTelemetry.logStorageOperation("DELETE_FILE", message.localMediaUri, 0L, 1, elapsed)
        }
    }

    suspend fun deleteChat(chatId: String) = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        // 1. Fetch all local media URIs in chat messages and delete them from internal storage
        val mediaPaths = messageDao.getLocalMediaUrisForChat(chatId)
        mediaPaths.forEach { path ->
            LocalMediaManager.deleteFile(path)
        }

        // 2. Delete messages and chat entity from Room DB (Foreign key CASCADE also ensures cleanup)
        messageDao.deleteAllInChat(chatId)
        chatDao.deleteById(chatId)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logDbOperation("DELETE_CASCADE", "chats", elapsed, mediaPaths.size + 1, "Chat $chatId and ${mediaPaths.size} media deleted")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  3. Media File & Storage Analytics Operations
    // ─────────────────────────────────────────────────────────────────────────────

    suspend fun saveMediaFile(uri: Uri, subfolder: String = "files", originalFileName: String? = null): String? = withContext(Dispatchers.IO) {
        LocalMediaManager.saveMedia(appContext, uri, subfolder, originalFileName)
    }

    suspend fun getStorageBreakdown(): MediaStorageBreakdown = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val activeMediaUris = messageDao.getAllLocalMediaUris().toSet()
        val activeAvatarUris = profileDao.getAllAvatarUris().toSet()
        val allActivePaths = activeMediaUris + activeAvatarUris
        val breakdown = LocalMediaManager.getStorageBreakdown(appContext, allActivePaths)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logStorageOperation(
            action = "STORAGE_BREAKDOWN_SCAN",
            bytes = breakdown.totalBytes,
            count = 1,
            durationMs = elapsed
        )
        breakdown
    }

    suspend fun getTotalStorageBytes(): Long = withContext(Dispatchers.IO) {
        LocalMediaManager.getTotalStorageUsed(appContext)
    }

    suspend fun cleanOrphanMedia(): Int = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val activeMediaUris = messageDao.getAllLocalMediaUris().toSet()
        val activeAvatarUris = profileDao.getAllAvatarUris().toSet()
        val allActivePaths = activeMediaUris + activeAvatarUris
        val deletedCount = LocalMediaManager.cleanOrphanMedia(appContext, allActivePaths)
        val elapsed = System.currentTimeMillis() - t0
        com.chat.app.telemetry.AppTelemetry.logStorageOperation(
            action = "CLEANUP_ORPHANS",
            count = deletedCount,
            durationMs = elapsed
        )
        deletedCount
    }

    suspend fun clearCategoryMedia(subfolder: String): Boolean = withContext(Dispatchers.IO) {
        LocalMediaManager.clearSubfolder(appContext, subfolder)
    }

    /**
     * Issues a TRUNCATE WAL checkpoint to flush the Room write-ahead log and
     * reclaim disk space. Safe to call during idle periods.
     */
    suspend fun checkpointDatabase() = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            val elapsed = System.currentTimeMillis() - t0
            com.chat.app.telemetry.AppTelemetry.logDbOperation("PRAGMA_CHECKPOINT", "sqlite_wal", elapsed, 0, "WAL truncated to 0")
        } catch (_: Exception) { /* non-fatal */ }
    }

    /**
     * Returns the temp directory used for in-progress media transfer chunks,
     * or null if the context is unavailable.
     */
    fun getTempMediaDir(): java.io.File? =
        LocalMediaManager.getMediaDir(appContext, "temp")

    // ─────────────────────────────────────────────────────────────────────────────
    //  4. Preferences & App Settings
    // ─────────────────────────────────────────────────────────────────────────────

    fun isDarkMode(): Boolean = prefs.getBoolean("is_dark_mode", true)

    fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean("is_dark_mode", isDark).apply()
    }

    fun isHapticsEnabled(): Boolean = prefs.getBoolean("is_haptics", true)

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_haptics", enabled).apply()
    }

    fun isSoundEnabled(): Boolean = prefs.getBoolean("is_sound_enabled", true)

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_sound_enabled", enabled).apply()
    }

    fun isAutoDownloadMedia(): Boolean = prefs.getBoolean("is_auto_download_media", true)

    fun setAutoDownloadMedia(enabled: Boolean) {
        prefs.edit().putBoolean("is_auto_download_media", enabled).apply()
    }

    fun isMessagePreviewEnabled(): Boolean = prefs.getBoolean("is_message_preview", true)

    fun setMessagePreviewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_message_preview", enabled).apply()
    }

    fun isOnboardingCompleted(): Boolean = prefs.getBoolean("is_onboarding_completed", false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("is_onboarding_completed", completed).apply()
    }

    suspend fun clearAllDataAndReset() = withContext(Dispatchers.IO) {
        try {
            db.clearAllTables()
        } catch (_: Exception) {}
        LocalMediaManager.clearSubfolder(appContext, "images")
        LocalMediaManager.clearSubfolder(appContext, "videos")
        LocalMediaManager.clearSubfolder(appContext, "audio")
        LocalMediaManager.clearSubfolder(appContext, "files")
        LocalMediaManager.clearSubfolder(appContext, "avatars")
        LocalMediaManager.clearSubfolder(appContext, "temp")
        prefs.edit().clear().apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: StorageManager? = null

        fun getInstance(context: Context): StorageManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: StorageManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
