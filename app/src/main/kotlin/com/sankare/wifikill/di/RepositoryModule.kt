package com.sankare.wifikill.di

import com.sankare.wifikill.core.domain.repository.AccessManagementRepository
import com.sankare.wifikill.core.domain.repository.DeviceRepository
import com.sankare.wifikill.core.domain.repository.DiagnosticsRepository
import com.sankare.wifikill.core.domain.repository.LocalDataRepository
import com.sankare.wifikill.core.domain.repository.MonitoringRepository
import com.sankare.wifikill.core.domain.repository.NetworkDiscoveryRepository
import com.sankare.wifikill.core.domain.repository.RouterRepository
import com.sankare.wifikill.core.domain.repository.ScanSessionRepository
import com.sankare.wifikill.core.domain.repository.SettingsRepository
import com.sankare.wifikill.core.domain.service.AppEventBus
import com.sankare.wifikill.core.domain.service.DeviceIdentityResolver
import com.sankare.wifikill.data.event.DefaultAppEventBus
import com.sankare.wifikill.data.identity.DefaultDeviceIdentityResolver
import com.sankare.wifikill.data.repository.AccessManagementRepositoryImpl
import com.sankare.wifikill.data.repository.DeviceRepositoryImpl
import com.sankare.wifikill.data.repository.DiagnosticsRepositoryImpl
import com.sankare.wifikill.data.repository.LocalDataRepositoryImpl
import com.sankare.wifikill.data.repository.MonitoringRepositoryImpl
import com.sankare.wifikill.data.repository.NetworkDiscoveryRepositoryImpl
import com.sankare.wifikill.data.repository.RouterRepositoryImpl
import com.sankare.wifikill.data.repository.ScanSessionRepositoryImpl
import com.sankare.wifikill.data.repository.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNetworkDiscoveryRepository(
        implementation: NetworkDiscoveryRepositoryImpl,
    ): NetworkDiscoveryRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(implementation: DeviceRepositoryImpl): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindScanSessionRepository(
        implementation: ScanSessionRepositoryImpl,
    ): ScanSessionRepository

    @Binds
    @Singleton
    abstract fun bindRouterRepository(
        implementation: RouterRepositoryImpl,
    ): RouterRepository

    @Binds
    @Singleton
    abstract fun bindAccessManagementRepository(
        implementation: AccessManagementRepositoryImpl,
    ): AccessManagementRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosticsRepository(
        implementation: DiagnosticsRepositoryImpl,
    ): DiagnosticsRepository

    @Binds
    @Singleton
    abstract fun bindMonitoringRepository(
        implementation: MonitoringRepositoryImpl,
    ): MonitoringRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: SettingsRepositoryImpl,
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindLocalDataRepository(
        implementation: LocalDataRepositoryImpl,
    ): LocalDataRepository

    @Binds
    @Singleton
    abstract fun bindDeviceIdentityResolver(
        implementation: DefaultDeviceIdentityResolver,
    ): DeviceIdentityResolver

    @Binds
    @Singleton
    abstract fun bindAppEventBus(implementation: DefaultAppEventBus): AppEventBus
}
