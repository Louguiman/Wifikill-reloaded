package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.TrafficSample
import com.sankare.wifikill.core.domain.repository.MonitoringRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveTrafficUseCase @Inject constructor(
    private val repository: MonitoringRepository,
) {
    operator fun invoke(networkId: String): Flow<List<TrafficSample>> =
        repository.observeTraffic(networkId)
}

class RefreshTrafficUseCase @Inject constructor(
    private val repository: MonitoringRepository,
) {
    suspend operator fun invoke(networkId: String): AppResult<Unit> {
        if (networkId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        return repository.refreshTraffic(networkId)
    }
}

class GetLatestTrafficSampleUseCase @Inject constructor(
    private val repository: MonitoringRepository,
) {
    suspend operator fun invoke(deviceId: String): TrafficSample? {
        if (deviceId.isBlank()) return null
        return repository.getLatestSample(deviceId)
    }
}
