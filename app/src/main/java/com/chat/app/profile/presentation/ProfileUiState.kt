package com.chat.app.profile.presentation

import com.chat.app.domain.model.Identity

data class ProfileUiState(
    val identity: Identity? = null,
    val displayNameInput: String = "",
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)
