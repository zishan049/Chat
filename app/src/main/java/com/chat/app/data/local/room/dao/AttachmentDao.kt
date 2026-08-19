package com.chat.app.data.local.room.dao

import androidx.room.*
import com.chat.app.data.local.room.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments ORDER BY id DESC")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity)

    @Update
    suspend fun update(attachment: AttachmentEntity)

    @Query("UPDATE attachments SET transferProgress = :progress, transferStatus = :status WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, status: String)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: String)
}
