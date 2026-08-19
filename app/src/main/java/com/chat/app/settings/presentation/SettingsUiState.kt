package com.chat.app.settings.presentation

import com.chat.app.data.local.media.MediaStorageBreakdown
import com.chat.app.domain.model.Identity

data class SettingsUiState(
    val identity: Identity? = null,
    val isDarkMode: Boolean = true,
    val isHapticsEnabled: Boolean = true,
    val storageBreakdown: MediaStorageBreakdown = MediaStorageBreakdown(),
    val isClearingStorage: Boolean = false,
    val appVersion: String = "2.0.0"
)
