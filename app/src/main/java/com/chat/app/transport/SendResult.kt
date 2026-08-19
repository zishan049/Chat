package com.chat.app.transport

sealed interface SendResult {
    data object Success : SendResult
    data class Failure(val reason: String, val isRetriable: Boolean = true) : SendResult
}
