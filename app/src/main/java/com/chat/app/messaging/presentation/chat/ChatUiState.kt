package com.chat.app.messaging.presentation.chat

import com.chat.app.domain.model.Contact
import com.chat.app.domain.model.Message

data class ChatUiState(
    val conversationId: String = "",
    val contact: Contact? = null,
    val messages: List<Message> = emptyList(),
    val textInput: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null
)

sealed interface ChatAction {
    data class OnTextChanged(val newText: String) : ChatAction
    data object SendMessage : ChatAction
    data class RetryMessage(val messageId: String) : ChatAction
    data class DeleteMessage(val messageId: String) : ChatAction
}
