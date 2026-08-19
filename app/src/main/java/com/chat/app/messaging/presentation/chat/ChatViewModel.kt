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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val getConversationMessagesUseCase: GetConversationMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val markReadUseCase: MarkReadUseCase,
    private val retryMessageUseCase: RetryMessageUseCase
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val _textInput = MutableStateFlow("")
    private val _isSending = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _contact = flow {
        emit(contactRepository.getContact(conversationId))
    }

    val uiState: StateFlow<ChatUiState> = combine(
        _contact,
        getConversationMessagesUseCase(conversationId),
        _textInput,
        _isSending,
        _errorMessage
    ) { contact, messages, text, isSending, error ->
        ChatUiState(
            conversationId = conversationId,
            contact = contact,
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
        markAsRead()
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
