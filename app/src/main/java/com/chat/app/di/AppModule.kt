package com.chat.app.di

import com.chat.app.core.common.DefaultDispatcherProvider
import com.chat.app.core.common.DispatcherProvider
import com.chat.app.crypto.KeyManager
import com.chat.app.crypto.KeyManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(
        impl: DefaultDispatcherProvider
    ): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindKeyManager(
        impl: KeyManagerImpl
    ): KeyManager
}
