package com.sankare.wifikill.core.domain.model

data class AccessPolicy(
    val id: String,
    val routerId: String,
    val deviceId: String,
    val action: AccessPolicyAction,
    val status: AccessPolicyStatus,
    val routerPolicyReference: String?,
    val reason: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long?,
)

enum class AccessPolicyAction {
    BLOCK_INTERNET,
    ALLOW_INTERNET,
}

enum class AccessPolicyStatus {
    PENDING,
    APPLYING,
    ACTIVE,
    REMOVING,
    REMOVED,
    FAILED,
    UNKNOWN,
}

data class AccessPolicyRequest(
    val routerId: String,
    val deviceId: String,
    val action: AccessPolicyAction,
    val reason: String?,
    val expiresAt: Long?,
    val targetMacAddress: String? = null,
    val targetIpAddress: String? = null,
)

data class PolicyVerificationResult(
    val isVerified: Boolean,
    val actualAction: AccessPolicyAction?,
    val checkedAt: Long,
    val details: String?,
)
