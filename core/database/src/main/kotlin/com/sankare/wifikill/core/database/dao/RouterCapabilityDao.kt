package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sankare.wifikill.core.database.entity.RouterCapabilityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouterCapabilityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(capability: RouterCapabilityEntity)

    @Query("SELECT * FROM router_capabilities WHERE routerId = :routerId")
    fun observeByRouter(routerId: String): Flow<List<RouterCapabilityEntity>>

    @Query(
        "SELECT * FROM router_capabilities WHERE routerId = :routerId AND capabilityCode = :capabilityCode LIMIT 1",
    )
    suspend fun get(routerId: String, capabilityCode: String): RouterCapabilityEntity?
}
