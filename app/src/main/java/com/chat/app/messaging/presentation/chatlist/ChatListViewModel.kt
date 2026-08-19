package com.chat.app.messaging.presentation.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
import com.chat.app.domain.repository.ConversationRepository
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selfIdentity = flow {
        val result = identityRepository.getIdentity()
        if (result is Result.Success) {
            emit(result.data)
        } else {
            emit(null)
        }
    }

    val uiState: StateFlow<ChatListUiState> = combine(
        _selfIdentity,
        conversationRepository.observeAllConversations(),
        _searchQuery
    ) { identity, conversations, query ->
        val filtered = if (query.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.effectiveName.contains(query, ignoreCase = true) ||
                it.lastMessageSnippet.contains(query, ignoreCase = true)
            }
        }
        ChatListUiState(
            selfIdentity = identity,
            conversations = filtered,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatListUiState())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            messageRepository.deleteAllMessagesInConversation(conversationId)
            conversationRepository.deleteConversation(conversationId)
        }
    }
}
