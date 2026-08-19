package com.chat.app.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chat.app.data.local.room.dao.*
import com.chat.app.data.local.room.entity.*

/**
 * Room database for the Chat application.
 *
 * Version 1 — fresh schema for the rebuild.
 * No fallbackToDestructiveMigration() — migration failures must be visible.
 * exportSchema = true for test validation.
 */
@Database(
    entities = [
        IdentityEntity::class,
        ContactEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        MessageDeliveryEntity::class,
        SessionEntity::class,
        AttachmentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun contactDao(): ContactDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao
    abstract fun attachmentDao(): AttachmentDao
}
