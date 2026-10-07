package io.github.kylinlee.chatsim.di

import io.github.kylinlee.chatsim.domain.rule.RuleEngine
import io.github.kylinlee.chatsim.domain.rule.RuleEngines
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RuleModule {
    @Provides
    @Singleton
    fun provideRuleEngine(): RuleEngine = RuleEngines.engine
}
