package com.sankare.wifikill.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.sankare.wifikill.core.database.entity.AccessPolicyEntity
import com.sankare.wifikill.core.database.entity.AppSettingsEntity
import com.sankare.wifikill.core.database.entity.DeviceEntity
import com.sankare.wifikill.core.database.entity.DeviceObservationEntity
import com.sankare.wifikill.core.database.entity.DiagnosticResultEntity
import com.sankare.wifikill.core.database.entity.NetworkEntity
import com.sankare.wifikill.core.database.entity.RouterCapabilityEntity
import com.sankare.wifikill.core.database.entity.RouterEntity
import com.sankare.wifikill.core.database.entity.ScanSessionEntity
import com.sankare.wifikill.core.database.entity.TrafficSampleEntity

@Database(
    entities = [
        NetworkEntity::class,
        RouterEntity::class,
        DeviceEntity::class,
        ScanSessionEntity::class,
        DeviceObservationEntity::class,
        AccessPolicyEntity::class,
        TrafficSampleEntity::class,
        DiagnosticResultEntity::class,
        RouterCapabilityEntity::class,
        AppSettingsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class WiFiKillDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun routerDao(): RouterDao
    abstract fun deviceDao(): DeviceDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun deviceObservationDao(): DeviceObservationDao
    abstract fun accessPolicyDao(): AccessPolicyDao
    abstract fun trafficSampleDao(): TrafficSampleDao
    abstract fun diagnosticResultDao(): DiagnosticResultDao
    abstract fun routerCapabilityDao(): RouterCapabilityDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        const val NAME = "wifikill.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE routers ADD COLUMN port INTEGER NOT NULL DEFAULT 443",
                )
                database.execSQL(
                    "ALTER TABLE routers ADD COLUMN protocol TEXT NOT NULL DEFAULT 'HTTPS'",
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
