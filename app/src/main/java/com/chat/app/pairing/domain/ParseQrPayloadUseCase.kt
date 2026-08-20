package com.chat.app.pairing.domain

import android.util.Base64
import com.chat.app.crypto.KeyManager
import com.chat.app.domain.model.Contact
import com.chat.app.domain.repository.ContactRepository
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.pairing.domain.model.PairingResult
import com.chat.app.pairing.domain.model.QrPayload
import java.security.MessageDigest
import javax.inject.Inject

class ParseQrPayloadUseCase @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val contactRepository: ContactRepository,
    private val keyManager: KeyManager
) {

    suspend operator fun invoke(rawPayloadString: String): PairingResult {
        val payload = QrPayload.fromJson(rawPayloadString.trim())
            ?: return PairingResult.MalformedPayload("QR code does not contain a valid payload")

        // 1. Check for self scan
        val selfIdentity = identityRepository.getIdentity()
        if (selfIdentity is com.chat.app.core.common.Result.Success && selfIdentity.data.id == payload.id) {
            return PairingResult.SelfScan
        }

        // 2. Verify Digital Signature if present
        if (payload.signature.isNotBlank()) {
            val canonicalBytes = payload.toCanonicalString().toByteArray(Charsets.UTF_8)
            val sigBytes = try {
                Base64.decode(payload.signature, Base64.DEFAULT)
            } catch (_: Exception) {
                null
            }

            if (sigBytes != null) {
                val isValidSig = keyManager.verifySignature(
                    peerPublicKeyBase64 = payload.publicKeyBase64,
                    data = canonicalBytes,
                    signature = sigBytes
                )

                if (!isValidSig) {
                    return PairingResult.InvalidSignature("Identity digital signature verification failed")
                }
            }
        }

        // 3. Compute public key SHA-256 fingerprint
        val computedFingerprint = try {
            val pubKeyBytes = Base64.decode(payload.publicKeyBase64, Base64.DEFAULT)
            val digest = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            return PairingResult.MalformedPayload("Invalid public key format: ${e.message}")
        }

        // 4. TOFU Key Verification check
        val existingContact = contactRepository.getContact(payload.id)
        if (existingContact != null) {
            if (existingContact.fingerprint != computedFingerprint || existingContact.publicKeyBase64 != payload.publicKeyBase64) {
                // Key Changed! Emit security warning
                return PairingResult.KeyMismatchWarning(
                    existingContact = existingContact,
                    newPublicKeyBase64 = payload.publicKeyBase64,
                    newFingerprint = computedFingerprint,
                    payload = payload
                )
            }

            // Key matches existing verified contact -> update network info and return
            val updated = existingContact.copy(
                displayName = payload.displayName,
                avatarUri = existingContact.avatarUri,
                lastKnownIp = payload.lanIp ?: existingContact.lastKnownIp,
                lastKnownPort = payload.port,
                updatedAt = System.currentTimeMillis()
            )
            return PairingResult.Success(contact = updated, isNew = false)
        }

        // 5. New Contact
        val newContact = Contact(
            id = payload.id,
            displayName = payload.displayName,
            avatarUri = null,
            publicKeyBase64 = payload.publicKeyBase64,
            fingerprint = computedFingerprint,
            isBlocked = false,
            isVerified = true, // Trusted on first use
            lastKnownIp = payload.lanIp,
            lastKnownPort = payload.port,
            lastSeenAt = System.currentTimeMillis(),
            pairedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        return PairingResult.Success(contact = newContact, isNew = true)
    }
}
