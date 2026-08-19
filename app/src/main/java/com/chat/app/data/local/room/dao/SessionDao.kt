package com.chat.app.data.local.room.dao

import androidx.room.*
import com.chat.app.data.local.room.entity.SessionEntity

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions WHERE contactId = :contactId LIMIT 1")
    suspend fun getByContactId(contactId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("UPDATE sessions SET lastUsedAt = :timestamp WHERE contactId = :contactId")
    suspend fun updateLastUsed(contactId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM sessions WHERE contactId = :contactId")
    suspend fun deleteByContactId(contactId: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
