package com.chat.app.contacts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.domain.model.Contact
import com.chat.app.domain.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(ContactsUiState())

    val uiState: StateFlow<ContactsUiState> = combine(
        contactRepository.observeAllContacts(),
        _searchQuery,
        _uiState
    ) { contacts, query, state ->
        val filtered = if (query.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.effectiveName.contains(query, ignoreCase = true) ||
                it.displayName.contains(query, ignoreCase = true)
            }
        }
        state.copy(
            contacts = filtered,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactsUiState())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun startEditNickname(contact: Contact) {
        _uiState.update {
            it.copy(
                editingNicknameForContact = contact,
                nicknameInput = contact.nickname ?: ""
            )
        }
    }

    fun onNicknameInputChanged(value: String) {
        _uiState.update { it.copy(nicknameInput = value) }
    }

    fun saveNickname() {
        val contact = _uiState.value.editingNicknameForContact ?: return
        val newNickname = _uiState.value.nicknameInput.trim()
        viewModelScope.launch {
            contactRepository.updateNickname(contact.id, newNickname)
            _uiState.update {
                it.copy(
                    editingNicknameForContact = null,
                    nicknameInput = ""
                )
            }
        }
    }

    fun cancelEditNickname() {
        _uiState.update {
            it.copy(
                editingNicknameForContact = null,
                nicknameInput = ""
            )
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact.id)
        }
    }

    fun toggleBlockContact(contact: Contact) {
        viewModelScope.launch {
            contactRepository.setBlocked(contact.id, !contact.isBlocked)
        }
    }
}
