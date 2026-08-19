package com.chat.app.domain.model

/**
 * Domain model for a paired peer contact.
 */
data class Contact(
    val id: String,
    val displayName: String,
    val nickname: String? = null,
    val avatarUri: String? = null,
    val publicKeyBase64: String,
    val fingerprint: String,
    val age: Int? = null,
    val bio: String? = null,
    val isBlocked: Boolean = false,
    val isVerified: Boolean = false,
    val lastKnownIp: String? = null,
    val lastKnownPort: Int? = null,
    val lastSeenAt: Long? = null,
    val pairedAt: Long,
    val updatedAt: Long = pairedAt,
) {
    /**
     * Returns the display name to show in UI, preferring nickname over display name.
     */
    val effectiveName: String
        get() = nickname?.takeIf { it.isNotBlank() } ?: displayName
}
