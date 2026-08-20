package com.chat.app.profile.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
import com.chat.app.data.local.media.MediaFileManager
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.pairing.domain.GenerateQrPayloadUseCase
import com.chat.app.pairing.presentation.QrCodeGenerator
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
class ProfileViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val generateQrPayloadUseCase: GenerateQrPayloadUseCase,
    private val mediaFileManager: MediaFileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            val result = identityRepository.getIdentity()
            if (result is Result.Success) {
                val id = result.data
                _uiState.update {
                    it.copy(
                        identity = id,
                        displayNameInput = id.displayName,
                        avatarUriInput = id.avatarUri,
                        ageInput = id.age?.toString() ?: "",
                        bioInput = id.bio ?: ""
                    )
                }
                generateQrCode()
            }
        }
    }

    private fun generateQrCode() {
        viewModelScope.launch {
            try {
                val qrResult = generateQrPayloadUseCase()
                if (qrResult is Result.Success) {
                    val qrString = qrResult.data
                    val bitmap = withContext(Dispatchers.Default) {
                        QrCodeGenerator.generateQrBitmap(qrString, size = 512)
                    }
                    _uiState.update { it.copy(qrBitmap = bitmap) }
                }
            } catch (_: Exception) {}
        }
    }

    fun openEditDialog() {
        _uiState.update { it.copy(showEditDialog = true, errorMessage = null) }
    }

    fun closeEditDialog() {
        _uiState.update { it.copy(showEditDialog = false, errorMessage = null) }
    }

    fun openQrDialog() {
        _uiState.update { it.copy(showQrDialog = true) }
    }

    fun closeQrDialog() {
        _uiState.update { it.copy(showQrDialog = false) }
    }

    fun toggleSecurityDetails() {
        _uiState.update { it.copy(showSecurityDetails = !it.showSecurityDetails) }
    }

    fun onDisplayNameChanged(value: String) {
        _uiState.update { it.copy(displayNameInput = value, errorMessage = null) }
    }

    fun onAvatarUriChanged(value: String?) {
        if (value == null) {
            _uiState.update { it.copy(avatarUriInput = null) }
            return
        }
        viewModelScope.launch {
            try {
                val parsed = Uri.parse(value)
                val persistentPath = if (value.startsWith("content://")) {
                    mediaFileManager.saveMediaFromUri(parsed, subfolder = "avatars", originalFileName = "avatar.jpg")
                } else {
                    value
                }
                _uiState.update { it.copy(avatarUriInput = persistentPath ?: value) }
            } catch (_: Exception) {
                _uiState.update { it.copy(avatarUriInput = value) }
            }
        }
    }

    fun onAgeChanged(value: String) {
        _uiState.update { it.copy(ageInput = value.filter { ch -> ch.isDigit() }) }
    }

    fun onBioChanged(value: String) {
        _uiState.update { it.copy(bioInput = value) }
    }

    fun saveProfile(onSuccess: () -> Unit = {}) {
        val newName = _uiState.value.displayNameInput.trim()
        if (newName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Display name cannot be empty") }
            return
        }

        val ageInt = _uiState.value.ageInput.trim().toIntOrNull()
        val bioText = _uiState.value.bioInput.trim().ifBlank { null }
        val avatar = _uiState.value.avatarUriInput

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = identityRepository.updateIdentity(
                displayName = newName,
                avatarUri = avatar,
                age = ageInt,
                bio = bioText
            )
            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            identity = result.data,
                            showEditDialog = false,
                            isSuccess = true
                        )
                    }
                    generateQrCode()
                    onSuccess()
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.error.message) }
                }
            }
        }
    }
}
