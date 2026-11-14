package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sankare.wifikill.core.database.entity.TrafficSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<TrafficSampleEntity>)

    @Query("SELECT * FROM traffic_samples WHERE networkId = :networkId ORDER BY sampledAt DESC")
    fun observeByNetwork(networkId: String): Flow<List<TrafficSampleEntity>>

    @Query("SELECT * FROM traffic_samples WHERE deviceId = :deviceId ORDER BY sampledAt DESC LIMIT 1")
    suspend fun getLatestForDevice(deviceId: String): TrafficSampleEntity?

    @Query("DELETE FROM traffic_samples WHERE sampledAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
