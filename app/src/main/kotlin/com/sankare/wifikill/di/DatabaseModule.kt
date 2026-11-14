package com.sankare.wifikill.di

import android.content.Context
import androidx.room.Room
import com.sankare.wifikill.core.database.WiFiKillDatabase
import com.sankare.wifikill.core.database.dao.AccessPolicyDao
import com.sankare.wifikill.core.database.dao.AppSettingsDao
import com.sankare.wifikill.core.database.dao.DeviceDao
import com.sankare.wifikill.core.database.dao.DeviceObservationDao
import com.sankare.wifikill.core.database.dao.DiagnosticResultDao
import com.sankare.wifikill.core.database.dao.NetworkDao
import com.sankare.wifikill.core.database.dao.RouterCapabilityDao
import com.sankare.wifikill.core.database.dao.RouterDao
import com.sankare.wifikill.core.database.dao.ScanSessionDao
import com.sankare.wifikill.core.database.dao.TrafficSampleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WiFiKillDatabase =
        Room.databaseBuilder(context, WiFiKillDatabase::class.java, WiFiKillDatabase.NAME)
            .addMigrations(*WiFiKillDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideNetworkDao(database: WiFiKillDatabase): NetworkDao = database.networkDao()

    @Provides
    fun provideRouterDao(database: WiFiKillDatabase): RouterDao = database.routerDao()

    @Provides
    fun provideDeviceDao(database: WiFiKillDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun provideScanSessionDao(database: WiFiKillDatabase): ScanSessionDao =
        database.scanSessionDao()

    @Provides
    fun provideDeviceObservationDao(database: WiFiKillDatabase): DeviceObservationDao =
        database.deviceObservationDao()

    @Provides
    fun provideAccessPolicyDao(database: WiFiKillDatabase): AccessPolicyDao =
        database.accessPolicyDao()

    @Provides
    fun provideTrafficSampleDao(database: WiFiKillDatabase): TrafficSampleDao =
        database.trafficSampleDao()

    @Provides
    fun provideDiagnosticResultDao(database: WiFiKillDatabase): DiagnosticResultDao =
        database.diagnosticResultDao()

    @Provides
    fun provideRouterCapabilityDao(database: WiFiKillDatabase): RouterCapabilityDao =
        database.routerCapabilityDao()

    @Provides
    fun provideAppSettingsDao(database: WiFiKillDatabase): AppSettingsDao =
        database.appSettingsDao()
}
