package com.chat.app.messaging.domain

import com.chat.app.core.common.Result
import com.chat.app.domain.model.Message
import com.chat.app.domain.model.MessageStatus
import com.chat.app.domain.repository.ConversationRepository
import com.chat.app.domain.repository.MessageRepository
import javax.inject.Inject

class ReceiveMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) {

    suspend operator fun invoke(
        messageId: String,
        conversationId: String,
        senderId: String,
        text: String,
        timestamp: Long,
        isChatActive: Boolean = false
    ): Result<Message> {
        // Idempotency: Check if message is already stored
        val existing = messageRepository.getMessage(messageId)
        if (existing != null) {
            return Result.Success(existing)
        }

        val message = Message(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            text = text,
            status = if (isChatActive) MessageStatus.READ else MessageStatus.DELIVERED,
            isOutgoing = false,
            timestamp = timestamp,
            createdAt = System.currentTimeMillis()
        )

        // 1. Insert into local SQLite database
        val saveResult = messageRepository.saveMessage(message)
        if (saveResult is Result.Failure) {
            return saveResult
        }

        // 2. Synchronize parent conversation snippet and unread counter
        conversationRepository.updateLastMessage(
            conversationId = conversationId,
            snippet = text,
            timestamp = timestamp,
            incrementUnread = !isChatActive
        )

        return Result.Success(message)
    }
}
