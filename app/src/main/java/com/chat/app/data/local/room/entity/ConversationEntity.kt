package com.chat.app.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for chat conversations.
 * For 1:1 chats, id matches the contact's id.
 */
@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["contactId"], unique = true),
        Index(value = ["lastMessageAt"]),
    ]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val lastMessageSnippet: String = "",
    val lastMessageAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
