package com.chat.app.crypto

import android.util.Base64
import com.chat.app.core.common.AppError
import com.chat.app.core.common.Result
import com.chat.app.core.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoSession implementation using ECDH (secp256r1) + AES-256-GCM.
 *
 * SECURITY INVARIANTS:
 * 1. encrypt() failure → Result.Failure (NEVER returns plaintext)
 * 2. decrypt() failure → Result.Failure (NEVER returns ciphertext)
 * 3. No fallback keys — exactly one shared key per session
 * 4. No key guessing — if the key is wrong, decryption fails hard
 * 5. GCM auth tag is always verified — tampered ciphertext is rejected
 */
class CryptoSessionImpl : CryptoSession {

    companion object {
        private const val TAG = "CryptoSession"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "chat_identity_ec_key"
    }

    private val secureRandom = SecureRandom()

    @Volatile
    private var sharedKey: SecretKey? = null

    @Volatile
    private var peerPubKeyB64: String? = null

    @Volatile
    private var peerFingerprintValue: String? = null

    override suspend fun establish(peerPublicKeyBase64: String): Result<SessionInfo> =
        withContext(Dispatchers.Default) {
            try {
                // Load our private key from Android Keystore
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                    ?: return@withContext Result.Failure(
                        AppError.SessionEstablishmentFailed("No identity key in Keystore")
                    )

                // Parse peer's public key
                val peerKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.DEFAULT)
                val keyFactory = KeyFactory.getInstance("EC")
                val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerKeyBytes))

                // ECDH key agreement
                val keyAgreement = KeyAgreement.getInstance("ECDH")
                keyAgreement.init(entry.privateKey)
                keyAgreement.doPhase(peerPublicKey, true)
                val rawSharedSecret = keyAgreement.generateSecret()

                // Derive AES-256 key: SHA-256(sharedSecret)
                val md = MessageDigest.getInstance("SHA-256")
                val aesKeyBytes = md.digest(rawSharedSecret)
                val derivedKey = SecretKeySpec(aesKeyBytes, "AES")

                // Compute peer fingerprint
                val fingerprint = peerKeyBytes.let { bytes ->
                    MessageDigest.getInstance("SHA-256").digest(bytes)
                        .joinToString(":") { "%02X".format(it) }
                }

                // Store session state
                sharedKey = derivedKey
                peerPubKeyB64 = peerPublicKeyBase64
                peerFingerprintValue = fingerprint

                // Zero the raw shared secret from memory
                rawSharedSecret.fill(0)

                AppLog.i(TAG, "Session established. Peer fingerprint: ${fingerprint.take(16)}…")

                Result.Success(
                    SessionInfo(
                        peerPublicKeyBase64 = peerPublicKeyBase64,
                        peerFingerprint = fingerprint,
                    )
                )
            } catch (e: Exception) {
                AppLog.e(TAG, "Session establishment failed", e)
                Result.Failure(AppError.SessionEstablishmentFailed("ECDH agreement failed", e))
            }
        }

    override suspend fun encrypt(plaintext: ByteArray): Result<ByteArray> =
        withContext(Dispatchers.Default) {
            val key = sharedKey
                ?: return@withContext Result.Failure(
                    AppError.EncryptionFailed("No session established — cannot encrypt")
                )

            try {
                val iv = ByteArray(GCM_IV_LENGTH)
                secureRandom.nextBytes(iv)

                val cipher = Cipher.getInstance(AES_GCM)
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

                val cipherBytes = cipher.doFinal(plaintext)

                // Output: IV || ciphertext+tag
                val combined = ByteArray(iv.size + cipherBytes.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

                Result.Success(combined)
            } catch (e: Exception) {
                AppLog.e(TAG, "Encryption failed", e)
                // HARD FAILURE — never return plaintext
                Result.Failure(AppError.EncryptionFailed("AES-GCM encryption failed", e))
            }
        }

    override suspend fun decrypt(ciphertext: ByteArray): Result<ByteArray> =
        withContext(Dispatchers.Default) {
            val key = sharedKey
                ?: return@withContext Result.Failure(
                    AppError.DecryptionFailed("No session established — cannot decrypt")
                )

            if (ciphertext.size <= GCM_IV_LENGTH) {
                return@withContext Result.Failure(
                    AppError.DecryptionFailed("Ciphertext too short (${ciphertext.size} bytes)")
                )
            }

            try {
                val iv = ByteArray(GCM_IV_LENGTH)
                System.arraycopy(ciphertext, 0, iv, 0, GCM_IV_LENGTH)

                val encryptedBytes = ByteArray(ciphertext.size - GCM_IV_LENGTH)
                System.arraycopy(ciphertext, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.size)

                val cipher = Cipher.getInstance(AES_GCM)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

                val decrypted = cipher.doFinal(encryptedBytes)

                Result.Success(decrypted)
            } catch (e: Exception) {
                AppLog.e(TAG, "Decryption failed (auth tag mismatch or wrong key)", e)
                // HARD FAILURE — never return ciphertext
                Result.Failure(AppError.DecryptionFailed("AES-GCM decryption failed", e))
            }
        }

    override fun peerFingerprint(): String? = peerFingerprintValue

    override fun isEstablished(): Boolean = sharedKey != null

    override fun destroy() {
        sharedKey = null
        peerPubKeyB64 = null
        peerFingerprintValue = null
        AppLog.d(TAG, "Session destroyed")
    }
}
