package com.chat.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
//  Entities
// ─────────────────────────────────────────────────────────────────────────────

enum class MediaType { NONE, IMAGE, VIDEO, FILE, AUDIO }

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

@Entity(
    tableName = "profiles",
    indices = [
        Index(value = ["isSelf"]),
        Index(value = ["username"]),
    ]
)
data class Profile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val username: String,
    val nickname: String? = null,
    val avatarUri: String? = null,
    val avatarVersion: String? = null,
    val age: Int? = null,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSelf: Boolean = false,
    val isBlocked: Boolean = false,
    val lastKnownIp: String? = null,
    val lastKnownPort: Int? = null,
    val lastSeenAt: Long? = null,
) {
    val displayName: String
        get() = if (!nickname.isNullOrBlank()) nickname else username
}

@Entity(tableName = "contact_nicknames")
data class ContactNickname(
    @PrimaryKey val contactId: String,
    val nickname: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["lastMessageAt"]),
    ]
)
data class Chat(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarUri: String? = null,
    val avatarVersion: String? = null,
    val lastMessageSnippet: String = "",
    val lastMessageAt: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = Chat::class,
        parentColumns = ["id"],
        childColumns = ["chatId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["chatId", "timestamp"]),
        Index(value = ["chatId", "mediaType", "timestamp"]),
        Index(value = ["chatId", "status", "isMine"]),
    ]
)
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    val senderId: String,
    val text: String = "",
    val mediaType: MediaType = MediaType.NONE,
    val localMediaUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isMine: Boolean = true,
    val status: MessageStatus = MessageStatus.SENT,
    val transferProgress: Float = 1.0f,
    val thumbnailUri: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
//  DAOs
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): Profile?

    @Query("SELECT * FROM profiles WHERE id = :profileId LIMIT 1")
    suspend fun getById(profileId: String): Profile?

    @Query("SELECT * FROM profiles WHERE isSelf = 0")
    fun getAllContacts(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE isSelf = 0")
    suspend fun getAllContactsList(): List<Profile>

    @Query("SELECT * FROM profiles WHERE isSelf = 0 AND username LIKE '%' || :query || '%'")
    suspend fun searchContacts(query: String): List<Profile>

    @Query("UPDATE profiles SET isBlocked = :isBlocked WHERE id = :profileId")
    suspend fun setBlockedState(profileId: String, isBlocked: Boolean)

    @Query("SELECT avatarUri FROM profiles WHERE avatarUri IS NOT NULL")
    suspend fun getAllAvatarUris(): List<String>

    @Query("UPDATE profiles SET lastSeenAt = :lastSeenAt WHERE id = :profileId")
    suspend fun updateLastSeen(profileId: String, lastSeenAt: Long)

    @Query("UPDATE profiles SET nickname = :nickname WHERE id = :profileId")
    suspend fun updateNickname(profileId: String, nickname: String?)

    @Upsert
    suspend fun upsert(profile: Profile)

    @Delete
    suspend fun delete(profile: Profile)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteById(profileId: String)
}

@Dao
interface ContactNicknameDao {
    @Query("SELECT * FROM contact_nicknames")
    fun getAllNicknames(): Flow<List<ContactNickname>>

    @Query("SELECT nickname FROM contact_nicknames WHERE contactId = :contactId LIMIT 1")
    suspend fun getNickname(contactId: String): String?

    @Query("SELECT * FROM contact_nicknames WHERE contactId = :contactId LIMIT 1")
    suspend fun getByContactId(contactId: String): ContactNickname?

    @Upsert
    suspend fun upsert(nickname: ContactNickname)

    @Query("DELETE FROM contact_nicknames WHERE contactId = :contactId")
    suspend fun deleteByContactId(contactId: String)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageAt DESC")
    fun getAllChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getById(chatId: String): Chat?

    @Query("SELECT * FROM chats LIMIT 1")
    suspend fun getAnyChat(): Chat?

    @Query("UPDATE chats SET isBlocked = :isBlocked WHERE id = :chatId")
    suspend fun setBlockedState(chatId: String, isBlocked: Boolean)

