package com.chat.app.messaging.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
import com.chat.app.domain.repository.ContactRepository
import com.chat.app.domain.repository.MessageRepository
import com.chat.app.messaging.domain.GetConversationMessagesUseCase
import com.chat.app.messaging.domain.MarkReadUseCase
import com.chat.app.messaging.domain.RetryMessageUseCase
import com.chat.app.messaging.domain.SendMessageUseCase
import com.chat.app.presence.HeartbeatManager
import com.chat.app.presence.domain.PresenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val presenceRepository: PresenceRepository,
    private val heartbeatManager: HeartbeatManager,
    private val getConversationMessagesUseCase: GetConversationMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val markReadUseCase: MarkReadUseCase,
    private val retryMessageUseCase: RetryMessageUseCase
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val _textInput = MutableStateFlow("")
    private val _isSending = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _contact = contactRepository.observeAllContacts().map { contacts ->
        contacts.find { it.id == conversationId }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        combine(
            _contact,
            getConversationMessagesUseCase(conversationId),
            presenceRepository.observePresenceMap()
        ) { contact, messages, presenceMap ->
            Triple(contact, messages, presenceMap[conversationId]?.isOnline == true)
        },
        _textInput,
        _isSending,
        _errorMessage
    ) { (contact, messages, isPeerOnline), text, isSending, error ->
        ChatUiState(
            conversationId = conversationId,
            contact = contact,
            isOnline = isPeerOnline,
            messages = messages,
            textInput = text,
            isSending = isSending,
            errorMessage = error
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ChatUiState(conversationId = conversationId)
    )

    init {
        heartbeatManager.setActiveConversationPeer(conversationId)
        markAsRead()

        // Continuously mark incoming messages as read while this chat is active
        viewModelScope.launch {
            getConversationMessagesUseCase(conversationId).collect { messages ->
                val hasUnreadIncoming = messages.any { !it.isOutgoing && it.status != com.chat.app.domain.model.MessageStatus.READ }
                if (hasUnreadIncoming) {
                    markAsRead()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        heartbeatManager.setActiveConversationPeer(null)
        // Ensure conversation has zero unread count in SQLite when user exits back to chat list
        CoroutineScope(Dispatchers.IO).launch {
            markReadUseCase(conversationId)
        }
    }

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.OnTextChanged -> {
                _textInput.value = action.newText
            }
            is ChatAction.SendMessage -> {
                sendMessage()
            }
            is ChatAction.RetryMessage -> {
                viewModelScope.launch {
                    retryMessageUseCase(action.messageId)
                }
            }
            is ChatAction.DeleteMessage -> {
                viewModelScope.launch {
                    messageRepository.deleteMessage(action.messageId)
                }
            }
        }
    }

    private fun sendMessage() {
        val textToSend = _textInput.value.trim()
        if (textToSend.isBlank()) return

        _textInput.value = ""
        viewModelScope.launch {
            _isSending.value = true
            val result = sendMessageUseCase(conversationId, textToSend)
            _isSending.value = false
            if (result is Result.Failure) {
                _errorMessage.value = result.error.message
            }
        }
    }

    private fun markAsRead() {
        viewModelScope.launch {
            markReadUseCase(conversationId)
        }
    }
}
