package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.NetworkDao
import com.sankare.wifikill.core.database.dao.TrafficSampleDao
import com.sankare.wifikill.core.database.entity.TrafficSampleEntity
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.TrafficSample
import com.sankare.wifikill.core.domain.model.TrafficSource
import com.sankare.wifikill.core.domain.repository.MonitoringRepository
import com.sankare.wifikill.core.domain.repository.SettingsRepository
import com.sankare.wifikill.core.network.NetworkContextProvider
import com.sankare.wifikill.core.network.traffic.LocalTrafficReader
import com.sankare.wifikill.data.mapper.toDomain
import com.sankare.wifikill.data.mapper.toEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MonitoringRepositoryImpl @Inject constructor(
    private val trafficSampleDao: TrafficSampleDao,
    private val networkDao: NetworkDao,
    private val networkContextProvider: NetworkContextProvider,
    private val trafficReader: LocalTrafficReader,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatchersProvider,
) : MonitoringRepository {

    override fun observeTraffic(networkId: String): Flow<List<TrafficSample>> =
        trafficSampleDao.observeByNetwork(networkId)
            .map { samples -> samples.map { it.toDomain() } }

    override suspend fun refreshTraffic(networkId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            if (networkId.isBlank()) {
                return@withContext AppResult.Failure(
                    AppError(AppErrorCode.INVALID_NETWORK_CONTEXT),
                )
            }
            val context = networkContextProvider.observeCurrentNetwork().first()
                ?: return@withContext AppResult.Failure(
                    AppError(AppErrorCode.NETWORK_UNAVAILABLE, "No active network"),
                )
            if (context.networkId == null || context.networkId != networkId) {
                return@withContext AppResult.Failure(
                    AppError(
                        AppErrorCode.INVALID_NETWORK_CONTEXT,
                        "Network context changed",
                    ),
                )
            }

            val counters = trafficReader.read()
            if (counters.bytesReceived == null && counters.bytesSent == null) {
                return@withContext AppResult.Failure(
                    AppError(
                        AppErrorCode.UNSUPPORTED_CAPABILITY,
                        "Traffic statistics are unavailable on this device",
                    ),
                )
            }

            persistNetwork(context)
            val sampledAt = timeProvider.nowMillis()
            trafficSampleDao.insertAll(
                listOf(
                    TrafficSampleEntity(
                        id = idGenerator.newId(),
                        deviceId = null,
                        networkId = networkId,
                        source = TrafficSource.ANDROID_LOCAL.name,
                        bytesReceived = counters.bytesReceived,
                        bytesSent = counters.bytesSent,
                        sampledAt = sampledAt,
                    ),
                ),
            )
            val retentionDays = settingsRepository.getSettings().trafficRetentionDays
            trafficSampleDao.deleteOlderThan(
                sampledAt - retentionDays.toLong() * MILLIS_PER_DAY,
            )

            AppResult.Success(Unit)
        }

    override suspend fun getLatestSample(deviceId: String): TrafficSample? =
        trafficSampleDao.getLatestForDevice(deviceId)?.toDomain()

    private suspend fun persistNetwork(context: NetworkContext) {
        val networkId = requireNotNull(context.networkId)
        networkDao.markCurrent(
            context.toEntity(
                existing = networkDao.getById(networkId),
                now = timeProvider.nowMillis(),
            ),
        )
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1_000
    }
}
