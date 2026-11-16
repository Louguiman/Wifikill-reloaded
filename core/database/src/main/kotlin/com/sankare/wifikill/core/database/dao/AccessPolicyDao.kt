package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sankare.wifikill.core.database.entity.AccessPolicyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessPolicyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(policy: AccessPolicyEntity)

    @Query("SELECT * FROM access_policies WHERE id = :policyId LIMIT 1")
    suspend fun getById(policyId: String): AccessPolicyEntity?

    @Query("SELECT * FROM access_policies WHERE routerId = :routerId ORDER BY createdAt DESC")
    fun observeByRouter(routerId: String): Flow<List<AccessPolicyEntity>>

    @Query("UPDATE access_policies SET status = :status, updatedAt = :updatedAt WHERE id = :policyId")
    suspend fun updateStatus(policyId: String, status: String, updatedAt: Long)

    @Query(
        "UPDATE access_policies SET status = :status, routerPolicyReference = :reference, " +
            "updatedAt = :updatedAt WHERE id = :policyId",
    )
    suspend fun updateStatusAndReference(
        policyId: String,
        status: String,
        reference: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM access_policies WHERE id = :policyId")
    suspend fun delete(policyId: String)

    @Query("DELETE FROM access_policies")
    suspend fun deleteAll()
}
