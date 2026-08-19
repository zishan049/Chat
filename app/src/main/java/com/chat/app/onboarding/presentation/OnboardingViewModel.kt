package com.chat.app.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
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
    private val createIdentityUseCase: CreateIdentityUseCase
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

    fun onDisplayNameChanged(newName: String) {
        _uiState.update { it.copy(displayName = newName, error = null) }
    }

    fun createIdentity(onSuccess: () -> Unit) {
        val name = _uiState.value.displayName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a display name") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = createIdentityUseCase(displayName = name)
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false, hasExistingIdentity = true) }
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
