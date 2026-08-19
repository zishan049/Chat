package com.chat.app.pairing.domain.model

import com.chat.app.domain.model.Contact

/**
 * Result outcomes of scanning and parsing a peer's pairing QR code.
 */
sealed interface PairingResult {

    /**
     * Successfully parsed and verified.
     * @param contact The contact domain model ready to be saved.
     * @param isNew True if this is a first-time pairing, false if contact was already known with matching key.
     */
    data class Success(val contact: Contact, val isNew: Boolean) : PairingResult

    /**
     * CRITICAL SECURITY EVENT (TOFU Warning):
     * The scanned contact ID already exists in the local database, but the public key/fingerprint
     * DOES NOT MATCH the previously verified key!
     *
     * In accordance with TOFU policy, this must NEVER be accepted silently.
     */
    data class KeyMismatchWarning(
        val existingContact: Contact,
        val newPublicKeyBase64: String,
        val newFingerprint: String,
        val payload: QrPayload
    ) : PairingResult

    /**
     * Cryptographic digital signature verification failed.
     */
    data class InvalidSignature(val reason: String = "Digital signature could not be verified") : PairingResult

    /**
     * Self-pairing attempt (scanning one's own QR code).
     */
    data object SelfScan : PairingResult

    /**
     * Malformed or unrecognized QR payload.
     */
    data class MalformedPayload(val reason: String = "Unrecognized QR code format") : PairingResult
}
