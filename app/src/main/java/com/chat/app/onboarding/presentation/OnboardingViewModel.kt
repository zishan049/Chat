package com.chat.app.onboarding.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
import com.chat.app.data.local.media.MediaFileManager
import com.chat.app.identity.domain.CreateIdentityUseCase
import com.chat.app.identity.domain.GetIdentityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val getIdentityUseCase: GetIdentityUseCase,
    private val createIdentityUseCase: CreateIdentityUseCase,
    private val mediaFileManager: MediaFileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        checkExistingIdentity()
    }

    private fun checkExistingIdentity() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingExisting = true) }
            val result = getIdentityUseCase()
            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isCheckingExisting = false,
                            hasExistingIdentity = true
                        )
                    }
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isCheckingExisting = false,
                            hasExistingIdentity = false
                        )
                    }
                }
            }
        }
    }

    fun openCreateSheet() {
        _uiState.update { it.copy(showCreateSheet = true, error = null) }
    }

    fun closeCreateSheet() {
        _uiState.update { it.copy(showCreateSheet = false, error = null) }
    }

    fun onDisplayNameChanged(newName: String) {
        _uiState.update { it.copy(displayName = newName, error = null) }
    }

    fun onAvatarUriChanged(newUri: String?) {
        if (newUri == null) {
            _uiState.update { it.copy(avatarUri = null) }
            return
        }
        viewModelScope.launch {
            try {
                val parsed = Uri.parse(newUri)
                val persistentPath = if (newUri.startsWith("content://")) {
                    mediaFileManager.saveMediaFromUri(parsed, subfolder = "avatars", originalFileName = "avatar.jpg")
                } else {
                    newUri
                }
                _uiState.update { it.copy(avatarUri = persistentPath ?: newUri) }
            } catch (_: Exception) {
                _uiState.update { it.copy(avatarUri = newUri) }
            }
        }
    }

    fun onAgeChanged(newAge: String) {
        _uiState.update { it.copy(age = newAge.filter { ch -> ch.isDigit() }) }
    }

    fun onBioChanged(newBio: String) {
        _uiState.update { it.copy(bio = newBio) }
    }

    fun createIdentity(onSuccess: () -> Unit) {
        val name = _uiState.value.displayName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a display name") }
            return
        }

        val ageInt = _uiState.value.age.trim().toIntOrNull()
        val bioText = _uiState.value.bio.trim().ifBlank { null }
        val avatar = _uiState.value.avatarUri

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = createIdentityUseCase(
                displayName = name,
                avatarUri = avatar,
                age = ageInt,
                bio = bioText
            )
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, hasExistingIdentity = true, showCreateSheet = false) }
                    onSuccess()
                }
                is Result.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.error.message
                        )
                    }
                }
            }
        }
    }
}
