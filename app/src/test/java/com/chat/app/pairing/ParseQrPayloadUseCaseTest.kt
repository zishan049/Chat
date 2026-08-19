package com.chat.app.pairing

import android.util.Base64
import com.chat.app.core.common.Result
import com.chat.app.crypto.IdentityKeys
import com.chat.app.crypto.KeyManager
import com.chat.app.domain.model.Contact
import com.chat.app.domain.model.Identity
import com.chat.app.domain.repository.ContactRepository
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.pairing.domain.ParseQrPayloadUseCase
import com.chat.app.pairing.domain.model.PairingResult
import com.chat.app.pairing.domain.model.QrPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class ParseQrPayloadUseCaseTest {

    private val selfIdentity = Identity(
        id = "self-uuid",
        displayName = "Alice",
        publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
        fingerprint = "AA:BB:CC",
        createdAt = 1000L
    )

    private val fakeIdentityRepo = object : IdentityRepository {
        override suspend fun createIdentity(displayName: String, avatarUri: String?): Result<Identity> = Result.Success(selfIdentity)
        override suspend fun getIdentity(): Result<Identity> = Result.Success(selfIdentity)
        override suspend fun updateIdentity(displayName: String, avatarUri: String?): Result<Identity> = Result.Success(selfIdentity)
        override suspend fun hasIdentity(): Boolean = true
    }

    private class FakeContactRepo : ContactRepository {
        val contacts = mutableMapOf<String, Contact>()

        override fun observeAllContacts(): Flow<List<Contact>> = flowOf(contacts.values.toList())
        override suspend fun getAllContacts(): List<Contact> = contacts.values.toList()
        override suspend fun getContact(contactId: String): Contact? = contacts[contactId]
        override suspend fun saveContact(contact: Contact): Result<Unit> {
            contacts[contact.id] = contact
            return Result.Success(Unit)
        }
        override suspend fun updateNickname(contactId: String, nickname: String?): Result<Unit> = Result.Success(Unit)
        override suspend fun setBlocked(contactId: String, isBlocked: Boolean): Result<Unit> = Result.Success(Unit)
        override suspend fun updateVerificationStatus(contactId: String, isVerified: Boolean): Result<Unit> = Result.Success(Unit)
        override suspend fun updateNetworkInfo(contactId: String, ip: String?, port: Int?): Result<Unit> = Result.Success(Unit)
        override suspend fun updateLastSeen(contactId: String, lastSeenAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteContact(contactId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun searchContacts(query: String): List<Contact> = emptyList()
    }

    private class FakeKeyManager : KeyManager {
        override fun generateIdentityKeyPair(): Result<IdentityKeys> = Result.Success(IdentityKeys("pk", "fp"))
        override fun loadIdentityKeyPair(): Result<IdentityKeys> = Result.Success(IdentityKeys("pk", "fp"))
        override fun hasIdentity(): Boolean = true
        override fun getPublicKeyBase64(): String? = "pk"
        override fun getFingerprint(): String? = "fp"
        override fun signPayload(data: ByteArray): Result<ByteArray> = Result.Success(ByteArray(64))
        override fun verifySignature(peerPublicKeyBase64: String, data: ByteArray, signature: ByteArray): Boolean {
            // For testing: signatures with first byte != 0xFF are considered valid
            return signature.isNotEmpty() && signature[0] != 0xFF.toByte()
        }
    }

    @Test
    fun testSelfScanDetection() = runBlocking {
        val contactRepo = FakeContactRepo()
        val keyManager = FakeKeyManager()
        val useCase = ParseQrPayloadUseCase(fakeIdentityRepo, contactRepo, keyManager)

        val selfPayload = QrPayload(
            id = "self-uuid",
            displayName = "Alice",
            publicKeyBase64 = java.util.Base64.getEncoder().encodeToString(ByteArray(32)),
            fingerprint = "AA:BB",
            signature = java.util.Base64.getEncoder().encodeToString(ByteArray(64))
        )

        val result = useCase(selfPayload.toJson())
        assertTrue("Scanning self QR code must return SelfScan", result is PairingResult.SelfScan)
    }

    @Test
    fun testNewContactPairingSuccess() = runBlocking {
        val contactRepo = FakeContactRepo()
        val keyManager = FakeKeyManager()
        val useCase = ParseQrPayloadUseCase(fakeIdentityRepo, contactRepo, keyManager)

        val bobPubKeyBytes = ByteArray(32) { (it + 1).toByte() }
        val bobPubKeyB64 = java.util.Base64.getEncoder().encodeToString(bobPubKeyBytes)
        val bobPayload = QrPayload(
            id = "bob-uuid",
            displayName = "Bob",
            publicKeyBase64 = bobPubKeyB64,
            fingerprint = "BO:B1:23",
            lanIp = "192.168.1.55",
            port = 47832,
            signature = java.util.Base64.getEncoder().encodeToString(ByteArray(64))
        )

        val result = useCase(bobPayload.toJson())
        assertTrue("New peer must be successfully parsed", result is PairingResult.Success)
        val success = result as PairingResult.Success
        assertTrue("Must be marked as new contact", success.isNew)
        assertEquals("Bob", success.contact.displayName)
        assertTrue("Must be trusted on first use", success.contact.isVerified)
    }

    @Test
    fun testTofuKeyMismatchWarning() = runBlocking {
        val contactRepo = FakeContactRepo()
        val keyManager = FakeKeyManager()
        val useCase = ParseQrPayloadUseCase(fakeIdentityRepo, contactRepo, keyManager)

        // Store Bob with Initial Key
        val initialKeyBytes = ByteArray(32) { 1 }
        val initialKeyB64 = java.util.Base64.getEncoder().encodeToString(initialKeyBytes)
        val initialDigest = java.security.MessageDigest.getInstance("SHA-256").digest(initialKeyBytes)
        val initialFp = initialDigest.joinToString(":") { "%02X".format(it) }

        val existingBob = Contact(
            id = "bob-uuid",
            displayName = "Bob",
            publicKeyBase64 = initialKeyB64,
            fingerprint = initialFp,
            isVerified = true,
            pairedAt = 1000L
        )
        contactRepo.saveContact(existingBob)

        // Now Bob presents a DIFFERENT key (e.g. reinstalled or MITM)
        val newKeyBytes = ByteArray(32) { 2 }
        val newKeyB64 = java.util.Base64.getEncoder().encodeToString(newKeyBytes)
        val newDigest = java.security.MessageDigest.getInstance("SHA-256").digest(newKeyBytes)
        val newFp = newDigest.joinToString(":") { "%02X".format(it) }

        val changedPayload = QrPayload(
            id = "bob-uuid",
            displayName = "Bob",
            publicKeyBase64 = newKeyB64,
            fingerprint = newFp,
            signature = java.util.Base64.getEncoder().encodeToString(ByteArray(64))
        )

        val result = useCase(changedPayload.toJson())
        assertTrue("Key change MUST trigger TOFU KeyMismatchWarning and NOT silently accept", result is PairingResult.KeyMismatchWarning)
        val warning = result as PairingResult.KeyMismatchWarning
        assertEquals(initialFp, warning.existingContact.fingerprint)
        assertEquals(newFp, warning.newFingerprint)
    }

    @Test
    fun testInvalidSignatureRejection() = runBlocking {
        val contactRepo = FakeContactRepo()
        val keyManager = FakeKeyManager()
        val useCase = ParseQrPayloadUseCase(fakeIdentityRepo, contactRepo, keyManager)

        val badSig = ByteArray(64) { 0xFF.toByte() }
        val payload = QrPayload(
            id = "eve-uuid",
            displayName = "Eve",
            publicKeyBase64 = java.util.Base64.getEncoder().encodeToString(ByteArray(32)),
            fingerprint = "EV:E1",
            signature = java.util.Base64.getEncoder().encodeToString(badSig)
        )

        val result = useCase(payload.toJson())
        assertTrue("Invalid signature must return InvalidSignature", result is PairingResult.InvalidSignature)
    }

    @Test
    fun testMalformedPayloadRejection() = runBlocking {
        val contactRepo = FakeContactRepo()
        val keyManager = FakeKeyManager()
        val useCase = ParseQrPayloadUseCase(fakeIdentityRepo, contactRepo, keyManager)

        val result = useCase("NOT_JSON_DATA_12345")
        assertTrue("Malformed payload must return MalformedPayload", result is PairingResult.MalformedPayload)
    }
}
