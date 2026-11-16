package com.sankare.wifikill.di

import android.content.Context
import com.sankare.wifikill.core.common.ApplicationScope
import com.sankare.wifikill.core.network.AndroidNetworkContextProvider
import com.sankare.wifikill.core.network.AndroidPermissionChecker
import com.sankare.wifikill.core.network.NetworkContextProvider
import com.sankare.wifikill.core.network.PermissionChecker
import com.sankare.wifikill.core.network.discovery.DefaultDiscoveryEngine
import com.sankare.wifikill.core.network.discovery.DefaultSubnetScanner
import com.sankare.wifikill.core.network.discovery.DiscoveryEngine
import com.sankare.wifikill.core.network.discovery.DiscoverySource
import com.sankare.wifikill.core.network.discovery.LocalReachabilitySource
import com.sankare.wifikill.core.network.discovery.SubnetScanner
import com.sankare.wifikill.core.network.diagnostics.AndroidDiagnosticProbe
import com.sankare.wifikill.core.network.diagnostics.DiagnosticProbe
import com.sankare.wifikill.core.network.traffic.AndroidLocalTrafficReader
import com.sankare.wifikill.core.network.traffic.LocalTrafficReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providePermissionChecker(@ApplicationContext context: Context): PermissionChecker =
        AndroidPermissionChecker(context)

    @Provides
    @Singleton
    fun provideNetworkContextProvider(
        @ApplicationContext context: Context,
        permissionChecker: PermissionChecker,
    ): NetworkContextProvider = AndroidNetworkContextProvider(context, permissionChecker)

    @Provides
    @Singleton
    fun provideSubnetScanner(): SubnetScanner = DefaultSubnetScanner()

    @Provides
    @Singleton
    fun provideDiagnosticProbe(): DiagnosticProbe = AndroidDiagnosticProbe()

    @Provides
    @Singleton
    fun provideLocalTrafficReader(): LocalTrafficReader = AndroidLocalTrafficReader()

    @Provides
    @IntoSet
    fun provideLocalReachabilitySource(subnetScanner: SubnetScanner): DiscoverySource =
        LocalReachabilitySource(subnetScanner)

    @Provides
    @Singleton
    fun provideDiscoveryEngine(
        sources: Set<@JvmSuppressWildcards DiscoverySource>,
    ): DiscoveryEngine = DefaultDiscoveryEngine(sources)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
