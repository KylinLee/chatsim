package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.local.RoleStore
import io.github.kylinlee.chatsim.data.local.SharedPrefsRoleStore
import io.github.kylinlee.chatsim.data.repository.RoleRepositoryImpl
import io.github.kylinlee.chatsim.repository.RoleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RoleModule {
    @Binds
    @Singleton
    abstract fun bindRoleStore(impl: SharedPrefsRoleStore): RoleStore

    @Binds
    @Singleton
    abstract fun bindRoleRepository(impl: RoleRepositoryImpl): RoleRepository
}
