package com.chat.app.contacts.data

import com.chat.app.core.common.AppError
import com.chat.app.core.common.DispatcherProvider
import com.chat.app.core.common.Result
import com.chat.app.data.local.room.dao.ContactDao
import com.chat.app.data.local.room.entity.ContactEntity
import com.chat.app.domain.model.Contact
import com.chat.app.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contactDao: ContactDao,
    private val dispatchers: DispatcherProvider
) : ContactRepository {

    override fun observeAllContacts(): Flow<List<Contact>> {
        return contactDao.observeAllContacts()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override fun observeBlockedContacts(): Flow<List<Contact>> {
        return contactDao.observeBlockedContacts()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getAllContacts(): List<Contact> = withContext(dispatchers.io) {
        contactDao.getAllContacts().map { it.toDomain() }
    }

    override suspend fun getContact(contactId: String): Contact? = withContext(dispatchers.io) {
        contactDao.getById(contactId)?.toDomain()
    }

    override suspend fun saveContact(contact: Contact): Result<Unit> = withContext(dispatchers.io) {
        try {
            contactDao.insert(contact.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to save contact", e))
        }
    }

    override suspend fun updateNickname(contactId: String, nickname: String?): Result<Unit> = withContext(dispatchers.io) {
        try {
            contactDao.updateNickname(contactId, nickname?.trim()?.ifBlank { null })
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to update nickname", e))
        }
    }

    override suspend fun setBlocked(contactId: String, isBlocked: Boolean): Result<Unit> = withContext(dispatchers.io) {
        try {
            contactDao.setBlocked(contactId, isBlocked)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to set blocked state", e))
        }
    }

    override suspend fun updateVerificationStatus(contactId: String, isVerified: Boolean): Result<Unit> = withContext(dispatchers.io) {
        try {
            contactDao.updateVerificationStatus(contactId, isVerified)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to update verification status", e))
        }
    }

    override suspend fun updateNetworkInfo(contactId: String, ip: String?, port: Int?): Result<Unit> = withContext(dispatchers.io) {
        try {
            contactDao.updateNetworkInfo(contactId, ip, port)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to update network info", e))
        }
    }

    override suspend fun updateLastSeen(contactId: String, lastSeenAt: Long): Result<Unit> = withContext(dispatchers.io) {
        try {
            contactDao.updateLastSeen(contactId, lastSeenAt)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to update last seen", e))
        }
    }

    override suspend fun deleteContact(contactId: String): Result<Unit> = withContext(dispatchers.io) {
        try {
            contactDao.deleteById(contactId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DatabaseError("Failed to delete contact", e))
        }
    }

    override suspend fun searchContacts(query: String): List<Contact> = withContext(dispatchers.io) {
        contactDao.search(query).map { it.toDomain() }
    }

    private fun ContactEntity.toDomain(): Contact = Contact(
        id = id,
        displayName = displayName,
        nickname = nickname,
        avatarUri = avatarUri,
        publicKeyBase64 = publicKeyBase64,
        fingerprint = fingerprint,
        age = age,
        bio = bio,
        isBlocked = isBlocked,
        isVerified = isVerified,
        lastKnownIp = lastKnownIp,
        lastKnownPort = lastKnownPort,
        lastSeenAt = lastSeenAt,
        pairedAt = pairedAt,
        updatedAt = updatedAt
    )

    private fun Contact.toEntity(): ContactEntity = ContactEntity(
        id = id,
        displayName = displayName,
        nickname = nickname,
        avatarUri = avatarUri,
        publicKeyBase64 = publicKeyBase64,
        fingerprint = fingerprint,
        age = age,
        bio = bio,
        isBlocked = isBlocked,
        isVerified = isVerified,
        lastKnownIp = lastKnownIp,
        lastKnownPort = lastKnownPort,
        lastSeenAt = lastSeenAt,
        pairedAt = pairedAt,
        updatedAt = updatedAt
    )
}
