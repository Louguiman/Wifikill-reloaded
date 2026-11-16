package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.RootCapability
import com.sankare.wifikill.core.domain.model.RootOperation
import com.sankare.wifikill.core.domain.model.RootOperationResult
import com.sankare.wifikill.core.domain.service.RootManager
import javax.inject.Inject

class GetRootAvailabilityUseCase @Inject constructor(
    private val rootManager: RootManager,
) {
    suspend operator fun invoke(): Boolean = rootManager.isAvailable()
}

class GetRootCapabilitiesUseCase @Inject constructor(
    private val rootManager: RootManager,
) {
    suspend operator fun invoke(): Set<RootCapability> =
        if (rootManager.isAvailable()) {
            rootManager.getCapabilities()
        } else {
            emptySet()
        }
}

class ExecuteRootOperationUseCase @Inject constructor(
    private val rootManager: RootManager,
) {
    suspend operator fun invoke(operation: RootOperation): AppResult<RootOperationResult> {
        if (!rootManager.isAvailable()) {
            return AppResult.Failure(
                AppError(
                    AppErrorCode.ROOT_UNAVAILABLE,
                    "Root access is not available on this device",
                ),
            )
        }
        return rootManager.execute(operation)
    }
}
