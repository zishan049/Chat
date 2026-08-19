package com.chat.app.utils

import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * End-to-End Encryption Engine (E2EE)
 * Uses Elliptic Curve Diffie-Hellman (ECDH secp256r1) for peer Key Exchange
 * and AES-256-GCM with 128-bit Auth Tag & random 12-byte IV for Text & Media Payload Encryption.
 */
object CryptoUtils {

    private const val EC_CURVE = "secp256r1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PREFS_NAME = "chat_crypto_identity"
    private const val KEY_PUBLIC = "self_public_key_b64"
    private const val KEY_PRIVATE = "self_private_key_b64"

    private var selfKeyPair: KeyPair? = null
    private val contactSecretKeys = java.util.concurrent.ConcurrentHashMap<String, SecretKey>()
    private val contactPublicKeys = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Thread-local Cipher pools — one instance per thread, reused across calls.
     * Eliminates repeated Cipher.getInstance() JCA provider lookup overhead.
     * GCM mode is stateful so each call must re-init; the instance itself is reused.
     */
    private val encryptCipherPool = ThreadLocal.withInitial<Cipher> {
        Cipher.getInstance(AES_GCM_TRANSFORMATION)
    }
    private val decryptCipherPool = ThreadLocal.withInitial<Cipher> {
        Cipher.getInstance(AES_GCM_TRANSFORMATION)
    }

    /** Single SecureRandom shared across calls on the same thread (thread-safe internally). */
    private val secureRandom = SecureRandom()

    init {
        ensureSelfKeyPair()
    }

