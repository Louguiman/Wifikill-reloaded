package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sankare.wifikill.core.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Query("SELECT * FROM devices WHERE id = :deviceId LIMIT 1")
    fun observeById(deviceId: String): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE id = :deviceId LIMIT 1")
    suspend fun getById(deviceId: String): DeviceEntity?

    @Query(
        """
        SELECT DISTINCT d.* FROM devices d
        INNER JOIN device_observations o ON o.deviceId = d.id
        WHERE o.networkId = :networkId
        ORDER BY COALESCE(d.customName, d.hostname, d.id) COLLATE NOCASE ASC
        """,
    )
    fun observeByNetwork(networkId: String): Flow<List<DeviceEntity>>

    @Query("SELECT DISTINCT d.* FROM devices d WHERE d.hostname = :hostname COLLATE NOCASE")
    suspend fun findByHostname(hostname: String): List<DeviceEntity>

    @Query(
        """
        SELECT o.deviceId FROM device_observations o
        WHERE o.macAddress = :macAddress
        ORDER BY o.observedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findDeviceIdByMac(macAddress: String): String?

    @Query("UPDATE devices SET customName = :name, updatedAt = :updatedAt WHERE id = :deviceId")
    suspend fun updateCustomName(deviceId: String, name: String?, updatedAt: Long)

    @Query("UPDATE devices SET isTrusted = :isTrusted, updatedAt = :updatedAt WHERE id = :deviceId")
    suspend fun updateTrusted(deviceId: String, isTrusted: Boolean, updatedAt: Long)

    @Query("UPDATE devices SET notes = :notes, updatedAt = :updatedAt WHERE id = :deviceId")
    suspend fun updateNotes(deviceId: String, notes: String?, updatedAt: Long)
}
