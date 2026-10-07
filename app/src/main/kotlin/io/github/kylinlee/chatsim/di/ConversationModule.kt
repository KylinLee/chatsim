package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.repository.ConversationRepositoryImpl
import io.github.kylinlee.chatsim.repository.ConversationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConversationModule {
    @Binds
    @Singleton
    abstract fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository
}
