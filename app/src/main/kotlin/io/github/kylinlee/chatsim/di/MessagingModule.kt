package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.repository.MessagingRepositoryImpl
import io.github.kylinlee.chatsim.repository.MessagingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MessagingModule {
    @Binds
    @Singleton
    abstract fun bindMessagingRepository(impl: MessagingRepositoryImpl): MessagingRepository
}
