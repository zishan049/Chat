package com.chat.app.pairing.presentation

import android.graphics.Bitmap
import com.chat.app.domain.model.Contact
import com.chat.app.domain.model.Identity
import com.chat.app.pairing.domain.model.PairingResult
import com.chat.app.pairing.domain.model.QrPayload

data class PairingUiState(
    val selectedTab: Int = 1, // 0 = Show QR, 1 = Scan QR (Default to Scan QR when adding a contact)
    val selfIdentity: Identity? = null,
    val selfQrPayloadString: String? = null,
    val selfQrBitmap: Bitmap? = null,
    val isLoadingQr: Boolean = false,
    val isScanning: Boolean = false,
    val pairingResult: PairingResult? = null,
    val keyMismatchWarning: PairingResult.KeyMismatchWarning? = null,
    val verifiedContactToConfirm: Contact? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
