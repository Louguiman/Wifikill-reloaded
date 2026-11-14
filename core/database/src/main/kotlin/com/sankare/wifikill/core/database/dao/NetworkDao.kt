package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sankare.wifikill.core.database.entity.NetworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(network: NetworkEntity)

    @Query("SELECT * FROM networks WHERE id = :networkId LIMIT 1")
    suspend fun getById(networkId: String): NetworkEntity?

    @Query("SELECT * FROM networks WHERE isCurrent = 1 LIMIT 1")
    fun observeCurrent(): Flow<NetworkEntity?>

    @Query("SELECT * FROM networks WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrent(): NetworkEntity?

    @Query("SELECT * FROM networks WHERE ssid = :ssid ORDER BY lastSeenAt DESC LIMIT 1")
    suspend fun getBySsid(ssid: String): NetworkEntity?

    @Query("UPDATE networks SET lastSeenAt = :lastSeenAt WHERE id = :networkId")
    suspend fun updateLastSeen(networkId: String, lastSeenAt: Long)

    @Query("UPDATE networks SET isCurrent = 0 WHERE isCurrent = 1")
    suspend fun clearCurrent()

    @Transaction
    suspend fun markCurrent(network: NetworkEntity) {
        upsert(network)
        clearCurrent()
        setCurrent(network.id)
    }

    @Query("UPDATE networks SET isCurrent = 1 WHERE id = :networkId")
    suspend fun setCurrent(networkId: String)
}
