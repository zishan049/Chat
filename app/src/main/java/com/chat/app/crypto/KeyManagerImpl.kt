package com.chat.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.chat.app.core.common.AppError
import com.chat.app.core.common.Result
import com.chat.app.core.logging.AppLog
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KeyManager implementation using Android Keystore.
 *
 * The EC private key is generated inside and never leaves the Keystore hardware
 * (TEE or StrongBox when available). Only the public key is extractable.
 */
@Singleton
class KeyManagerImpl @Inject constructor() : KeyManager {

    companion object {
        private const val TAG = "KeyManager"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "chat_identity_ec_key"
        private const val EC_CURVE = "secp256r1"
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    override fun generateIdentityKeyPair(): Result<IdentityKeys> {
        return try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                AppLog.w(TAG, "Identity key pair already exists, loading instead of regenerating")
                return loadIdentityKeyPair()
            }

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                KEYSTORE_PROVIDER
            )

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or KeyProperties.PURPOSE_AGREE_KEY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()

            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()

            val publicKeyB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            val fingerprint = computeFingerprint(keyPair.public.encoded)

            AppLog.i(TAG, "Identity key pair generated. Fingerprint: ${fingerprint.take(16)}…")

            Result.Success(IdentityKeys(publicKeyBase64 = publicKeyB64, fingerprint = fingerprint))
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to generate identity key pair", e)
            Result.Failure(AppError.KeyGenerationFailed("EC key pair generation failed", e))
        }
    }

    override fun loadIdentityKeyPair(): Result<IdentityKeys> {
        return try {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                return Result.Failure(AppError.IdentityNotFound("No identity key in Keystore"))
            }

            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: return Result.Failure(AppError.IdentityNotFound("Keystore entry is not a PrivateKeyEntry"))

            val publicKeyB64 = Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
            val fingerprint = computeFingerprint(entry.certificate.publicKey.encoded)

            Result.Success(IdentityKeys(publicKeyBase64 = publicKeyB64, fingerprint = fingerprint))
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to load identity key pair", e)
            Result.Failure(AppError.IdentityNotFound("Failed to load from Keystore: ${e.message}"))
        }
    }

    override fun hasIdentity(): Boolean {
        return try {
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to check identity existence", e)
            false
        }
    }

    override fun getPublicKeyBase64(): String? {
        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
            Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to get public key", e)
            null
        }
    }

    override fun getFingerprint(): String? {
        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
            computeFingerprint(entry.certificate.publicKey.encoded)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to compute fingerprint", e)
            null
        }
    }

    override fun signPayload(data: ByteArray): Result<ByteArray> {
        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: return Result.Failure(AppError.IdentityNotFound("Cannot sign: no identity key"))

            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(entry.privateKey)
            signer.update(data)
            val signatureBytes = signer.sign()

            Result.Success(signatureBytes)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to sign payload", e)
            Result.Failure(AppError.EncryptionFailed("Signing failed", e))
        }
    }

    override fun verifySignature(
        peerPublicKeyBase64: String,
        data: ByteArray,
        signature: ByteArray
    ): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val pubKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.DEFAULT)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubKeyBytes))

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(data)
            verifier.verify(signature)
        } catch (e: Exception) {
            AppLog.w(TAG, "Signature verification failed", e)
            false
        }
    }

    override fun wipeIdentityKeys(): Result<Unit> {
        return try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
                AppLog.i(TAG, "Identity keys permanently wiped from Keystore")
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to wipe identity keys", e)
            Result.Failure(AppError.KeyGenerationFailed("Failed to wipe identity keys", e))
        }
    }

    /**
     * Computes a hex-encoded SHA-256 fingerprint of the raw public key bytes.
     * Format: "AB:CD:EF:12:..." (colon-separated hex pairs)
     */
    private fun computeFingerprint(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
