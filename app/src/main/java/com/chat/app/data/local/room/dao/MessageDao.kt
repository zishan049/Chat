package com.chat.app.data.local.room.dao

import androidx.room.*
import com.chat.app.data.local.room.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(conversationId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status IN ('QUEUED', 'FAILED') AND isOutgoing = 1")
    suspend fun getPendingMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status IN ('QUEUED', 'FAILED') AND isOutgoing = 1")
    suspend fun getAllPendingMessages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("UPDATE messages SET text = :text WHERE id = :messageId")
    suspend fun updateText(messageId: String, text: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteAllInConversation(conversationId: String)

    /**
     * Marks all incoming messages in a conversation as read.
     */
    @Query("UPDATE messages SET status = 'READ' WHERE conversationId = :conversationId AND isOutgoing = 0 AND status != 'READ'")
    suspend fun markAllIncomingAsRead(conversationId: String)

    /**
     * Marks outgoing messages as READ (when peer sends read receipt).
     */
    @Query("UPDATE messages SET status = 'READ' WHERE conversationId = :conversationId AND isOutgoing = 1 AND status IN ('SENT', 'DELIVERED')")
    suspend fun markOutgoingAsRead(conversationId: String)

    /**
     * Marks a specific outgoing message as DELIVERED.
     */
    @Query("UPDATE messages SET status = 'DELIVERED' WHERE id = :messageId AND status = 'SENT'")
    suspend fun markAsDelivered(messageId: String)
}
