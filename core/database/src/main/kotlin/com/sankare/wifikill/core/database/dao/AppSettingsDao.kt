package com.sankare.wifikill.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sankare.wifikill.core.database.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: AppSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(settings: List<AppSettingsEntity>)

    @Query("SELECT * FROM app_settings WHERE settingKey = :key LIMIT 1")
    suspend fun get(key: String): AppSettingsEntity?

    @Query("SELECT * FROM app_settings WHERE settingKey = :key LIMIT 1")
    fun observe(key: String): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingsEntity>>

    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSettingsEntity>

    @Query("DELETE FROM app_settings WHERE settingKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM app_settings")
    suspend fun deleteAll()
}
