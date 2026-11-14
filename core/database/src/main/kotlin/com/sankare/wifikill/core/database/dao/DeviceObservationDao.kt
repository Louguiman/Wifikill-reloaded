package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sankare.wifikill.core.database.entity.DeviceObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceObservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: DeviceObservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(observations: List<DeviceObservationEntity>)

    @Query(
        "SELECT * FROM device_observations WHERE deviceId = :deviceId ORDER BY observedAt DESC",
    )
    fun observeHistory(deviceId: String): Flow<List<DeviceObservationEntity>>

    @Query(
        "SELECT * FROM device_observations WHERE networkId = :networkId ORDER BY observedAt DESC",
    )
    fun observeByNetwork(networkId: String): Flow<List<DeviceObservationEntity>>

    @Query("SELECT * FROM device_observations WHERE deviceId = :deviceId ORDER BY observedAt DESC LIMIT 1")
    suspend fun getLatestForDevice(deviceId: String): DeviceObservationEntity?

    @Query("SELECT * FROM device_observations WHERE macAddress = :macAddress ORDER BY observedAt DESC LIMIT 1")
    suspend fun getLatestByMac(macAddress: String): DeviceObservationEntity?

    @Query("DELETE FROM device_observations WHERE observedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
