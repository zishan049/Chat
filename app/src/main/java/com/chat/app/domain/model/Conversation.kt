package com.chat.app.domain.model

data class Conversation(
    val id: String,
    val contactId: String,
    val contactDisplayName: String = "Contact",
    val contactNickname: String? = null,
    val contactAvatarUri: String? = null,
    val lastMessageSnippet: String = "",
    val lastMessageAt: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val effectiveName: String
        get() = contactNickname?.takeIf { it.isNotBlank() } ?: contactDisplayName
}
