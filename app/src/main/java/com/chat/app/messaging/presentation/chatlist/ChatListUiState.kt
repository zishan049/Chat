package com.chat.app.messaging.presentation.chatlist

import com.chat.app.domain.model.Conversation
import com.chat.app.domain.model.Identity
import com.chat.app.domain.model.PeerPresence

data class ChatListUiState(
    val selfIdentity: Identity? = null,
    val conversations: List<Conversation> = emptyList(),
    val presenceMap: Map<String, PeerPresence> = emptyMap(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
