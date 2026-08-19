package com.chat.app.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Tracks delivery lifecycle for outgoing messages independently from message content.
 * Timestamps are recorded at each state transition for observability.
 */
@Entity(
    tableName = "message_delivery",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class MessageDeliveryEntity(
    @PrimaryKey val messageId: String,
    val status: String = "QUEUED",
    val sentAt: Long? = null,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val retryCount: Int = 0,
    val failureReason: String? = null,
)
