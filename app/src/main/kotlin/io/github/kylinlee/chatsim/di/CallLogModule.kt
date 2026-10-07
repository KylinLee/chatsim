package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.repository.CallLogRepositoryImpl
import io.github.kylinlee.chatsim.repository.CallLogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CallLogModule {
    @Binds
    @Singleton
    abstract fun bindCallLogRepository(impl: CallLogRepositoryImpl): CallLogRepository
}
