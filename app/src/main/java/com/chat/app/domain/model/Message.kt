package com.chat.app.domain.model

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val status: MessageStatus = MessageStatus.QUEUED,
    val isOutgoing: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
