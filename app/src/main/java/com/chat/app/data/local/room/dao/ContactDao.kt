package com.chat.app.data.local.room.dao

import androidx.room.*
import com.chat.app.data.local.room.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE isBlocked = 0 ORDER BY displayName ASC")
    fun observeAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isBlocked = 1 ORDER BY displayName ASC")
    fun observeBlockedContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    suspend fun getAllContacts(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :contactId LIMIT 1")
    suspend fun getById(contactId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE displayName LIKE '%' || :query || '%' OR nickname LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("UPDATE contacts SET isBlocked = :isBlocked, updatedAt = :updatedAt WHERE id = :contactId")
    suspend fun setBlocked(contactId: String, isBlocked: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE contacts SET nickname = :nickname, updatedAt = :updatedAt WHERE id = :contactId")
    suspend fun updateNickname(contactId: String, nickname: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE contacts SET lastKnownIp = :ip, lastKnownPort = :port, updatedAt = :updatedAt WHERE id = :contactId")
    suspend fun updateNetworkInfo(contactId: String, ip: String?, port: Int?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE contacts SET lastSeenAt = :lastSeenAt WHERE id = :contactId")
    suspend fun updateLastSeen(contactId: String, lastSeenAt: Long)

    @Query("UPDATE contacts SET isVerified = :isVerified, updatedAt = :updatedAt WHERE id = :contactId")
    suspend fun updateVerificationStatus(contactId: String, isVerified: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteById(contactId: String)
}
