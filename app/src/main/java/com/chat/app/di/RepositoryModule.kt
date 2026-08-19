package com.chat.app.di

import com.chat.app.contacts.data.ContactRepositoryImpl
import com.chat.app.domain.repository.ContactRepository
import com.chat.app.domain.repository.ConversationRepository
import com.chat.app.domain.repository.IdentityRepository
import com.chat.app.domain.repository.MessageRepository
import com.chat.app.identity.data.IdentityRepositoryImpl
import com.chat.app.messaging.data.ConversationRepositoryImpl
import com.chat.app.messaging.data.MessageRepositoryImpl
import com.chat.app.presence.data.PresenceRepositoryImpl
import com.chat.app.presence.domain.PresenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindIdentityRepository(
        impl: IdentityRepositoryImpl
    ): IdentityRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(
        impl: ContactRepositoryImpl
    ): ContactRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        impl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: ConversationRepositoryImpl
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindPresenceRepository(
        impl: PresenceRepositoryImpl
    ): PresenceRepository
}
