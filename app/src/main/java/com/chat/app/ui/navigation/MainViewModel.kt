package com.chat.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chat.app.core.common.Result
import com.chat.app.core.network.PortProvider
import com.chat.app.domain.model.Contact
import com.chat.app.domain.model.Identity
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.messaging.domain.TransportDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthState {
    data object Loading : AuthState
    data class Authenticated(val identity: Identity) : AuthState
    data object Unauthenticated : AuthState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val transportDispatcher: TransportDispatcher,
    private val portProvider: PortProvider
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _incomingPairedContact = MutableStateFlow<Contact?>(null)
    val incomingPairedContact: StateFlow<Contact?> = _incomingPairedContact.asStateFlow()

    init {
        checkAuth()
        observePairingEvents()
    }

    private fun observePairingEvents() {
        viewModelScope.launch {
            transportDispatcher.pairingEvents.collect { contact ->
                _incomingPairedContact.value = contact
            }
        }
    }

    fun dismissPairingDialog() {
        _incomingPairedContact.value = null
    }

    fun checkAuth() {
        viewModelScope.launch {
            val result = identityRepository.getIdentity()
            if (result is Result.Success) {
                _authState.value = AuthState.Authenticated(result.data)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun refreshAuth() {
        checkAuth()
    }

    fun logout() {
        viewModelScope.launch {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun getActivePort(): Int = portProvider.getActivePort()
}

