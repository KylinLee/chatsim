package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.events.AppEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EventModule {
    @Provides
    @Singleton
    fun provideAppEventBus(): AppEventBus = AppEventBus
}
