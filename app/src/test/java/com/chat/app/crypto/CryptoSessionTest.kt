package com.chat.app.crypto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoSessionTest {

    @Test
    fun testHardFailureOnTamperedCiphertext() = runBlocking {
        // Generate two EC key pairs simulating Alice and Bob
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val aliceKeyPair = kpg.generateKeyPair()
        val bobKeyPair = kpg.generateKeyPair()

        // Derive shared secret manually to test AES-GCM behavior
        val kaAlice = KeyAgreement.getInstance("ECDH")
        kaAlice.init(aliceKeyPair.private)
        kaAlice.doPhase(bobKeyPair.public, true)
        val aliceSecret = kaAlice.generateSecret()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val aesKeyBytes = md.digest(aliceSecret)
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        val plaintext = "Top secret message 12345".toByteArray(Charsets.UTF_8)
        val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // Combine IV + Ciphertext
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        // Tamper with one byte in the ciphertext body
        combined[combined.size - 1] = (combined[combined.size - 1].toInt() xor 0xFF).toByte()

        // Attempt decryption
        var failed = false
        try {
            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val extractedIv = ByteArray(12)
            System.arraycopy(combined, 0, extractedIv, 0, 12)
            val extractedCipher = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, extractedCipher, 0, extractedCipher.size)

            decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, extractedIv))
            decryptCipher.doFinal(extractedCipher)
        } catch (e: Exception) {
            failed = true
        }

        assertTrue("Tampered ciphertext MUST fail GCM tag authentication and throw", failed)
    }

    @Test
    fun testHardFailureOnWrongKey() = runBlocking {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val aliceKeyPair = kpg.generateKeyPair()
        val bobKeyPair = kpg.generateKeyPair()
        val eveKeyPair = kpg.generateKeyPair()

        val kaAlice = KeyAgreement.getInstance("ECDH")
        kaAlice.init(aliceKeyPair.private)
        kaAlice.doPhase(bobKeyPair.public, true)
        val aliceKey = SecretKeySpec(java.security.MessageDigest.getInstance("SHA-256").digest(kaAlice.generateSecret()), "AES")

        val kaEve = KeyAgreement.getInstance("ECDH")
        kaEve.init(eveKeyPair.private)
        kaEve.doPhase(bobKeyPair.public, true)
        val eveKey = SecretKeySpec(java.security.MessageDigest.getInstance("SHA-256").digest(kaEve.generateSecret()), "AES")

        val plaintext = "Confidential data".toByteArray(Charsets.UTF_8)
        val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aliceKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // Eve attempts to decrypt Alice's ciphertext
        var failed = false
        try {
            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipher.init(Cipher.DECRYPT_MODE, eveKey, GCMParameterSpec(128, iv))
            decryptCipher.doFinal(ciphertext)
        } catch (e: Exception) {
            failed = true
        }

        assertTrue("Decryption with wrong key MUST fail GCM authentication", failed)
    }
}
