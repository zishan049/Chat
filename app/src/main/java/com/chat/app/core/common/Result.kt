package com.chat.app.core.common

/**
 * A generic Result type for domain operations.
 * Every important operation returns Result instead of throwing.
 */
sealed interface Result<out T> {

    data class Success<T>(val data: T) : Result<T>

    data class Failure(val error: AppError) : Result<Nothing>
}

/**
 * Sealed hierarchy of application errors with enough context for debugging.
 */
sealed interface AppError {
    val message: String
    val cause: Throwable?
        get() = null

    // Crypto errors
    data class KeyGenerationFailed(override val message: String, override val cause: Throwable? = null) : AppError
    data class SessionEstablishmentFailed(override val message: String, override val cause: Throwable? = null) : AppError
    data class EncryptionFailed(override val message: String, override val cause: Throwable? = null) : AppError
    data class DecryptionFailed(override val message: String, override val cause: Throwable? = null) : AppError
    data class AuthenticationFailed(override val message: String) : AppError

    // Identity errors
    data class IdentityNotFound(override val message: String = "Local identity not found") : AppError
    data class IdentityCreationFailed(override val message: String, override val cause: Throwable? = null) : AppError

    // Pairing errors
    data class PairingFailed(override val message: String, override val cause: Throwable? = null) : AppError
    data class KeyChanged(override val message: String, val contactId: String) : AppError

    // Transport errors
    data class TransportSendFailed(override val message: String, override val cause: Throwable? = null) : AppError
    data class TransportConnectionFailed(override val message: String, override val cause: Throwable? = null) : AppError

    // Database errors
    data class DatabaseError(override val message: String, override val cause: Throwable? = null) : AppError

    // Generic
    data class Unknown(override val message: String, override val cause: Throwable? = null) : AppError
}

/**
 * Extension to map a successful result.
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Failure -> this
}

/**
 * Extension to flatMap a successful result.
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
    is Result.Success -> transform(data)
    is Result.Failure -> this
}

/**
 * Extension to get the value or null.
 */
fun <T> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> data
    is Result.Failure -> null
}

/**
 * Extension to check if result is a success.
 */
val Result<*>.isSuccess: Boolean get() = this is Result.Success