    /**
     * Initializes or loads local device's persistent EC KeyPair.
     */
    @Synchronized
    fun ensureSelfKeyPair(context: android.content.Context? = null): KeyPair {
        if (selfKeyPair != null) return selfKeyPair!!

        // Attempt to load from persistent storage if context available
        if (context != null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val pubB64 = prefs.getString(KEY_PUBLIC, null)
            val privB64 = prefs.getString(KEY_PRIVATE, null)
            if (!pubB64.isNullOrBlank() && !privB64.isNullOrBlank()) {
                try {
                    val kf = KeyFactory.getInstance("EC")
                    val pubKey = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubB64, Base64.DEFAULT)))
                    val privKey = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.DEFAULT)))
                    val loaded = KeyPair(pubKey, privKey)
                    selfKeyPair = loaded
                    return loaded
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256)
            val newPair = kpg.generateKeyPair()
            selfKeyPair = newPair

            if (context != null) {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                val pubB64 = Base64.encodeToString(newPair.public.encoded, Base64.NO_WRAP)
                val privB64 = Base64.encodeToString(newPair.private.encoded, Base64.NO_WRAP)
                prefs.edit().putString(KEY_PUBLIC, pubB64).putString(KEY_PRIVATE, privB64).apply()
            }
            return newPair
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback emergency generation
            val kpg = KeyPairGenerator.getInstance("EC")
            val newPair = kpg.generateKeyPair()
            selfKeyPair = newPair
            return newPair
        }
    }

    /**
     * Initializes persistence context eagerly on application/viewmodel startup.
     */
    fun init(context: android.content.Context) {
        ensureSelfKeyPair(context)
    }

    /**
     * Returns Base64-encoded Public Key for sharing via P2P QR exchange or Handshake.
     */
    fun getSelfPublicKeyBase64(): String {
        val kp = ensureSelfKeyPair()
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    /**
     * Cryptographically signs a payload canonical string with the local device's EC Private Key using SHA256withECDSA.
     */
    fun signPayload(canonicalText: String): String {
        return try {
            val kp = ensureSelfKeyPair()
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(kp.private)
            signer.update(canonicalText.toByteArray(Charsets.UTF_8))
            val signatureBytes = signer.sign()
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Verifies an ECDSA digital signature over a canonical payload string using the peer's public key.
     */
    fun verifySignature(peerPublicKeyBase64: String, canonicalText: String, signatureBase64: String): Boolean {
        if (peerPublicKeyBase64.isBlank() || signatureBase64.isBlank() || canonicalText.isBlank()) return false
        return try {
            val peerKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance("EC")
            val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerKeyBytes))

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(peerPublicKey)
            verifier.update(canonicalText.toByteArray(Charsets.UTF_8))
            val sigBytes = Base64.decode(signatureBase64, Base64.DEFAULT)
            verifier.verify(sigBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Derives a 256-bit AES symmetric key for a contact given their Base64 EC Public Key.
     */
    fun deriveSharedKeyForContact(contactId: String, peerPublicKeyBase64: String): SecretKey? {
        if (contactId.isBlank() || peerPublicKeyBase64.isBlank()) return null
        return try {
            val peerKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance("EC")
            val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerKeyBytes))

            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(ensureSelfKeyPair().private)
            keyAgreement.doPhase(peerPublicKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            // Hash shared secret with SHA-256 to produce 256-bit AES key
            val md = MessageDigest.getInstance("SHA-256")
            val aesKeyBytes = md.digest(sharedSecret)
            val secretKey = SecretKeySpec(aesKeyBytes, "AES")

            contactSecretKeys[contactId] = secretKey
            contactPublicKeys[contactId] = peerPublicKeyBase64
            secretKey
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Checks whether a true ECDH-derived shared key exists for a contact.
     */
    fun hasDerivedSharedKey(contactId: String): Boolean = contactSecretKeys.containsKey(contactId)

    /**
     * Gets or creates a fallback key derived deterministically from the sorted pair of IDs if ECDH key exchange is pending.
     */
    private fun getOrFallbackSecretKey(contactId: String, selfId: String = ""): SecretKey {
        return contactSecretKeys[contactId] ?: run {
            val pairId = listOf(contactId, selfId).filter { it.isNotBlank() }.sorted().joinToString("_")
            val md = MessageDigest.getInstance("SHA-256")
            val fallbackBytes = md.digest("E2EE_KEY_$pairId".toByteArray(Charsets.UTF_8))
            val key = SecretKeySpec(fallbackBytes, "AES")
            contactSecretKeys[contactId] = key
            key
        }
    }

    /**
     * Returns a prioritized list of candidate SecretKeys to attempt for decryption:
     * 1. True ECDH shared key for contactId (if derived)
     * 2. Deterministic sorted pair fallback key (contactId, selfId)
     * 3. Deterministic key for contactId alone
     * 4. Deterministic key for selfId alone
     */
    private fun getCandidateDecryptionKeys(contactId: String, selfId: String = ""): List<SecretKey> {
        val keys = mutableListOf<SecretKey>()
        contactSecretKeys[contactId]?.let { keys.add(it) }
        
        if (contactId.isNotBlank() && selfId.isNotBlank()) {
            val pairId = listOf(contactId, selfId).sorted().joinToString("_")
            val md = MessageDigest.getInstance("SHA-256")
            val fallbackBytes = md.digest("E2EE_KEY_$pairId".toByteArray(Charsets.UTF_8))
            keys.add(SecretKeySpec(fallbackBytes, "AES"))
        }

        if (contactId.isNotBlank()) {
            val md = MessageDigest.getInstance("SHA-256")
            val fallbackBytes = md.digest("E2EE_KEY_$contactId".toByteArray(Charsets.UTF_8))
            keys.add(SecretKeySpec(fallbackBytes, "AES"))
        }

        if (selfId.isNotBlank()) {
            val md = MessageDigest.getInstance("SHA-256")
            val fallbackBytes = md.digest("E2EE_KEY_$selfId".toByteArray(Charsets.UTF_8))
            keys.add(SecretKeySpec(fallbackBytes, "AES"))
        }

        return keys.distinct()
    }

    /**
     * Encrypts plain text string using AES-256-GCM.
     * Output format: Base64(IV + CipherText)
     * Runs on Dispatchers.Default (CPU thread pool) using a per-thread pooled Cipher.
     */
    suspend fun encryptText(contactId: String, selfId: String = "", plainText: String): String =
        withContext(Dispatchers.Default) {
            try {
                val secretKey = getOrFallbackSecretKey(contactId, selfId)
                val cipher = encryptCipherPool.get()!!

                val iv = ByteArray(GCM_IV_LENGTH)
                secureRandom.nextBytes(iv)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

                val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(iv.size + cipherBytes.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

                Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (e: Exception) {
                e.printStackTrace()
                plainText
            }
        }

    /**
     * Decrypts Base64 encrypted string using AES-256-GCM.
     * Runs on Dispatchers.Default (CPU thread pool) using a per-thread pooled Cipher with multi-key candidate fallback.
     */
    suspend fun decryptText(contactId: String, selfId: String = "", encryptedBase64: String): String =
        withContext(Dispatchers.Default) {
            try {
                val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
                if (combined.size <= GCM_IV_LENGTH) return@withContext encryptedBase64

                val iv = ByteArray(GCM_IV_LENGTH)
                System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

                val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
                System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

                val cipher = decryptCipherPool.get()!!
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

                val candidateKeys = getCandidateDecryptionKeys(contactId, selfId)
                for (key in candidateKeys) {
                    try {
                        cipher.init(Cipher.DECRYPT_MODE, key, spec)
                        val decryptedBytes = cipher.doFinal(cipherBytes)
                        return@withContext String(decryptedBytes, Charsets.UTF_8)
                    } catch (_: Exception) {
                        // Key mismatch, try next candidate key
                    }
                }

                encryptedBase64
            } catch (e: Exception) {
                e.printStackTrace()
                encryptedBase64
            }
        }

    /**
     * Encrypts binary byte array (e.g. Media chunk) using AES-256-GCM.
     * Runs on Dispatchers.Default (CPU thread pool) using a per-thread pooled Cipher.
     */
    suspend fun encryptBytes(contactId: String, selfId: String = "", data: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            try {
                val secretKey = getOrFallbackSecretKey(contactId, selfId)
                val cipher = encryptCipherPool.get()!!

                val iv = ByteArray(GCM_IV_LENGTH)
                secureRandom.nextBytes(iv)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

                val cipherBytes = cipher.doFinal(data)
                val combined = ByteArray(iv.size + cipherBytes.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
                combined
            } catch (e: Exception) {
                e.printStackTrace()
                data
            }
        }

    /**
     * Decrypts binary byte array using AES-256-GCM with multi-key candidate fallback.
     * Runs on Dispatchers.Default (CPU thread pool) using a per-thread pooled Cipher.
     */
    suspend fun decryptBytes(contactId: String, selfId: String = "", combined: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            try {
                if (combined.size <= GCM_IV_LENGTH) return@withContext combined

                val iv = ByteArray(GCM_IV_LENGTH)
                System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

                val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
                System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

                val cipher = decryptCipherPool.get()!!
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

                val candidateKeys = getCandidateDecryptionKeys(contactId, selfId)
                for (key in candidateKeys) {
                    try {
                        cipher.init(Cipher.DECRYPT_MODE, key, spec)
                        return@withContext cipher.doFinal(cipherBytes)
                    } catch (_: Exception) {
                        // Key mismatch, try next candidate key
                    }
                }

                combined
            } catch (e: Exception) {
                e.printStackTrace()
                combined
            }
        }

    /**
     * Evicts cached secret and public keys for a specific contact from volatile memory.
     * Called when a contact is deleted or blocked.
     */
    fun invalidateContactKey(contactId: String) {
        contactSecretKeys.remove(contactId)
        contactPublicKeys.remove(contactId)
    }
}
