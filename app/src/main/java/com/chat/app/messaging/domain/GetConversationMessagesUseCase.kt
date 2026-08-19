package com.chat.app.messaging.domain

import com.chat.app.domain.model.Message
import com.chat.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetConversationMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(conversationId: String): Flow<List<Message>> {
        return messageRepository.observeMessages(conversationId)
    }
}
