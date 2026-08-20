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
import com.chat.app.messaging.domain.TransportDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val generateQrPayloadUseCase: GenerateQrPayloadUseCase,
    private val parseQrPayloadUseCase: ParseQrPayloadUseCase,
    private val acceptContactUseCase: AcceptContactUseCase,
    private val transportDispatcher: TransportDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private var qrAutoRefreshJob: kotlinx.coroutines.Job? = null

    init {
        startQrAutoRefresh()
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex, errorMessage = null) }
        if (tabIndex == 1) {
            startQrAutoRefresh()
        } else {
            qrAutoRefreshJob?.cancel()
        }
    }

    fun startQrAutoRefresh() {
        qrAutoRefreshJob?.cancel()
        qrAutoRefreshJob = viewModelScope.launch {
            while (isActive) {
                generateFreshQrCode()
                delay(45_000L) // Auto-regenerate every 45 seconds with fresh timestamp & signature
            }
        }
    }

    fun loadSelfQrCode() {
        startQrAutoRefresh()
    }

    private suspend fun generateFreshQrCode() {
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

    fun onQrScanned(rawPayload: String) {
        viewModelScope.launch {
            val result = parseQrPayloadUseCase(rawPayload)
            when (result) {
                is PairingResult.Success -> {
                    // Send handshake immediately upon scan so peer device receives pairing info and opens dialog
                    transportDispatcher.sendPairingHandshake(result.contact)
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
                // Dispatch bi-directional pairing handshake over network so peer device auto-pairs
                transportDispatcher.sendPairingHandshake(contact)
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

    fun confirmKeyOverride(warning: com.chat.app.pairing.domain.model.PairingResult.KeyMismatchWarning, onComplete: () -> Unit) {
        viewModelScope.launch {
            // User explicitly confirmed key replacement after security warning
            val updatedContact = warning.existingContact.copy(
                displayName = warning.payload.displayName,
                avatarUri = warning.existingContact.avatarUri,
                publicKeyBase64 = warning.newPublicKeyBase64,
                fingerprint = warning.newFingerprint,
                lastKnownIp = warning.payload.lanIp ?: warning.existingContact.lastKnownIp,
                lastKnownPort = warning.payload.port,
                isVerified = true,
                updatedAt = System.currentTimeMillis()
            )

            val result = acceptContactUseCase(updatedContact)
            if (result is Result.Success) {
                transportDispatcher.sendPairingHandshake(updatedContact)
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
