package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.domain.model.ScanSession
import kotlinx.coroutines.flow.Flow

interface ScanSessionRepository {
    suspend fun createSession(networkId: String, startedAt: Long): String

    suspend fun completeSession(sessionId: String, devicesFound: Int, completedAt: Long)

    suspend fun failSession(sessionId: String, error: AppError)

    fun observeHistory(networkId: String): Flow<List<ScanSession>>
}
