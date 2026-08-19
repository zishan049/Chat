package com.chat.app.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for messages.
 * Message status is tracked here for UI display.
 * Detailed delivery tracking is in MessageDeliveryEntity.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["conversationId", "timestamp"]),
        Index(value = ["conversationId", "status", "isOutgoing"]),
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String = "",
    val status: String = "QUEUED",
    @ColumnInfo(defaultValue = "1") val isOutgoing: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)
