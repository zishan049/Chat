package com.chat.app.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
import com.chat.app.crypto.KeyManager
import com.chat.app.data.local.media.MediaCategory
import com.chat.app.data.local.media.MediaFileManager
import com.chat.app.data.local.preferences.ThemePreferenceManager
import com.chat.app.data.local.room.ChatDatabase
import com.chat.app.domain.repository.ContactRepository
import com.chat.app.domain.repository.ConversationRepository
import com.chat.app.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val contactRepository: ContactRepository,
    private val conversationRepository: ConversationRepository,
    private val mediaFileManager: MediaFileManager,
    private val themePreferenceManager: ThemePreferenceManager,
    private val chatDatabase: ChatDatabase,
    private val keyManager: KeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observePreferences()
        observeBlockedContacts()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            themePreferenceManager.isDarkMode.collect { isDark ->
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }
        viewModelScope.launch {
            themePreferenceManager.isHapticsEnabled.collect { isHaptics ->
                _uiState.update { it.copy(isHapticsEnabled = isHaptics) }
            }
        }
    }

    private fun observeBlockedContacts() {
        viewModelScope.launch {
            contactRepository.observeBlockedContacts().collect { list ->
                _uiState.update { it.copy(blockedContacts = list) }
            }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            val identityResult = identityRepository.getIdentity()
            val storage = mediaFileManager.getStorageBreakdown()

            _uiState.update {
                it.copy(
                    identity = (identityResult as? Result.Success)?.data,
                    storageBreakdown = storage,
                    isDarkMode = themePreferenceManager.isDarkMode.value,
                    isHapticsEnabled = themePreferenceManager.isHapticsEnabled.value
                )
            }
        }
    }

    fun openBlockedContactsDialog() {
        _uiState.update { it.copy(showBlockedContactsDialog = true) }
    }

    fun closeBlockedContactsDialog() {
        _uiState.update { it.copy(showBlockedContactsDialog = false) }
    }

    fun unblockContact(contactId: String) {
        viewModelScope.launch {
            contactRepository.setBlocked(contactId, false)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        themePreferenceManager.setDarkMode(enabled)
    }

    fun toggleHaptics(enabled: Boolean) {
        themePreferenceManager.setHapticsEnabled(enabled)
    }

    fun clearAllConversations(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            conversationRepository.clearAllConversations()
            val storage = mediaFileManager.getStorageBreakdown()
            _uiState.update { it.copy(storageBreakdown = storage) }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mediaFileManager.clearMedia(MediaCategory.ALL)
                chatDatabase.clearAllTables()
                keyManager.wipeIdentityKeys()
            }
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }
}

