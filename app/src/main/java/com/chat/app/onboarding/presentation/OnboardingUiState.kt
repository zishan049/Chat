package com.chat.app.onboarding.presentation

data class OnboardingUiState(
    val displayName: String = "",
    val avatarUri: String? = null,
    val age: String = "",
    val bio: String = "",
    val showCreateSheet: Boolean = false,
    val isLoading: Boolean = false,
    val isCheckingExisting: Boolean = true,
    val hasExistingIdentity: Boolean = false,
    val error: String? = null
)