    @Upsert
    suspend fun upsert(chat: Chat)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteById(chatId: String)

    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun markRead(chatId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessages(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE mediaType != 'NONE' ORDER BY timestamp DESC")
    fun getAllMediaMessages(): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND mediaType != 'NONE' ORDER BY timestamp DESC")
    fun getMediaMessagesForChat(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(chatId: String): Message?

    @Query("SELECT localMediaUri FROM messages WHERE localMediaUri IS NOT NULL UNION SELECT thumbnailUri FROM messages WHERE thumbnailUri IS NOT NULL")
    suspend fun getAllLocalMediaUris(): List<String>

    @Query("SELECT localMediaUri FROM messages WHERE chatId = :chatId AND localMediaUri IS NOT NULL UNION SELECT thumbnailUri FROM messages WHERE chatId = :chatId AND thumbnailUri IS NOT NULL")
    suspend fun getLocalMediaUrisForChat(chatId: String): List<String>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getById(messageId: String): Message?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND status IN ('SENDING', 'FAILED') AND isMine = 1")
    suspend fun getPendingMessagesForChat(chatId: String): List<Message>

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: MessageStatus)

    @Query("UPDATE messages SET transferProgress = :progress WHERE id = :messageId")
    suspend fun updateTransferProgress(messageId: String, progress: Float)

    @Query("UPDATE messages SET text = :newText WHERE id = :messageId")
    suspend fun updateMessageText(messageId: String, newText: String)

    @Upsert
    suspend fun upsert(message: Message)

    @Delete
    suspend fun delete(message: Message)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllInChat(chatId: String)

    @Query("UPDATE messages SET isRead = 1, status = 'READ' WHERE chatId = :chatId AND isMine = 0")
    suspend fun markAllRead(chatId: String)

    @Query("UPDATE messages SET status = 'READ' WHERE chatId = :chatId AND isMine = 1 AND status != 'READ'")
    suspend fun markSentMessagesAsRead(chatId: String)

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isMine = 1 AND status IN ('SENT', 'DELIVERED')")
    suspend fun getOutgoingUnconfirmedMessages(chatId: String): List<Message>

    @Query("SELECT * FROM messages WHERE id IN (:messageIds)")
    suspend fun getMessagesByIds(messageIds: List<String>): List<Message>

    /**
     * Atomically inserts/updates a message and synchronizes the parent chat's
     * snippet, timestamp, and unread counter in a single database transaction.
     */
    @Transaction
    suspend fun insertMessageAndUpdateChat(
        chatDao: ChatDao,
        message: Message,
        snippet: String,
        timestamp: Long,
        incrementUnread: Boolean,
        fallbackChatName: String = "Contact"
    ) {
        val currentChat = chatDao.getById(message.chatId)
        if (currentChat != null) {
            chatDao.upsert(
                currentChat.copy(
                    lastMessageSnippet = snippet,
                    lastMessageAt = timestamp,
                    unreadCount = if (incrementUnread) currentChat.unreadCount + 1 else currentChat.unreadCount
                )
            )
        } else {
            chatDao.upsert(
                Chat(
                    id = message.chatId,
                    name = fallbackChatName,
                    lastMessageSnippet = snippet,
                    lastMessageAt = timestamp,
                    unreadCount = if (incrementUnread) 1 else 0
                )
            )
        }
        upsert(message)
    }

    /**
     * Atomically deletes a message and recalculates the parent chat's snippet
     * and timestamp based on the latest remaining message.
     */
    @Transaction
    suspend fun deleteMessageAndUpdateChat(
        chatDao: ChatDao,
        message: Message
    ) {
        delete(message)
        val latest = getLatest(message.chatId)
        val chat = chatDao.getById(message.chatId)
        if (chat != null) {
            chatDao.upsert(
                chat.copy(
                    lastMessageSnippet = latest?.text ?: "",
                    lastMessageAt = latest?.timestamp ?: System.currentTimeMillis()
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Database & Migrations
// ─────────────────────────────────────────────────────────────────────────────

val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_profiles_isSelf` ON `profiles` (`isSelf`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_profiles_username` ON `profiles` (`username`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chats_lastMessageAt` ON `chats` (`lastMessageAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId_timestamp` ON `messages` (`chatId`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId_mediaType_timestamp` ON `messages` (`chatId`, `mediaType`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId_status_isMine` ON `messages` (`chatId`, `status`, `isMine`)")
    }
}

val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `thumbnailUri` TEXT DEFAULT NULL")
    }
}

val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `lastSeenAt` INTEGER DEFAULT NULL")
    }
}

val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `contact_nicknames` (`contactId` TEXT NOT NULL, `nickname` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`contactId`))")
        db.execSQL("ALTER TABLE `profiles` ADD COLUMN `nickname` TEXT DEFAULT NULL")
    }
}

@Database(
    entities = [Profile::class, Chat::class, Message::class, ContactNickname::class],
    version = 11,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun contactNicknameDao(): ContactNicknameDao

    companion object {
        @Volatile private var INSTANCE: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_database"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
    }
}

class Converters {
    @TypeConverter fun fromMediaType(v: MediaType): String = v.name
    @TypeConverter fun toMediaType(v: String): MediaType = MediaType.valueOf(v)

    @TypeConverter fun fromMessageStatus(v: MessageStatus): String = v.name
    @TypeConverter fun toMessageStatus(v: String): MessageStatus = try { MessageStatus.valueOf(v) } catch (e: Exception) { MessageStatus.SENT }
}

