package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AppSettings

interface LocalDataRepository {
    suspend fun deleteAllLocalData(): AppResult<Unit>

    suspend fun deleteExpiredData(settings: AppSettings): AppResult<Unit>
}
