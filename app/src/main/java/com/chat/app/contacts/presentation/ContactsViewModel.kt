package com.chat.app.contacts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
import com.chat.app.domain.model.Contact
import com.chat.app.domain.repository.ContactRepository
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.presence.domain.PresenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val identityRepository: IdentityRepository,
    private val presenceRepository: PresenceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(ContactsUiState())

    init {
        loadSelfIdentity()
    }

    private fun loadSelfIdentity() {
        viewModelScope.launch {
            val result = identityRepository.getIdentity()
            if (result is Result.Success) {
                _uiState.update { it.copy(selfIdentity = result.data) }
            }
        }
    }

    val uiState: StateFlow<ContactsUiState> = combine(
        contactRepository.observeAllContacts(),
        identityRepository.observeIdentity(),
        presenceRepository.observePresenceMap(),
        _searchQuery
    ) { contacts, identity, presenceMap, query ->
        val filtered = if (query.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.effectiveName.contains(query, ignoreCase = true) ||
                it.displayName.contains(query, ignoreCase = true)
            }
        }
        _uiState.value.copy(
            selfIdentity = identity,
            contacts = filtered,
            presenceMap = presenceMap,
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
