package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.ScanEvent
import com.sankare.wifikill.core.domain.model.ScanRequest
import com.sankare.wifikill.core.domain.model.ScanSession
import com.sankare.wifikill.core.domain.repository.NetworkDiscoveryRepository
import com.sankare.wifikill.core.domain.repository.ScanSessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCurrentNetworkUseCase @Inject constructor(
    private val repository: NetworkDiscoveryRepository,
) {
    operator fun invoke(): Flow<NetworkContext?> = repository.observeCurrentNetwork()
}

class StartNetworkScanUseCase @Inject constructor(
    private val repository: NetworkDiscoveryRepository,
) {
    suspend operator fun invoke(request: ScanRequest): AppResult<String> {
        if (request.networkId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        return repository.startScan(request)
    }
}

class CancelNetworkScanUseCase @Inject constructor(
    private val repository: NetworkDiscoveryRepository,
) {
    suspend operator fun invoke(sessionId: String): AppResult<Unit> {
        if (sessionId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        return repository.cancelScan(sessionId)
    }
}

class ObserveScanEventsUseCase @Inject constructor(
    private val repository: NetworkDiscoveryRepository,
) {
    operator fun invoke(sessionId: String): Flow<ScanEvent> =
        repository.observeScanEvents(sessionId)
}

class ObserveScanHistoryUseCase @Inject constructor(
    private val repository: ScanSessionRepository,
) {
    operator fun invoke(networkId: String): Flow<List<ScanSession>> =
        repository.observeHistory(networkId)
}
