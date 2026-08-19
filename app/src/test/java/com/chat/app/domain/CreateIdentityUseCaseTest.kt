package com.chat.app.domain

import com.chat.app.core.common.Result
import com.chat.app.domain.model.Identity
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.identity.domain.CreateIdentityUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CreateIdentityUseCaseTest {

    private val fakeRepo = object : IdentityRepository {
        var storedIdentity: Identity? = null

        override suspend fun createIdentity(displayName: String, avatarUri: String?): Result<Identity> {
            val identity = Identity(
                id = "test-uuid",
                displayName = displayName,
                avatarUri = avatarUri,
                publicKeyBase64 = "MIIB...",
                fingerprint = "AA:BB:CC",
                createdAt = 1000L
            )
            storedIdentity = identity
            return Result.Success(identity)
        }

        override suspend fun getIdentity(): Result<Identity> {
            return storedIdentity?.let { Result.Success(it) }
                ?: Result.Failure(com.chat.app.core.common.AppError.IdentityNotFound())
        }

        override suspend fun updateIdentity(displayName: String, avatarUri: String?): Result<Identity> {
            val updated = storedIdentity!!.copy(displayName = displayName)
            storedIdentity = updated
            return Result.Success(updated)
        }

        override suspend fun hasIdentity(): Boolean = storedIdentity != null
    }

    @Test
    fun testBlankDisplayNameRejection() = runBlocking {
        val useCase = CreateIdentityUseCase(fakeRepo)
        val result = useCase(displayName = "   ")
        assertTrue("Blank display name must be rejected", result is Result.Failure)
    }

    @Test
    fun testSuccessfulIdentityCreation() = runBlocking {
        val useCase = CreateIdentityUseCase(fakeRepo)
        val result = useCase(displayName = "Alice")
        assertTrue("Valid display name creates identity", result is Result.Success)
        assertEquals("Alice", (result as Result.Success).data.displayName)
    }
}
