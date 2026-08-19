package com.chat.app.messaging.presentation.chatlist

import com.chat.app.domain.model.Conversation
import com.chat.app.domain.model.Identity

data class ChatListUiState(
    val selfIdentity: Identity? = null,
    val conversations: List<Conversation> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
