package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sankare.wifikill.core.database.entity.ScanSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions WHERE networkId = :networkId ORDER BY startedAt DESC")
    fun observeHistory(networkId: String): Flow<List<ScanSessionEntity>>

    @Query(
        """
        UPDATE scan_sessions
        SET status = :status, completedAt = :completedAt, devicesFound = :devicesFound,
            errorMessage = :errorMessage
        WHERE id = :sessionId
        """,
    )
    suspend fun updateResult(
        sessionId: String,
        status: String,
        completedAt: Long?,
        devicesFound: Int,
        errorMessage: String?,
    )

    @Query("DELETE FROM scan_sessions WHERE startedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
