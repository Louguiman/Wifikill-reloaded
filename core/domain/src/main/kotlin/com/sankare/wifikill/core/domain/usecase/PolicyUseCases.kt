package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AccessPolicy
import com.sankare.wifikill.core.domain.model.AccessPolicyRequest
import com.sankare.wifikill.core.domain.model.PolicyVerificationResult
import com.sankare.wifikill.core.domain.repository.AccessManagementRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveAccessPoliciesUseCase @Inject constructor(
    private val repository: AccessManagementRepository,
) {
    operator fun invoke(routerId: String): Flow<List<AccessPolicy>> =
        repository.observePolicies(routerId)
}

class ApplyAccessPolicyUseCase @Inject constructor(
    private val repository: AccessManagementRepository,
) {
    suspend operator fun invoke(request: AccessPolicyRequest): AppResult<AccessPolicy> {
        if (request.routerId.isBlank() || request.deviceId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        if (request.reason != null && request.reason.length > MAX_REASON_LENGTH) {
            return AppResult.Failure(
                AppError(AppErrorCode.UNKNOWN, "Reason is too long"),
            )
        }
        val normalized = request.copy(reason = request.reason?.trim()?.takeIf { it.isNotEmpty() })
        return repository.applyPolicy(normalized)
    }

    private companion object {
        const val MAX_REASON_LENGTH = 256
    }
}

class RemoveAccessPolicyUseCase @Inject constructor(
    private val repository: AccessManagementRepository,
) {
    suspend operator fun invoke(policyId: String): AppResult<Unit> {
        if (policyId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        return repository.removePolicy(policyId)
    }
}

class VerifyAccessPolicyUseCase @Inject constructor(
    private val repository: AccessManagementRepository,
) {
    suspend operator fun invoke(policyId: String): AppResult<PolicyVerificationResult> {
        if (policyId.isBlank()) {
            return AppResult.Failure(AppError(AppErrorCode.INVALID_NETWORK_CONTEXT))
        }
        return repository.verifyPolicy(policyId)
    }
}
