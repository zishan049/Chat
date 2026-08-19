package com.chat.app.crypto

import com.chat.app.transport.protocol.Envelope
import com.chat.app.transport.protocol.EnvelopeSerializer
import com.chat.app.transport.protocol.PacketType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SecureMessagingFlowTest {

    @Test
    fun testEndToEndEncryptedEnvelopeFlow() = runBlocking {
        // 1. Generate Alice and Bob EC Keypairs (secp256r1)
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val aliceKeyPair = kpg.generateKeyPair()
        val bobKeyPair = kpg.generateKeyPair()

        val alicePubB64 = Base64.getEncoder().encodeToString(aliceKeyPair.public.encoded)
        val bobPubB64 = Base64.getEncoder().encodeToString(bobKeyPair.public.encoded)

        // 2. Perform ECDH on both ends
        val kaAlice = KeyAgreement.getInstance("ECDH")
        kaAlice.init(aliceKeyPair.private)
        kaAlice.doPhase(bobKeyPair.public, true)
        val aliceSharedSecret = kaAlice.generateSecret()
        val aliceAesKey = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(aliceSharedSecret), "AES")

        val kaBob = KeyAgreement.getInstance("ECDH")
        kaBob.init(bobKeyPair.private)
        kaBob.doPhase(aliceKeyPair.public, true)
        val bobSharedSecret = kaBob.generateSecret()
        val bobAesKey = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(bobSharedSecret), "AES")

        // Keys MUST be mathematically identical
        assertArrayEquals("Derived AES keys must match", aliceAesKey.encoded, bobAesKey.encoded)

        // 3. Alice encrypts message text
        val plaintext = "Meeting at 18:00 on the secure channel 🔒"
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encryptCipher.init(Cipher.ENCRYPT_MODE, aliceAesKey, GCMParameterSpec(128, iv))
        val cipherBytes = encryptCipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        val ciphertextB64 = Base64.getEncoder().encodeToString(combined)

        // 4. Construct Envelope & Serialize
        val envelope = Envelope(
            messageId = "msg-101",
            senderId = "alice-uuid",
            recipientId = "bob-uuid",
            type = PacketType.TEXT,
            timestamp = 1770001000L,
            ciphertextBase64 = ciphertextB64,
            senderPublicKey = alicePubB64
        )
        val wireJson = EnvelopeSerializer.toJson(envelope)

        // 5. Bob receives and deserializes wire JSON
        val receivedEnvelope = EnvelopeSerializer.fromJson(wireJson)
        assertNotNull(receivedEnvelope)

        // 6. Bob decrypts payload using his ECDH session key
        val receivedCombined = Base64.getDecoder().decode(receivedEnvelope?.ciphertextBase64)
        val receivedIv = ByteArray(12)
        System.arraycopy(receivedCombined, 0, receivedIv, 0, 12)
        val receivedCipherBytes = ByteArray(receivedCombined.size - 12)
        System.arraycopy(receivedCombined, 12, receivedCipherBytes, 0, receivedCipherBytes.size)

        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, bobAesKey, GCMParameterSpec(128, receivedIv))
        val decryptedBytes = decryptCipher.doFinal(receivedCipherBytes)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals("Decrypted text must match Alice's original plaintext exactly", plaintext, decryptedText)
    }

    @Test
    fun testEveCannotDecryptEnvelope() = runBlocking {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val aliceKeyPair = kpg.generateKeyPair()
        val bobKeyPair = kpg.generateKeyPair()
        val eveKeyPair = kpg.generateKeyPair()

        // Alice computes key with Bob
        val kaAlice = KeyAgreement.getInstance("ECDH")
        kaAlice.init(aliceKeyPair.private)
        kaAlice.doPhase(bobKeyPair.public, true)
        val aliceAesKey = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(kaAlice.generateSecret()), "AES")

        // Eve tries to compute key with Bob (pretending to be Alice)
        val kaEve = KeyAgreement.getInstance("ECDH")
        kaEve.init(eveKeyPair.private)
        kaEve.doPhase(bobKeyPair.public, true)
        val eveAesKey = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(kaEve.generateSecret()), "AES")

        // Alice encrypts
        val plaintext = "Secret payload"
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aliceAesKey, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Eve tries to decrypt Alice's ciphertext
        var failed = false
        try {
            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipher.init(Cipher.DECRYPT_MODE, eveAesKey, GCMParameterSpec(128, iv))
            decryptCipher.doFinal(cipherBytes)
        } catch (_: Exception) {
            failed = true
        }

        assertTrue("Eve must fail to decrypt Alice's message", failed)
    }
}
