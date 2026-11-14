package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AccessPolicy
import com.sankare.wifikill.core.domain.model.AccessPolicyRequest
import com.sankare.wifikill.core.domain.repository.AccessManagementRepository
import javax.inject.Inject

class ApplyAccessPolicyUseCase @Inject constructor(
    private val repository: AccessManagementRepository,
) {
    suspend operator fun invoke(request: AccessPolicyRequest): AppResult<AccessPolicy> {
        if (request.routerId.isBlank() || request.deviceId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        return repository.applyPolicy(request)
    }
}

class RemoveAccessPolicyUseCase @Inject constructor(
    private val repository: AccessManagementRepository,
) {
    suspend operator fun invoke(policyId: String): AppResult<Unit> {
        if (policyId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.UNKNOWN))
        }
        return repository.removePolicy(policyId)
    }
}
