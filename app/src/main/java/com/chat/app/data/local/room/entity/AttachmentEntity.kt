package com.chat.app.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["messageId"], unique = true)
    ]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val mediaType: String = "FILE", // IMAGE, VIDEO, AUDIO, FILE
    val fileName: String,
    val fileSize: Long,
    val localUri: String,
    val mimeType: String = "application/octet-stream",
    val durationMs: Long? = null,
    val transferStatus: String = "COMPLETE", // PENDING, TRANSFERRING, COMPLETE, FAILED
    @ColumnInfo(defaultValue = "1.0") val transferProgress: Float = 1.0f
)
