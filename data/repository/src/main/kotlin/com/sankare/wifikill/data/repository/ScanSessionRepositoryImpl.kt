package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.ScanSessionDao
import com.sankare.wifikill.core.database.entity.ScanSessionEntity
import com.sankare.wifikill.core.domain.model.ScanSession
import com.sankare.wifikill.core.domain.model.ScanStatus
import com.sankare.wifikill.core.domain.repository.ScanSessionRepository
import com.sankare.wifikill.data.mapper.toDomain
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScanSessionRepositoryImpl @Inject constructor(
    private val scanSessionDao: ScanSessionDao,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) : ScanSessionRepository {

    override suspend fun createSession(networkId: String, startedAt: Long): String {
        val sessionId = idGenerator.newId()
        scanSessionDao.upsert(
            ScanSessionEntity(
                id = sessionId,
                networkId = networkId,
                startedAt = startedAt,
                status = ScanStatus.RUNNING.name,
                scanMethod = "LOCAL_DISCOVERY",
            ),
        )
        return sessionId
    }

    override suspend fun completeSession(
        sessionId: String,
        devicesFound: Int,
        completedAt: Long,
    ) {
        scanSessionDao.updateResult(
            sessionId = sessionId,
            status = ScanStatus.COMPLETED.name,
            completedAt = completedAt,
            devicesFound = devicesFound,
            errorMessage = null,
        )
    }

    override suspend fun failSession(sessionId: String, error: AppError) {
        scanSessionDao.updateResult(
            sessionId = sessionId,
            status = ScanStatus.FAILED.name,
            completedAt = timeProvider.nowMillis(),
            devicesFound = 0,
            errorMessage = error.message ?: error.code.name,
        )
    }

    override fun observeHistory(networkId: String): Flow<List<ScanSession>> =
        scanSessionDao.observeHistory(networkId).map { sessions ->
            sessions.map { it.toDomain() }
        }
}
