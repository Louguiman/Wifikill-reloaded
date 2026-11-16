package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sankare.wifikill.core.database.entity.RouterEntity
import com.sankare.wifikill.core.database.relation.RouterWithCapabilities
import kotlinx.coroutines.flow.Flow

@Dao
interface RouterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(router: RouterEntity)

    @Query("SELECT * FROM routers WHERE id = :routerId LIMIT 1")
    suspend fun getById(routerId: String): RouterEntity?

    @Transaction
    @Query("SELECT * FROM routers WHERE id = :routerId LIMIT 1")
    suspend fun getWithCapabilities(routerId: String): RouterWithCapabilities?

    @Query("SELECT * FROM routers WHERE networkId = :networkId ORDER BY name COLLATE NOCASE ASC")
    fun observeByNetwork(networkId: String): Flow<List<RouterEntity>>

    @Transaction
    @Query("SELECT * FROM routers WHERE networkId = :networkId ORDER BY name COLLATE NOCASE ASC")
    fun observeByNetworkWithCapabilities(networkId: String): Flow<List<RouterWithCapabilities>>

    @Query("SELECT * FROM routers WHERE networkId = :networkId AND ipAddress = :ipAddress LIMIT 1")
    suspend fun getByAddress(networkId: String, ipAddress: String): RouterEntity?

    @Query(
        "UPDATE routers SET connectionStatus = :status, lastConnectedAt = :lastConnectedAt WHERE id = :routerId",
    )
    suspend fun updateStatus(routerId: String, status: String, lastConnectedAt: Long?)

    @Query(
        """
        UPDATE routers
        SET manufacturer = :manufacturer,
            model = :model,
            firmwareVersion = :firmwareVersion,
            connectionStatus = :status,
            lastConnectedAt = :lastConnectedAt
        WHERE id = :routerId
        """,
    )
    suspend fun updateInfo(
        routerId: String,
        manufacturer: String?,
        model: String?,
        firmwareVersion: String?,
        status: String,
        lastConnectedAt: Long?,
    )

    @Query("DELETE FROM routers WHERE id = :routerId")
    suspend fun deleteById(routerId: String)

    @Query("SELECT * FROM routers")
    suspend fun getAll(): List<RouterEntity>

    @Query("DELETE FROM routers")
    suspend fun deleteAll()
}
