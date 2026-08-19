package com.chat.app.crypto

import com.chat.app.core.common.Result

/**
 * Represents an established cryptographic session with a peer.
 *
 * A session wraps a shared symmetric key derived from ECDH key agreement.
 * All encrypt/decrypt operations go through this interface.
 *
 * HARD RULES:
 * - encrypt() NEVER returns plaintext on failure — always Result.Failure
 * - decrypt() NEVER returns ciphertext on failure — always Result.Failure
 * - No fallback keys, no key guessing, no silent downgrades
 */
interface CryptoSession {

    /**
     * Establishes a session by performing ECDH key agreement with the peer's public key.
     * The derived shared secret is hashed with SHA-256 to produce a 256-bit AES key.
     */
    suspend fun establish(peerPublicKeyBase64: String): Result<SessionInfo>

    /**
     * Encrypts plaintext using AES-256-GCM with a random 12-byte IV.
     * Output: IV (12 bytes) || ciphertext || auth tag (16 bytes)
     *
     * On failure: Result.Failure — NEVER returns plaintext.
     */
    suspend fun encrypt(plaintext: ByteArray): Result<ByteArray>

    /**
     * Decrypts ciphertext produced by encrypt().
     * Input format: IV (12 bytes) || ciphertext || auth tag (16 bytes)
     *
     * On failure: Result.Failure — NEVER returns ciphertext.
     */
    suspend fun decrypt(ciphertext: ByteArray): Result<ByteArray>

    /**
     * Returns the fingerprint of the peer's public key for this session.
     */
    fun peerFingerprint(): String?

    /**
     * Returns true if a session has been established (shared key is available).
     */
    fun isEstablished(): Boolean

    /**
     * Destroys session state (zeroes key material from memory).
     */
    fun destroy()
}

/**
 * Metadata about an established session.
 */
data class SessionInfo(
    val peerPublicKeyBase64: String,
    val peerFingerprint: String,
    val establishedAt: Long = System.currentTimeMillis(),
)
