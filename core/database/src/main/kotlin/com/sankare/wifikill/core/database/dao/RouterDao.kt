package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sankare.wifikill.core.database.entity.RouterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(router: RouterEntity)

    @Query("SELECT * FROM routers WHERE id = :routerId LIMIT 1")
    suspend fun getById(routerId: String): RouterEntity?

    @Query("SELECT * FROM routers WHERE networkId = :networkId ORDER BY name COLLATE NOCASE ASC")
    fun observeByNetwork(networkId: String): Flow<List<RouterEntity>>

    @Query("SELECT * FROM routers WHERE networkId = :networkId AND ipAddress = :ipAddress LIMIT 1")
    suspend fun getByAddress(networkId: String, ipAddress: String): RouterEntity?
}
