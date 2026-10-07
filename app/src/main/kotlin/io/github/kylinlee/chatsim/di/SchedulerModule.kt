package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.repository.ScheduledTaskRepositoryImpl
import io.github.kylinlee.chatsim.repository.ScheduledTaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulerModule {
    @Binds
    @Singleton
    abstract fun bindScheduledTaskRepository(impl: ScheduledTaskRepositoryImpl): ScheduledTaskRepository
}
