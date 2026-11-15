package com.sankare.wifikill.integrations.router

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AccessPolicyRequest
import com.sankare.wifikill.core.domain.model.PolicyOperationReceipt
import com.sankare.wifikill.core.domain.model.PolicyVerificationRequest
import com.sankare.wifikill.core.domain.model.PolicyVerificationResult
import com.sankare.wifikill.core.domain.model.RouterAccessPolicy
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterConnectionConfig
import com.sankare.wifikill.core.domain.model.RouterDevice
import com.sankare.wifikill.core.domain.model.RouterInfo

interface RouterAdapter {

    val adapterType: RouterAdapterType

    suspend fun connect(config: RouterConnectionConfig): AppResult<RouterInfo>

    suspend fun getRouterInfo(): AppResult<RouterInfo>

    suspend fun discoverDevices(): AppResult<List<RouterDevice>>

    suspend fun getCapabilities(): Set<RouterCapability>

    suspend fun getAccessPolicies(): AppResult<List<RouterAccessPolicy>>

    suspend fun applyAccessPolicy(request: AccessPolicyRequest): AppResult<PolicyOperationReceipt>

    suspend fun removeAccessPolicy(policyId: String): AppResult<PolicyOperationReceipt>

    suspend fun verifyAccessPolicy(
        request: PolicyVerificationRequest,
    ): AppResult<PolicyVerificationResult>

    suspend fun disconnect()
}
