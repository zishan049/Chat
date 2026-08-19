package com.chat.app.messaging.domain

import com.chat.app.core.common.Result
import com.chat.app.domain.repository.ConversationRepository
import com.chat.app.domain.repository.MessageRepository
import javax.inject.Inject

class MarkReadUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val transportDispatcher: TransportDispatcher
) {
    suspend operator fun invoke(conversationId: String): Result<Unit> {
        messageRepository.markIncomingMessagesAsRead(conversationId)
        val result = conversationRepository.markRead(conversationId)

        // Dispatch read receipt across the network to the peer
        transportDispatcher.sendReadReceipt(conversationId)

        return result
    }
}
