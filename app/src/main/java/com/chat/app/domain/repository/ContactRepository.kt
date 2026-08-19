package com.chat.app.domain.repository

import com.chat.app.core.common.Result
import com.chat.app.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {

    fun observeAllContacts(): Flow<List<Contact>>

    suspend fun getAllContacts(): List<Contact>

    suspend fun getContact(contactId: String): Contact?

    suspend fun saveContact(contact: Contact): Result<Unit>

    suspend fun updateNickname(contactId: String, nickname: String?): Result<Unit>

    suspend fun setBlocked(contactId: String, isBlocked: Boolean): Result<Unit>

    suspend fun updateVerificationStatus(contactId: String, isVerified: Boolean): Result<Unit>

    suspend fun updateNetworkInfo(contactId: String, ip: String?, port: Int?): Result<Unit>

    suspend fun updateLastSeen(contactId: String, lastSeenAt: Long): Result<Unit>

    suspend fun deleteContact(contactId: String): Result<Unit>

    suspend fun searchContacts(query: String): List<Contact>
}
