package com.sankare.wifikill.di

import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.domain.service.RootManager
import com.sankare.wifikill.integrations.root.RootManagerImpl
import com.sankare.wifikill.integrations.root.SuRootCommandRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RootModule {

    @Provides
    @Singleton
    fun provideRootManager(
        dispatchers: DispatchersProvider,
        timeProvider: TimeProvider,
    ): RootManager = RootManagerImpl(
        commandRunner = SuRootCommandRunner(dispatchers),
        timeProvider = timeProvider,
    )
}
