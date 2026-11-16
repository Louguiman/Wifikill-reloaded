package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sankare.wifikill.core.database.entity.DiagnosticResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: DiagnosticResultEntity)

    @Query("SELECT * FROM diagnostic_results WHERE networkId = :networkId ORDER BY executedAt DESC")
    fun observeByNetwork(networkId: String): Flow<List<DiagnosticResultEntity>>

    @Query("DELETE FROM diagnostic_results WHERE executedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM diagnostic_results")
    suspend fun deleteAll()
}
