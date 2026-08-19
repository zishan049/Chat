package com.chat.app.messaging.data

import com.chat.app.core.common.AppError
import com.chat.app.core.common.DispatcherProvider
import com.chat.app.core.common.Result
import com.chat.app.data.local.room.dao.MessageDao
import com.chat.app.data.local.room.entity.MessageEntity
import com.chat.app.domain.model.Message
import com.chat.app.domain.model.MessageStatus
import com.chat.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val dispatchers: DispatcherProvider
) : MessageRepository {

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.observeMessages(conversationId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getMessage(messageId: String): Message? = withContext(dispatchers.io) {
        messageDao.getById(messageId)?.toDomain()
    }

    override suspend fun saveMessage(message: Message): Result<Unit> = withContext(dispatchers.io) {
        try {
            messageDao.insert(message.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to save message", e))
        }
    }

    override suspend fun updateMessageStatus(messageId: String, status: MessageStatus): Result<Unit> = withContext(dispatchers.io) {
        try {
            messageDao.updateStatus(messageId, status.name)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to update message status", e))
        }
    }

    override suspend fun getPendingOutgoingMessages(): List<Message> = withContext(dispatchers.io) {
        messageDao.getAllPendingMessages().map { it.toDomain() }
    }

    override suspend fun markIncomingMessagesAsRead(conversationId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            messageDao.markAllIncomingAsRead(conversationId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to mark incoming messages as read", e))
        }
    }

    override suspend fun markOutgoingMessagesAsRead(conversationId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            messageDao.markOutgoingAsRead(conversationId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to mark outgoing messages as read", e))
        }
    }

    override suspend fun markOutgoingMessageAsDelivered(messageId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            messageDao.markAsDelivered(messageId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to mark message as delivered", e))
        }
    }

    override suspend fun deleteMessage(messageId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            messageDao.deleteById(messageId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to delete message", e))
        }
    }

    override suspend fun deleteAllMessagesInConversation(conversationId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            messageDao.deleteAllInConversation(conversationId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to delete all messages", e))
        }
    }

    private fun MessageEntity.toDomain(): Message = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        text = text,
        status = try { MessageStatus.valueOf(status) } catch (_: Exception) { MessageStatus.QUEUED },
        isOutgoing = isOutgoing,
        timestamp = timestamp,
        createdAt = createdAt
    )

    private fun Message.toEntity(): MessageEntity = MessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        text = text,
        status = status.name,
        isOutgoing = isOutgoing,
        timestamp = timestamp,
        createdAt = createdAt
    )
}
