package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import com.sankare.wifikill.core.domain.repository.DeviceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDevicesUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    operator fun invoke(networkId: String): Flow<List<Device>> = repository.observeDevices(networkId)
}

class GetDeviceDetailsUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    operator fun invoke(deviceId: String): Flow<Device?> = repository.observeDevice(deviceId)
}

class RenameDeviceUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    suspend operator fun invoke(deviceId: String, name: String?): AppResult<Unit> {
        if (deviceId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        val normalized = name?.trim()?.takeIf { it.isNotEmpty() }
        return repository.updateCustomName(deviceId, normalized)
    }
}

class SetDeviceTrustUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    suspend operator fun invoke(deviceId: String, isTrusted: Boolean): AppResult<Unit> {
        if (deviceId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        return repository.updateTrustedStatus(deviceId, isTrusted)
    }
}

class GetDeviceHistoryUseCase @Inject constructor(
    private val repository: DeviceRepository,
) {
    operator fun invoke(deviceId: String): Flow<List<DeviceObservation>> =
        repository.observeDeviceHistory(deviceId)
}
