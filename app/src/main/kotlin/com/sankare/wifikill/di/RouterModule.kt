package com.sankare.wifikill.di

import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.repository.CredentialProvider
import com.sankare.wifikill.integrations.generic.GenericRouterAdapter
import com.sankare.wifikill.integrations.mikrotik.MikroTikAdapter
import com.sankare.wifikill.integrations.openwrt.OpenWrtAdapter
import com.sankare.wifikill.integrations.router.DefaultRouterAdapterRegistry
import com.sankare.wifikill.integrations.router.RouterAdapterRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RouterModule {

    @Provides
    @Singleton
    fun provideRouterAdapterRegistry(
        generic: GenericRouterAdapter,
        credentialProvider: CredentialProvider,
        dispatchers: DispatchersProvider,
        timeProvider: TimeProvider,
    ): RouterAdapterRegistry = DefaultRouterAdapterRegistry(
        mapOf(
            RouterAdapterType.GENERIC to generic,
            RouterAdapterType.OPENWRT to OpenWrtAdapter(
                credentialProvider = credentialProvider,
                dispatchers = dispatchers,
                timeProvider = timeProvider,
            ),
            RouterAdapterType.MIKROTIK to MikroTikAdapter(
                credentialProvider = credentialProvider,
                dispatchers = dispatchers,
                timeProvider = timeProvider,
            ),
        ),
    )
}
