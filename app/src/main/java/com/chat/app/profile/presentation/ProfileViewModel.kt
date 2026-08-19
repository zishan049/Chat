package com.chat.app.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
import com.chat.app.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val identityRepository: IdentityRepository
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
                _uiState.update {
                    it.copy(
                        identity = result.data,
                        displayNameInput = result.data.displayName
                    )
                }
            }
        }
    }

    fun onDisplayNameChanged(value: String) {
        _uiState.update { it.copy(displayNameInput = value, errorMessage = null) }
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val newName = _uiState.value.displayNameInput.trim()
        if (newName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Display name cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = identityRepository.updateIdentity(displayName = newName)
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, identity = result.data, isSuccess = true) }
                    onSuccess()
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.error.message) }
                }
            }
        }
    }
}
