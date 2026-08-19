package com.chat.app.data.local.room.dao

import androidx.room.*
import com.chat.app.data.local.room.entity.IdentityEntity

@Dao
interface IdentityDao {

    @Query("SELECT * FROM identity LIMIT 1")
    suspend fun getIdentity(): IdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(identity: IdentityEntity)

    @Update
    suspend fun update(identity: IdentityEntity)

    @Query("DELETE FROM identity")
    suspend fun deleteAll()
}
