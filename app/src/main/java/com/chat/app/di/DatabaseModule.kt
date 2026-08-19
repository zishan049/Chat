package com.chat.app.di

import android.content.Context
import androidx.room.Room
import com.chat.app.data.local.room.ChatDatabase
import com.chat.app.data.local.room.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideChatDatabase(
        @ApplicationContext context: Context
    ): ChatDatabase {
        return Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            "chat_database_v2.db"
        ).build()
    }

    @Provides
    fun provideIdentityDao(database: ChatDatabase): IdentityDao = database.identityDao()

    @Provides
    fun provideContactDao(database: ChatDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideConversationDao(database: ChatDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: ChatDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideSessionDao(database: ChatDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideAttachmentDao(database: ChatDatabase): AttachmentDao = database.attachmentDao()
}
