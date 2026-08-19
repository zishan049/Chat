package com.chat.app.messaging

import com.chat.app.core.common.Result
import com.chat.app.domain.model.Conversation
import com.chat.app.domain.model.Identity
import com.chat.app.domain.model.Message
import com.chat.app.domain.model.MessageStatus
import com.chat.app.domain.repository.ConversationRepository
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.domain.repository.MessageRepository
import com.chat.app.messaging.domain.ReceiveMessageUseCase
import com.chat.app.messaging.domain.SendMessageUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MessagingUseCasesTest {

    private val selfIdentity = Identity(
        id = "alice-uuid",
        displayName = "Alice",
        publicKeyBase64 = "pk",
        fingerprint = "fp",
        createdAt = 1000L
    )

    private val fakeIdentityRepo = object : IdentityRepository {
        override suspend fun createIdentity(displayName: String, avatarUri: String?): Result<Identity> = Result.Success(selfIdentity)
        override suspend fun getIdentity(): Result<Identity> = Result.Success(selfIdentity)
        override suspend fun updateIdentity(displayName: String, avatarUri: String?): Result<Identity> = Result.Success(selfIdentity)
        override suspend fun hasIdentity(): Boolean = true
    }

    private class FakeMessageRepo : MessageRepository {
        val messages = mutableMapOf<String, Message>()

        override fun observeMessages(conversationId: String): Flow<List<Message>> =
            flowOf(messages.values.filter { it.conversationId == conversationId })

        override suspend fun getMessage(messageId: String): Message? = messages[messageId]

        override suspend fun saveMessage(message: Message): Result<Unit> {
            messages[message.id] = message
            return Result.Success(Unit)
        }

        override suspend fun updateMessageStatus(messageId: String, status: MessageStatus): Result<Unit> {
            messages[messageId]?.let {
                messages[messageId] = it.copy(status = status)
            }
            return Result.Success(Unit)
        }

        override suspend fun getPendingOutgoingMessages(): List<Message> =
            messages.values.filter { it.status.isPending && it.isOutgoing }

        override suspend fun markIncomingMessagesAsRead(conversationId: String): Result<Unit> {
            messages.values.filter { it.conversationId == conversationId && !it.isOutgoing }.forEach {
                messages[it.id] = it.copy(status = MessageStatus.READ)
            }
            return Result.Success(Unit)
        }

        override suspend fun markOutgoingMessagesAsRead(conversationId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun markOutgoingMessageAsDelivered(messageId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteMessage(messageId: String): Result<Unit> {
            messages.remove(messageId)
            return Result.Success(Unit)
        }
        override suspend fun deleteAllMessagesInConversation(conversationId: String): Result<Unit> {
            messages.entries.removeIf { it.value.conversationId == conversationId }
            return Result.Success(Unit)
        }
    }

    private class FakeConversationRepo : ConversationRepository {
        val conversations = mutableMapOf<String, Conversation>()

        override fun observeAllConversations(): Flow<List<Conversation>> = flowOf(conversations.values.toList())
        override suspend fun getConversation(conversationId: String): Conversation? = conversations[conversationId]

        override suspend fun updateLastMessage(
            conversationId: String,
            snippet: String,
            timestamp: Long,
            incrementUnread: Boolean
        ): Result<Unit> {
            val existing = conversations[conversationId] ?: Conversation(
                id = conversationId,
                contactId = conversationId,
                lastMessageSnippet = snippet,
                lastMessageAt = timestamp,
                unreadCount = 0
            )
            conversations[conversationId] = existing.copy(
                lastMessageSnippet = snippet,
                lastMessageAt = timestamp,
                unreadCount = if (incrementUnread) existing.unreadCount + 1 else existing.unreadCount
            )
            return Result.Success(Unit)
        }

        override suspend fun markRead(conversationId: String): Result<Unit> {
            conversations[conversationId]?.let {
                conversations[conversationId] = it.copy(unreadCount = 0)
            }
            return Result.Success(Unit)
        }

        override suspend fun deleteConversation(conversationId: String): Result<Unit> {
            conversations.remove(conversationId)
            return Result.Success(Unit)
        }
    }

    @Test
    fun testSendMessageOfflineQueued() = runBlocking {
        val messageRepo = FakeMessageRepo()
        val convRepo = FakeConversationRepo()
        val sendUseCase = SendMessageUseCase(fakeIdentityRepo, messageRepo, convRepo)

        val result = sendUseCase(conversationId = "bob-uuid", text = "Hello Bob!")
        assertTrue("Message send must succeed", result is Result.Success)
        val msg = (result as Result.Success).data

        assertEquals("Hello Bob!", msg.text)
        assertEquals("bob-uuid", msg.conversationId)
        assertEquals("alice-uuid", msg.senderId)
        assertEquals(MessageStatus.QUEUED, msg.status)
        assertTrue(msg.isOutgoing)

        // Verify stored in DB
        val stored = messageRepo.getMessage(msg.id)
        assertNotNull("Message must be persisted locally in DB", stored)
        assertEquals(MessageStatus.QUEUED, stored?.status)

        // Verify conversation updated
        val conv = convRepo.getConversation("bob-uuid")
        assertNotNull(conv)
        assertEquals("Hello Bob!", conv?.lastMessageSnippet)
    }

    @Test
    fun testEmptyMessageRejection() = runBlocking {
        val messageRepo = FakeMessageRepo()
        val convRepo = FakeConversationRepo()
        val sendUseCase = SendMessageUseCase(fakeIdentityRepo, messageRepo, convRepo)

        val result = sendUseCase(conversationId = "bob-uuid", text = "   ")
        assertTrue("Empty message must be rejected", result is Result.Failure)
    }

    @Test
    fun testReceiveMessageIdempotency() = runBlocking {
        val messageRepo = FakeMessageRepo()
        val convRepo = FakeConversationRepo()
        val receiveUseCase = ReceiveMessageUseCase(messageRepo, convRepo)

        val msgId = "incoming-msg-123"
        val timestamp = 5000L

        // First receive
        val result1 = receiveUseCase(
            messageId = msgId,
            conversationId = "bob-uuid",
            senderId = "bob-uuid",
            text = "Hey Alice",
            timestamp = timestamp,
            isChatActive = false
        )
        assertTrue(result1 is Result.Success)
        assertEquals(1, messageRepo.messages.size)
        assertEquals(1, convRepo.getConversation("bob-uuid")?.unreadCount)

        // Duplicate receive of same packet/message ID
        val result2 = receiveUseCase(
            messageId = msgId,
            conversationId = "bob-uuid",
            senderId = "bob-uuid",
            text = "Hey Alice",
            timestamp = timestamp,
            isChatActive = false
        )
        assertTrue(result2 is Result.Success)
        assertEquals("Duplicate message packet MUST NOT create duplicate rows", 1, messageRepo.messages.size)
    }
}
