package com.chat.app.crypto

import com.chat.app.core.common.AppError
import com.chat.app.core.common.Result
import com.chat.app.core.logging.AppLog
import com.chat.app.data.local.room.dao.SessionDao
import com.chat.app.data.local.room.entity.SessionEntity
import com.chat.app.domain.repository.ContactRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sessionDao: SessionDao,
    private val contactRepository: ContactRepository
) {

    companion object {
        private const val TAG = "SessionManager"
    }

    private val activeSessions = ConcurrentHashMap<String, CryptoSession>()
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Obtains or establishes an active CryptoSession for the specified contact.
     * Uses ECDH key agreement with the contact's verified EC public key.
     */
    suspend fun getOrCreateSession(contactId: String): Result<CryptoSession> {
        val existing = activeSessions[contactId]
        if (existing != null && existing.isEstablished()) {
            return Result.Success(existing)
        }

        val lock = sessionLocks.computeIfAbsent(contactId) { Mutex() }
        return lock.withLock {
            val doubleCheck = activeSessions[contactId]
            if (doubleCheck != null && doubleCheck.isEstablished()) {
                return@withLock Result.Success(doubleCheck)
            }

            // Retrieve peer's public key from contacts repository
            val contact = contactRepository.getContact(contactId)
                ?: return@withLock Result.Failure(AppError.SessionEstablishmentFailed("Contact $contactId not found in database"))

            if (contact.publicKeyBase64.isBlank()) {
                return@withLock Result.Failure(AppError.SessionEstablishmentFailed("Contact $contactId has no public key"))
            }

            val session = CryptoSessionImpl()
            val establishResult = session.establish(contact.publicKeyBase64)
            if (establishResult is Result.Failure) {
                return@withLock establishResult
            }

            // Persist session metadata
            val now = System.currentTimeMillis()
            sessionDao.insert(
                SessionEntity(
                    id = UUID.randomUUID().toString(),
                    contactId = contactId,
                    peerPublicKeyBase64 = contact.publicKeyBase64,
                    establishedAt = now,
                    lastUsedAt = now
                )
            )

            activeSessions[contactId] = session
            AppLog.i(TAG, "Active CryptoSession cached for contact ${AppLog.truncatedId(contactId)}")
            Result.Success(session)
        }
    }

    /**
     * Evicts and zeroes the session key material when a contact is blocked or deleted.
     */
    suspend fun invalidateSession(contactId: String) {
        val session = activeSessions.remove(contactId)
        session?.destroy()
        sessionDao.deleteByContactId(contactId)
        AppLog.d(TAG, "Session invalidated for contact ${AppLog.truncatedId(contactId)}")
    }

    /**
     * Clears all in-memory sessions on logout / factory reset.
     */
    fun clearAllSessions() {
        activeSessions.values.forEach { it.destroy() }
        activeSessions.clear()
        sessionLocks.clear()
    }
}
