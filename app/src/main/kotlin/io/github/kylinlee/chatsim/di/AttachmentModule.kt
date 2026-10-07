package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.data.repository.AttachmentRepositoryImpl
import io.github.kylinlee.chatsim.repository.AttachmentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AttachmentModule {
    @Binds
    @Singleton
    abstract fun bindAttachmentRepository(impl: AttachmentRepositoryImpl): AttachmentRepository
}
