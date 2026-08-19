package com.chat.app.contacts.presentation

import com.chat.app.domain.model.Contact

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedContactForAction: Contact? = null,
    val editingNicknameForContact: Contact? = null,
    val nicknameInput: String = "",
    val errorMessage: String? = null
)
