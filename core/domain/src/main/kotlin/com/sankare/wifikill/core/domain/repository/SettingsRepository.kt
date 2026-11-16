package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>

    suspend fun getSettings(): AppSettings

    suspend fun updateSettings(settings: AppSettings): AppResult<Unit>
}
