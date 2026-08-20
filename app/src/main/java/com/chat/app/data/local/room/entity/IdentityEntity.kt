package com.chat.app.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the local user's identity.
 * Only one row should exist (the self identity).
 */
@Entity(tableName = "identity")
data class IdentityEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val avatarUri: String? = null,
    val age: Int? = null,
    val bio: String? = null,
    val publicKeyBase64: String,
    val fingerprint: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
)

