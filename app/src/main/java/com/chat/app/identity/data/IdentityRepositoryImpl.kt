package com.chat.app.identity.data

import com.chat.app.core.common.AppError
import com.chat.app.core.common.Result
import com.chat.app.core.common.DispatcherProvider
import com.chat.app.core.logging.AppLog
import com.chat.app.crypto.KeyManager
import com.chat.app.data.local.room.dao.IdentityDao
import com.chat.app.data.local.room.entity.IdentityEntity
import com.chat.app.domain.model.Identity
import com.chat.app.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdentityRepositoryImpl @Inject constructor(
    private val identityDao: IdentityDao,
    private val keyManager: KeyManager,
    private val dispatchers: DispatcherProvider,
) : IdentityRepository {

    companion object {
        private const val TAG = "IdentityRepo"
    }

    override suspend fun createIdentity(
        displayName: String,
        avatarUri: String?,
        age: Int?,
        bio: String?,
    ): Result<Identity> =
        withContext(dispatchers.io) {
            try {
                // Check if identity already exists
                val existing = identityDao.getIdentity()
                if (existing != null) {
                    AppLog.w(TAG, "Identity already exists, returning existing")
                    return@withContext Result.Success(existing.toDomain())
                }

                // Generate crypto key pair
                val keysResult = keyManager.generateIdentityKeyPair()
                if (keysResult is Result.Failure) {
                    return@withContext keysResult
                }
                val keys = (keysResult as Result.Success).data

                // Create and persist identity
                val now = System.currentTimeMillis()
                val entity = IdentityEntity(
                    id = UUID.randomUUID().toString(),
                    displayName = displayName,
                    avatarUri = avatarUri,
                    age = age,
                    bio = bio,
                    publicKeyBase64 = keys.publicKeyBase64,
                    fingerprint = keys.fingerprint,
                    createdAt = now,
                    updatedAt = now,
                )

                identityDao.insert(entity)

                AppLog.i(TAG, "Identity created: ${AppLog.truncatedId(entity.id)}")
                Result.Success(entity.toDomain())
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to create identity", e)
                Result.Failure(AppError.IdentityCreationFailed("Failed to create identity", e))
            }
        }

    override suspend fun getIdentity(): Result<Identity> =
        withContext(dispatchers.io) {
            try {
                val entity = identityDao.getIdentity()
                if (entity != null) {
                    Result.Success(entity.toDomain())
                } else {
                    Result.Failure(AppError.IdentityNotFound())
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to load identity", e)
                Result.Failure(AppError.DatabaseError("Failed to load identity", e))
            }
        }

    override fun observeIdentity(): Flow<Identity?> {
        return identityDao.observeIdentity()
            .map { it?.toDomain() }
            .flowOn(dispatchers.io)
    }

    override suspend fun updateIdentity(
        displayName: String,
        avatarUri: String?,
        age: Int?,
        bio: String?,
    ): Result<Identity> =
        withContext(dispatchers.io) {
            try {
                val existing = identityDao.getIdentity()
                    ?: return@withContext Result.Failure(AppError.IdentityNotFound())

                val updated = existing.copy(
                    displayName = displayName,
                    avatarUri = avatarUri ?: existing.avatarUri,
                    age = age ?: existing.age,
                    bio = bio ?: existing.bio,
                    updatedAt = System.currentTimeMillis(),
                )
                identityDao.update(updated)

                AppLog.i(TAG, "Identity updated: ${AppLog.truncatedId(updated.id)}")
                Result.Success(updated.toDomain())
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to update identity", e)
                Result.Failure(AppError.DatabaseError("Failed to update identity", e))
            }
        }

    override suspend fun hasIdentity(): Boolean =
        withContext(dispatchers.io) {
            try {
                identityDao.getIdentity() != null
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to check identity existence", e)
                false
            }
        }

    override suspend fun deleteIdentity(): Result<Unit> =
        withContext(dispatchers.io) {
            try {
                identityDao.deleteAll()
                keyManager.wipeIdentityKeys()
                AppLog.i(TAG, "Local identity deleted and keys wiped")
                Result.Success(Unit)
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to delete identity", e)
                Result.Failure(AppError.DatabaseError("Failed to delete identity", e))
            }
        }

    private fun IdentityEntity.toDomain(): Identity = Identity(
        id = id,
        displayName = displayName,
        avatarUri = avatarUri,
        age = age,
        bio = bio,
        publicKeyBase64 = publicKeyBase64,
        fingerprint = fingerprint,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
