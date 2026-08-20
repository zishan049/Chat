package com.chat.app.messaging.data

import com.chat.app.core.common.AppError
import com.chat.app.core.common.DispatcherProvider
import com.chat.app.core.common.Result
import com.chat.app.data.local.room.dao.ContactDao
import com.chat.app.data.local.room.dao.ConversationDao
import com.chat.app.data.local.room.dao.MessageDao
import com.chat.app.domain.model.Conversation
import com.chat.app.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao,
    private val messageDao: MessageDao,
    private val dispatchers: DispatcherProvider
) : ConversationRepository {

    override fun observeAllConversations(): Flow<List<Conversation>> {
        return combine(
            conversationDao.observeAll(),
            contactDao.observeAllContacts()
        ) { conversations, contacts ->
            val convMap = conversations.associateBy { it.contactId }
            val unblockedContacts = contacts.filter { !it.isBlocked }

            val allList = mutableListOf<Conversation>()

            for (conv in conversations) {
                val contact = unblockedContacts.find { it.id == conv.contactId }
                if (contact != null) {
                    allList.add(
                        Conversation(
                            id = conv.id,
                            contactId = conv.contactId,
                            contactDisplayName = contact.displayName,
                            contactNickname = contact.nickname,
                            contactAvatarUri = contact.avatarUri,
                            lastMessageSnippet = conv.lastMessageSnippet,
                            lastMessageAt = conv.lastMessageAt,
                            unreadCount = conv.unreadCount,
                            createdAt = conv.createdAt
                        )
                    )
                }
            }

            for (contact in unblockedContacts) {
                if (convMap[contact.id] == null) {
                    allList.add(
                        Conversation(
                            id = contact.id,
                            contactId = contact.id,
                            contactDisplayName = contact.displayName,
                            contactNickname = contact.nickname,
                            contactAvatarUri = contact.avatarUri,
                            lastMessageSnippet = "Tap to start chatting",
                            lastMessageAt = contact.pairedAt.takeIf { it > 0 } ?: contact.updatedAt,
                            unreadCount = 0,
                            createdAt = contact.pairedAt.takeIf { it > 0 } ?: contact.updatedAt
                        )
                    )
                }
            }

            allList.sortedByDescending { it.lastMessageAt }
        }.flowOn(dispatchers.io)
    }

    override suspend fun getConversation(conversationId: String): Conversation? = withContext(dispatchers.io) {
        val conv = conversationDao.getById(conversationId) ?: return@withContext null
        val contact = contactDao.getById(conv.contactId)
        Conversation(
            id = conv.id,
            contactId = conv.contactId,
            contactDisplayName = contact?.displayName ?: "Contact",
            contactNickname = contact?.nickname,
            contactAvatarUri = contact?.avatarUri,
            lastMessageSnippet = conv.lastMessageSnippet,
            lastMessageAt = conv.lastMessageAt,
            unreadCount = conv.unreadCount,
            createdAt = conv.createdAt
        )
    }

    override suspend fun updateLastMessage(
        conversationId: String,
        snippet: String,
        timestamp: Long,
        incrementUnread: Boolean
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val existing = conversationDao.getById(conversationId)
            if (existing == null) {
                conversationDao.insert(
                    com.chat.app.data.local.room.entity.ConversationEntity(
                        id = conversationId,
                        contactId = conversationId,
                        lastMessageSnippet = snippet,
                        lastMessageAt = timestamp,
                        unreadCount = if (incrementUnread) 1 else 0,
                        createdAt = timestamp
                    )
                )
            } else {
                conversationDao.updateLastMessage(conversationId, snippet, timestamp)
                if (incrementUnread) {
                    conversationDao.incrementUnread(conversationId)
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to update last message", e))
        }
    }

    override suspend fun markRead(conversationId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            conversationDao.markRead(conversationId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to mark conversation read", e))
        }
    }

    override suspend fun deleteConversation(conversationId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            conversationDao.deleteById(conversationId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to delete conversation", e))
        }
    }

    override suspend fun clearAllConversations(): Result<Unit> = withContext(dispatchers.io) {
        try {
            messageDao.deleteAll()
            conversationDao.deleteAll()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to clear conversations", e))
        }
    }
}
