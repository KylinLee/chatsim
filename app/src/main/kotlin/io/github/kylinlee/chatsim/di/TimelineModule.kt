package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.repository.TimelineRepositoryImpl
import io.github.kylinlee.chatsim.repository.TimelineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimelineModule {
    @Binds
    @Singleton
    abstract fun bindTimelineRepository(impl: TimelineRepositoryImpl): TimelineRepository
}
