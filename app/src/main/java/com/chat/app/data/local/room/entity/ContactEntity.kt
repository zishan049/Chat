package com.chat.app.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for paired peer contacts.
 * Includes TOFU trust state: fingerprint is stored on first pairing,
 * isVerified tracks whether the user has accepted the key.
 */
@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["displayName"]),
        Index(value = ["isBlocked"]),
    ]
)
data class ContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val nickname: String? = null,
    val avatarUri: String? = null,
    val publicKeyBase64: String,
    val fingerprint: String,
    val age: Int? = null,
    val bio: String? = null,
    @ColumnInfo(defaultValue = "0") val isBlocked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isVerified: Boolean = false,
    val lastKnownIp: String? = null,
    val lastKnownPort: Int? = null,
    val lastSeenAt: Long? = null,
    val pairedAt: Long,
    val updatedAt: Long = pairedAt,
)
