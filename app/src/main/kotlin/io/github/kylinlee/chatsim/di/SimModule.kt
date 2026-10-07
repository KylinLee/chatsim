package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.repository.SimRepositoryImpl
import io.github.kylinlee.chatsim.repository.SimRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SimModule {
    @Binds
    @Singleton
    abstract fun bindSimRepository(impl: SimRepositoryImpl): SimRepository
}
