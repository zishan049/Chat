package com.chat.app.messaging.domain

import com.chat.app.core.common.AppError
import com.chat.app.core.common.Result
import com.chat.app.domain.model.Message
import com.chat.app.domain.model.MessageStatus
import com.chat.app.domain.repository.ConversationRepository
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val transportDispatcher: TransportDispatcher
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    suspend operator fun invoke(
        conversationId: String,
        text: String
    ): Result<Message> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return Result.Failure(AppError.Unknown("Cannot send empty message"))
        }

        val identityResult = identityRepository.getIdentity()
        if (identityResult is Result.Failure) {
            return identityResult
        }
        val selfId = (identityResult as Result.Success).data.id

        val now = System.currentTimeMillis()
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = selfId,
            text = trimmed,
            status = MessageStatus.QUEUED,
            isOutgoing = true,
            timestamp = now,
            createdAt = now
        )

        // 1. Persist locally first (durable state before transmission)
        val saveResult = messageRepository.saveMessage(message)
        if (saveResult is Result.Failure) {
            return Result.Failure(AppError.DatabaseError("Failed to persist message locally", (saveResult as Result.Failure).error.cause))
        }

        // 2. Update conversation snippet and timestamp
        conversationRepository.updateLastMessage(
            conversationId = conversationId,
            snippet = trimmed,
            timestamp = now,
            incrementUnread = false
        )

        // 3. Trigger asynchronous encryption and network transmission
        scope.launch {
            transportDispatcher.dispatchMessage(message)
        }

        return Result.Success(message)
    }
}
