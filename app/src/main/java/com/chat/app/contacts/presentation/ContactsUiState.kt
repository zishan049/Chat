package com.chat.app.contacts.presentation

import com.chat.app.domain.model.Contact
import com.chat.app.domain.model.Identity
import com.chat.app.domain.model.PeerPresence

data class ContactsUiState(
    val selfIdentity: Identity? = null,
    val contacts: List<Contact> = emptyList(),
    val presenceMap: Map<String, PeerPresence> = emptyMap(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val selectedContactForAction: Contact? = null,
    val editingNicknameForContact: Contact? = null,
    val nicknameInput: String = "",
    val errorMessage: String? = null
)

