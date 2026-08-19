package com.chat.app.domain.repository

import com.chat.app.core.common.Result
import com.chat.app.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {

    fun observeAllConversations(): Flow<List<Conversation>>

    suspend fun getConversation(conversationId: String): Conversation?

    suspend fun updateLastMessage(
        conversationId: String,
        snippet: String,
        timestamp: Long,
        incrementUnread: Boolean = false
    ): Result<Unit>

    suspend fun markRead(conversationId: String): Result<Unit>

    suspend fun deleteConversation(conversationId: String): Result<Unit>
}
