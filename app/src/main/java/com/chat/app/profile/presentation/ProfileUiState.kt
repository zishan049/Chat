package com.chat.app.profile.presentation

import android.graphics.Bitmap
import com.chat.app.domain.model.Identity

data class ProfileUiState(
    val identity: Identity? = null,
    val displayNameInput: String = "",
    val avatarUriInput: String? = null,
    val ageInput: String = "",
    val bioInput: String = "",
    val qrBitmap: Bitmap? = null,
    val showQrDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showSecurityDetails: Boolean = false,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

