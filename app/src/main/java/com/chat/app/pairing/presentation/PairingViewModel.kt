package com.chat.app.pairing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
import com.chat.app.domain.model.Contact
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.pairing.domain.AcceptContactUseCase
import com.chat.app.pairing.domain.GenerateQrPayloadUseCase
import com.chat.app.pairing.domain.ParseQrPayloadUseCase
import com.chat.app.pairing.domain.model.PairingResult
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
class PairingViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val generateQrPayloadUseCase: GenerateQrPayloadUseCase,
    private val parseQrPayloadUseCase: ParseQrPayloadUseCase,
    private val acceptContactUseCase: AcceptContactUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        loadSelfQrCode()
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex, errorMessage = null) }
    }

    fun loadSelfQrCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingQr = true) }
            val identityResult = identityRepository.getIdentity()
            val qrStringResult = generateQrPayloadUseCase()

            if (identityResult is Result.Success && qrStringResult is Result.Success) {
                val identity = identityResult.data
                val qrString = qrStringResult.data
                val bitmap = withContext(Dispatchers.Default) {
                    QrCodeGenerator.generateQrBitmap(qrString, size = 600)
                }
                _uiState.update {
                    it.copy(
                        selfIdentity = identity,
                        selfQrPayloadString = qrString,
                        selfQrBitmap = bitmap,
                        isLoadingQr = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingQr = false,
                        errorMessage = "Failed to generate identity QR code"
                    )
                }
            }
        }
    }

    fun onQrScanned(rawPayload: String) {
        viewModelScope.launch {
            val result = parseQrPayloadUseCase(rawPayload)
            when (result) {
                is PairingResult.Success -> {
                    _uiState.update {
                        it.copy(
                            verifiedContactToConfirm = result.contact,
                            keyMismatchWarning = null,
                            errorMessage = null
                        )
                    }
                }
                is PairingResult.KeyMismatchWarning -> {
                    _uiState.update {
                        it.copy(
                            keyMismatchWarning = result,
                            verifiedContactToConfirm = null,
                            errorMessage = null
                        )
                    }
                }
                is PairingResult.InvalidSignature -> {
                    _uiState.update { it.copy(errorMessage = "Security Alert: Invalid digital signature! Scanned code may be tampered.") }
                }
                is PairingResult.SelfScan -> {
                    _uiState.update { it.copy(errorMessage = "You cannot pair with your own device QR code.") }
                }
                is PairingResult.MalformedPayload -> {
                    _uiState.update { it.copy(errorMessage = result.reason) }
                }
            }
        }
    }

    fun confirmAddContact(contact: Contact, onComplete: () -> Unit) {
        viewModelScope.launch {
            val result = acceptContactUseCase(contact)
            if (result is Result.Success) {
                _uiState.update {
                    it.copy(
                        verifiedContactToConfirm = null,
                        keyMismatchWarning = null,
                        successMessage = "Successfully paired with ${contact.displayName}!"
                    )
                }
                onComplete()
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to save contact to database") }
            }
        }
    }

    fun confirmKeyOverride(warning: PairingResult.KeyMismatchWarning, onComplete: () -> Unit) {
        viewModelScope.launch {
            // User explicitly confirmed key replacement after security warning
            val updatedContact = warning.existingContact.copy(
                displayName = warning.payload.displayName,
                publicKeyBase64 = warning.newPublicKeyBase64,
                fingerprint = warning.newFingerprint,
                lastKnownIp = warning.payload.lanIp ?: warning.existingContact.lastKnownIp,
                lastKnownPort = warning.payload.port,
                isVerified = true,
                updatedAt = System.currentTimeMillis()
            )

            val result = acceptContactUseCase(updatedContact)
            if (result is Result.Success) {
                _uiState.update {
                    it.copy(
                        keyMismatchWarning = null,
                        successMessage = "Identity key updated and trusted for ${updatedContact.displayName}."
                    )
                }
                onComplete()
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to update contact") }
            }
        }
    }

    fun dismissDialogs() {
        _uiState.update {
            it.copy(
                verifiedContactToConfirm = null,
                keyMismatchWarning = null,
                errorMessage = null
            )
        }
    }
}
