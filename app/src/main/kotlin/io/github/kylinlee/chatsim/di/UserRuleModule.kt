package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.repository.UserRuleRepositoryImpl
import io.github.kylinlee.chatsim.repository.UserRuleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UserRuleModule {
    @Binds
    @Singleton
    abstract fun bindUserRuleRepository(impl: UserRuleRepositoryImpl): UserRuleRepository
}
