package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.AccessPolicyDao
import com.sankare.wifikill.core.database.dao.DeviceDao
import com.sankare.wifikill.core.database.dao.DeviceObservationDao
import com.sankare.wifikill.core.database.dao.DiagnosticResultDao
import com.sankare.wifikill.core.database.dao.NetworkDao
import com.sankare.wifikill.core.database.dao.RouterCapabilityDao
import com.sankare.wifikill.core.database.dao.RouterDao
import com.sankare.wifikill.core.database.dao.ScanSessionDao
import com.sankare.wifikill.core.database.dao.TrafficSampleDao
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.repository.CredentialProvider
import com.sankare.wifikill.core.domain.repository.LocalDataRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

class LocalDataRepositoryImpl @Inject constructor(
    private val accessPolicyDao: AccessPolicyDao,
    private val trafficSampleDao: TrafficSampleDao,
    private val observationDao: DeviceObservationDao,
    private val diagnosticResultDao: DiagnosticResultDao,
    private val scanSessionDao: ScanSessionDao,
    private val routerCapabilityDao: RouterCapabilityDao,
    private val deviceDao: DeviceDao,
    private val routerDao: RouterDao,
    private val networkDao: NetworkDao,
    private val credentialProvider: CredentialProvider,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatchersProvider,
) : LocalDataRepository {

    override suspend fun deleteAllLocalData(): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val credentialIds = routerDao.getAll().mapNotNull { it.credentialId }
                val credentialFailures = credentialIds.count { credentialId ->
                    credentialProvider.delete(credentialId) is AppResult.Failure
                }

                accessPolicyDao.deleteAll()
                trafficSampleDao.deleteAll()
                observationDao.deleteAll()
                diagnosticResultDao.deleteAll()
                scanSessionDao.deleteAll()
                routerCapabilityDao.deleteAll()
                deviceDao.deleteAll()
                routerDao.deleteAll()
                networkDao.deleteAll()

                credentialFailures
            }.fold(
                onSuccess = { failures ->
                    if (failures == 0) {
                        AppResult.Success(Unit)
                    } else {
                        AppResult.Failure(
                            AppError(
                                AppErrorCode.STORAGE_ERROR,
                                "Local history was deleted but $failures credential(s) " +
                                    "could not be removed",
                            ),
                        )
                    }
                },
                onFailure = {
                    AppResult.Failure(
                        AppError(AppErrorCode.STORAGE_ERROR, "Unable to delete local data"),
                    )
                },
            )
        }

    override suspend fun deleteExpiredData(settings: AppSettings): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val now = timeProvider.nowMillis()
                scanSessionDao.deleteOlderThan(
                    cutoff(now, settings.scanHistoryRetentionDays),
                )
                observationDao.deleteOlderThan(
                    cutoff(now, settings.deviceObservationRetentionDays),
                )
                trafficSampleDao.deleteOlderThan(
                    cutoff(now, settings.trafficRetentionDays),
                )
                diagnosticResultDao.deleteOlderThan(
                    cutoff(now, settings.diagnosticsRetentionDays),
                )
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = {
                    AppResult.Failure(
                        AppError(AppErrorCode.STORAGE_ERROR, "Unable to clean up local data"),
                    )
                },
            )
        }

    private fun cutoff(now: Long, days: Int): Long =
        now - days.toLong() * MILLIS_PER_DAY

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1_000
    }
}
