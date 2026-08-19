package com.chat.app.messaging.domain

import com.chat.app.core.common.AppError
import com.chat.app.core.common.Result
import com.chat.app.domain.model.MessageStatus
import com.chat.app.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class RetryMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val transportDispatcher: TransportDispatcher
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    suspend operator fun invoke(messageId: String): Result<Unit> {
        val message = messageRepository.getMessage(messageId)
            ?: return Result.Failure(AppError.Unknown("Message not found"))

        messageRepository.updateMessageStatus(messageId, MessageStatus.QUEUED)

        scope.launch {
            transportDispatcher.dispatchMessage(message.copy(status = MessageStatus.QUEUED))
        }

        return Result.Success(Unit)
    }
}
