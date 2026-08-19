package com.chat.app.domain.repository

import com.chat.app.core.common.Result
import com.chat.app.domain.model.Message
import com.chat.app.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface MessageRepository {

    fun observeMessages(conversationId: String): Flow<List<Message>>

    suspend fun getMessage(messageId: String): Message?

    suspend fun saveMessage(message: Message): Result<Unit>

    suspend fun updateMessageStatus(messageId: String, status: MessageStatus): Result<Unit>

    suspend fun getPendingOutgoingMessages(): List<Message>

    suspend fun markIncomingMessagesAsRead(conversationId: String): Result<Unit>

    suspend fun markOutgoingMessagesAsRead(conversationId: String): Result<Unit>

    suspend fun markOutgoingMessageAsDelivered(messageId: String): Result<Unit>

    suspend fun deleteMessage(messageId: String): Result<Unit>

    suspend fun deleteAllMessagesInConversation(conversationId: String): Result<Unit>
}
