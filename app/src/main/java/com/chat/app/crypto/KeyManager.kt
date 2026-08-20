package com.chat.app.crypto

import com.chat.app.core.common.Result

/**
 * Key management interface. Handles identity key pair generation, storage,
 * and cryptographic signing/verification.
 *
 * Implementation must use Android Keystore for private key storage.
 */
interface KeyManager {

    /**
     * Generates a new EC identity key pair (secp256r1) and stores it securely.
     * This should only be called once during onboarding.
     */
    fun generateIdentityKeyPair(): Result<IdentityKeys>

    /**
     * Loads the existing identity key pair from secure storage.
     * Returns Failure if no identity exists.
     */
    fun loadIdentityKeyPair(): Result<IdentityKeys>

    /**
     * Returns whether an identity key pair has been generated.
     */
    fun hasIdentity(): Boolean

    /**
     * Returns the Base64-encoded EC public key for sharing.
     */
    fun getPublicKeyBase64(): String?

    /**
     * Returns a human-readable fingerprint (hex-encoded SHA-256 of public key bytes).
     */
    fun getFingerprint(): String?

    /**
     * Signs arbitrary data with the identity private key using SHA256withECDSA.
     * Returns Base64-encoded signature.
     */
    fun signPayload(data: ByteArray): Result<ByteArray>

    /**
     * Verifies an ECDSA signature using a peer's public key.
     */
    fun verifySignature(
        peerPublicKeyBase64: String,
        data: ByteArray,
        signature: ByteArray
    ): Boolean

    /**
     * Permanently deletes the identity key pair from the Keystore.
     */
    fun wipeIdentityKeys(): Result<Unit>
}

/**
 * Holds the public portion of the identity key pair for domain use.
 * The private key never leaves KeyManager/Keystore.
 */
data class IdentityKeys(
    val publicKeyBase64: String,
    val fingerprint: String,
)
