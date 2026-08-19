package com.chat.app.domain.model

/**
 * Domain model for the local user's identity.
 * Contains only the public portion — private key never leaves KeyManager.
 */
data class Identity(
    val id: String,
    val displayName: String,
    val avatarUri: String? = null,
    val publicKeyBase64: String,
    val fingerprint: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
)
