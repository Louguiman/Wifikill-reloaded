package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AccessPolicy
import com.sankare.wifikill.core.domain.model.AccessPolicyRequest
import com.sankare.wifikill.core.domain.model.PolicyVerificationResult
import kotlinx.coroutines.flow.Flow

interface AccessManagementRepository {
    fun observePolicies(routerId: String): Flow<List<AccessPolicy>>

    suspend fun applyPolicy(request: AccessPolicyRequest): AppResult<AccessPolicy>

    suspend fun removePolicy(policyId: String): AppResult<Unit>

    suspend fun verifyPolicy(policyId: String): AppResult<PolicyVerificationResult>
}
