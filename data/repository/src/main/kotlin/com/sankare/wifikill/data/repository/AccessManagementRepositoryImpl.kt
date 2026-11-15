package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.AccessPolicyDao
import com.sankare.wifikill.core.database.dao.DeviceDao
import com.sankare.wifikill.core.database.dao.DeviceObservationDao
import com.sankare.wifikill.core.database.dao.RouterDao
import com.sankare.wifikill.core.database.entity.AccessPolicyEntity
import com.sankare.wifikill.core.database.entity.RouterEntity
import com.sankare.wifikill.core.domain.model.AccessPolicy
import com.sankare.wifikill.core.domain.model.AccessPolicyAction
import com.sankare.wifikill.core.domain.model.AccessPolicyRequest
import com.sankare.wifikill.core.domain.model.AccessPolicyStatus
import com.sankare.wifikill.core.domain.model.PolicyVerificationRequest
import com.sankare.wifikill.core.domain.model.PolicyVerificationResult
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterConnectionConfig
import com.sankare.wifikill.core.domain.model.RouterProtocol
import com.sankare.wifikill.core.domain.repository.AccessManagementRepository
import com.sankare.wifikill.core.domain.service.AppEvent
import com.sankare.wifikill.core.domain.service.AppEventBus
import com.sankare.wifikill.integrations.router.RouterAdapter
import com.sankare.wifikill.integrations.router.RouterAdapterRegistry
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AccessManagementRepositoryImpl @Inject constructor(
    private val policyDao: AccessPolicyDao,
    private val routerDao: RouterDao,
    private val deviceDao: DeviceDao,
    private val observationDao: DeviceObservationDao,
    private val adapterRegistry: RouterAdapterRegistry,
    private val eventBus: AppEventBus,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatchersProvider,
) : AccessManagementRepository {

    override fun observePolicies(routerId: String): Flow<List<AccessPolicy>> =
        policyDao.observeByRouter(routerId).map { policies -> policies.map { it.toDomain() } }

    override suspend fun applyPolicy(request: AccessPolicyRequest): AppResult<AccessPolicy> =
        withContext(dispatchers.io) {
            val resolved = when (val result = resolveRouter(request.routerId)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            deviceDao.getById(request.deviceId)
                ?: return@withContext AppResult.Failure(
                    AppError(AppErrorCode.INVALID_NETWORK_CONTEXT, "Unknown device"),
                )
            val observation = observationDao.getLatestForDevice(request.deviceId)
            val targetRequest = request.copy(
                targetMacAddress = request.targetMacAddress ?: observation?.macAddress,
                targetIpAddress = request.targetIpAddress ?: observation?.ipAddress,
            )
            if (targetRequest.targetMacAddress == null && targetRequest.targetIpAddress == null) {
                return@withContext AppResult.Failure(
                    AppError(
                        AppErrorCode.UNSUPPORTED_CAPABILITY,
                        "The device has no known IP or MAC address",
                    ),
                )
            }

            val now = timeProvider.nowMillis()
            val policyId = idGenerator.newId()
            policyDao.upsert(
                AccessPolicyEntity(
                    id = policyId,
                    routerId = request.routerId,
                    deviceId = request.deviceId,
                    action = request.action.name,
                    status = AccessPolicyStatus.PENDING.name,
                    reason = request.reason,
                    createdAt = now,
                    updatedAt = now,
                    expiresAt = request.expiresAt,
                ),
            )
            emit(policyId, AccessPolicyStatus.PENDING)

            val connected = connect(resolved)
            if (connected is AppResult.Failure) {
                return@withContext failPolicy(policyId, connected.error)
            }
            if (RouterCapability.ACCESS_POLICY_WRITE !in resolved.adapter.getCapabilities()) {
                return@withContext failPolicy(
                    policyId,
                    AppError(
                        AppErrorCode.UNSUPPORTED_CAPABILITY,
                        "Router does not support access policies",
                    ),
                )
            }

            val existingReference = findExistingReference(resolved.adapter, targetRequest)
            if (existingReference.applies) {
                updatePolicy(policyId, AccessPolicyStatus.ACTIVE, existingReference.reference)
                return@withContext successPolicy(policyId)
            }

            updatePolicy(policyId, AccessPolicyStatus.APPLYING, null)
            emit(policyId, AccessPolicyStatus.APPLYING)

            val receipt = when (
                val result = guarded(APPLY_TIMEOUT_MS) {
                    resolved.adapter.applyAccessPolicy(targetRequest)
                }
            ) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext failPolicy(policyId, result.error)
            }
            updatePolicy(policyId, AccessPolicyStatus.APPLYING, receipt.routerPolicyReference)

            val verification = when (
                val result = guarded(VERIFY_TIMEOUT_MS) {
                    resolved.adapter.verifyAccessPolicy(
                        PolicyVerificationRequest(
                            operationId = receipt.operationId,
                            routerPolicyReference = receipt.routerPolicyReference,
                            expectedAction = targetRequest.action,
                            deviceId = targetRequest.deviceId,
                            targetMacAddress = targetRequest.targetMacAddress,
                            targetIpAddress = targetRequest.targetIpAddress,
                        ),
                    )
                }
            ) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    return@withContext failPolicy(
                        policyId,
                        AppError(
                            AppErrorCode.POLICY_VERIFICATION_FAILED,
                            result.error.message ?: "Policy could not be verified",
                            retryable = result.error.retryable,
                        ),
                    )
                }
            }

            if (!verification.isVerified) {
                return@withContext failPolicy(
                    policyId,
                    AppError(
                        AppErrorCode.POLICY_VERIFICATION_FAILED,
                        "Router did not confirm the requested policy",
                    ),
                    status = AccessPolicyStatus.UNKNOWN,
                )
            }

            updatePolicy(policyId, AccessPolicyStatus.ACTIVE, receipt.routerPolicyReference)
            successPolicy(policyId)
        }

    override suspend fun removePolicy(policyId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val entity = policyDao.getById(policyId)
                ?: return@withContext AppResult.Failure(
                    AppError(AppErrorCode.UNKNOWN, "Policy is not known"),
                )
            if (entity.status == AccessPolicyStatus.REMOVED.name) {
                return@withContext AppResult.Success(Unit)
            }
            val resolved = when (val result = resolveRouter(entity.routerId)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            updatePolicy(policyId, AccessPolicyStatus.REMOVING, entity.routerPolicyReference)
            emit(policyId, AccessPolicyStatus.REMOVING)

            val connected = connect(resolved)
            if (connected is AppResult.Failure) {
                return@withContext failPolicy(policyId, connected.error)
            }

            val reference = entity.routerPolicyReference
            if (reference != null) {
                val removal = guarded(APPLY_TIMEOUT_MS) {
                    resolved.adapter.removeAccessPolicy(reference)
                }
                if (removal is AppResult.Failure) {
                    return@withContext failPolicy(policyId, removal.error)
                }
            }

            val verification = verifyRemoval(resolved.adapter, entity)
            if (verification is AppResult.Failure) {
                return@withContext failPolicy(policyId, verification.error)
            }
            if (!(verification as AppResult.Success).data.isVerified) {
                return@withContext failPolicy(
                    policyId,
                    AppError(
                        AppErrorCode.POLICY_VERIFICATION_FAILED,
                        "Router did not confirm the policy removal",
                    ),
                    status = AccessPolicyStatus.UNKNOWN,
                )
            }
            updatePolicy(policyId, AccessPolicyStatus.REMOVED, reference)
            AppResult.Success(Unit)
        }

    override suspend fun verifyPolicy(policyId: String): AppResult<PolicyVerificationResult> =
        withContext(dispatchers.io) {
            val entity = policyDao.getById(policyId)
                ?: return@withContext AppResult.Failure(
                    AppError(AppErrorCode.UNKNOWN, "Policy is not known"),
                )
            val resolved = when (val result = resolveRouter(entity.routerId)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            val connected = connect(resolved)
            if (connected is AppResult.Failure) {
                return@withContext failPolicy(policyId, connected.error)
            }
            val observation = observationDao.getLatestForDevice(entity.deviceId)
            val expected = if (entity.status == AccessPolicyStatus.REMOVED.name) {
                AccessPolicyAction.ALLOW_INTERNET
            } else {
                entity.action.toAction()
            }
            val verification = when (
                val result = guarded(VERIFY_TIMEOUT_MS) {
                    resolved.adapter.verifyAccessPolicy(
                        PolicyVerificationRequest(
                            operationId = policyId,
                            routerPolicyReference = entity.routerPolicyReference,
                            expectedAction = expected,
                            deviceId = entity.deviceId,
                            targetMacAddress = observation?.macAddress,
                            targetIpAddress = observation?.ipAddress,
                        ),
                    )
                }
            ) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    updatePolicy(policyId, AccessPolicyStatus.UNKNOWN, entity.routerPolicyReference)
                    return@withContext AppResult.Failure(result.error)
                }
            }
            val status = when {
                !verification.isVerified -> AccessPolicyStatus.UNKNOWN
                expected == AccessPolicyAction.BLOCK_INTERNET -> AccessPolicyStatus.ACTIVE
                else -> AccessPolicyStatus.REMOVED
            }
            updatePolicy(policyId, status, entity.routerPolicyReference)
            AppResult.Success(verification)
        }

    private suspend fun resolveRouter(routerId: String): AppResult<ResolvedRouter> {
        val router = routerDao.getById(routerId)
            ?: return AppResult.Failure(
                AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router is not known"),
            )
        val type = runCatching { RouterAdapterType.valueOf(router.adapterType) }.getOrNull()
            ?: return AppResult.Failure(
                AppError(AppErrorCode.UNSUPPORTED_CAPABILITY, "Unknown router adapter"),
            )
        val adapter = adapterRegistry.getAdapter(type)
            ?: return AppResult.Failure(
                AppError(AppErrorCode.UNSUPPORTED_CAPABILITY, "Router adapter is not available"),
            )
        return AppResult.Success(ResolvedRouter(router, adapter))
    }

    private suspend fun connect(resolved: ResolvedRouter): AppResult<Unit> {
        val credentialId = resolved.router.credentialId
            ?: return AppResult.Failure(
                AppError(AppErrorCode.AUTHENTICATION_FAILED, "Router credentials are missing"),
            )
        val config = RouterConnectionConfig(
            routerId = resolved.router.id,
            host = resolved.router.ipAddress,
            port = resolved.router.port,
            protocol = runCatching { RouterProtocol.valueOf(resolved.router.protocol) }
                .getOrDefault(RouterProtocol.UNKNOWN),
            credentialId = credentialId,
        )
        return when (
            val result = guarded(CONNECT_TIMEOUT_MS) { resolved.adapter.connect(config) }
        ) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }

    private suspend fun findExistingReference(
        adapter: RouterAdapter,
        request: AccessPolicyRequest,
    ): ExistingPolicy {
        val policies = when (val result = adapter.getAccessPolicies()) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return ExistingPolicy(applies = false, reference = null)
        }
        val targetMac = request.targetMacAddress?.trim()?.lowercase()
        val targetIp = request.targetIpAddress?.trim()?.substringBefore("/")
        val matching = policies.filter { policy ->
            val identifier = policy.targetIdentifier.trim().lowercase().substringBefore("/")
            when {
                targetMac != null && identifier == targetMac -> true
                targetIp != null && identifier == targetIp -> true
                else -> false
            }
        }
        return when (request.action) {
            AccessPolicyAction.BLOCK_INTERNET -> ExistingPolicy(
                applies = matching.isNotEmpty(),
                reference = matching.firstOrNull()?.id,
            )

            AccessPolicyAction.ALLOW_INTERNET -> ExistingPolicy(
                applies = matching.isEmpty(),
                reference = null,
            )
        }
    }

    private suspend fun verifyRemoval(
        adapter: RouterAdapter,
        entity: AccessPolicyEntity,
    ): AppResult<PolicyVerificationResult> {
        val observation = observationDao.getLatestForDevice(entity.deviceId)
        return guarded(VERIFY_TIMEOUT_MS) {
            adapter.verifyAccessPolicy(
                PolicyVerificationRequest(
                    operationId = entity.id,
                    routerPolicyReference = entity.routerPolicyReference,
                    expectedAction = AccessPolicyAction.ALLOW_INTERNET,
                    deviceId = entity.deviceId,
                    targetMacAddress = observation?.macAddress,
                    targetIpAddress = observation?.ipAddress,
                ),
            )
        }
    }

    private suspend fun updatePolicy(
        policyId: String,
        status: AccessPolicyStatus,
        reference: String?,
    ) {
        policyDao.updateStatusAndReference(
            policyId = policyId,
            status = status.name,
            reference = reference,
            updatedAt = timeProvider.nowMillis(),
        )
        emit(policyId, status)
    }

    private suspend fun failPolicy(
        policyId: String,
        error: AppError,
        status: AccessPolicyStatus = if (error.retryable) {
            AccessPolicyStatus.UNKNOWN
        } else {
            AccessPolicyStatus.FAILED
        },
    ): AppResult.Failure {
        val entity = policyDao.getById(policyId)
        updatePolicy(policyId, status, entity?.routerPolicyReference)
        return AppResult.Failure(error)
    }

    private suspend fun successPolicy(policyId: String): AppResult<AccessPolicy> {
        val entity = policyDao.getById(policyId)
            ?: return AppResult.Failure(
                AppError(AppErrorCode.STORAGE_ERROR, "Policy could not be loaded"),
            )
        return AppResult.Success(entity.toDomain())
    }

    private suspend fun emit(policyId: String, status: AccessPolicyStatus) {
        eventBus.emit(AppEvent.AccessPolicyChanged(policyId, status))
    }

    private suspend fun <T> guarded(
        timeoutMs: Long,
        block: suspend () -> AppResult<T>,
    ): AppResult<T> = try {
        withTimeout(timeoutMs) { block() }
    } catch (timeout: TimeoutCancellationException) {
        AppResult.Failure(
            AppError(
                AppErrorCode.ROUTER_UNREACHABLE,
                "Router did not respond in time",
                retryable = true,
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        AppResult.Failure(
            AppError(AppErrorCode.UNKNOWN, "Router operation failed", retryable = true),
        )
    }

    private data class ResolvedRouter(
        val router: RouterEntity,
        val adapter: RouterAdapter,
    )

    private data class ExistingPolicy(
        val applies: Boolean,
        val reference: String?,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val APPLY_TIMEOUT_MS = 20_000L
        const val VERIFY_TIMEOUT_MS = 10_000L
    }
}

private fun AccessPolicyEntity.toDomain(): AccessPolicy = AccessPolicy(
    id = id,
    routerId = routerId,
    deviceId = deviceId,
    action = action.toAction(),
    status = runCatching { AccessPolicyStatus.valueOf(status) }
        .getOrDefault(AccessPolicyStatus.UNKNOWN),
    routerPolicyReference = routerPolicyReference,
    reason = reason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    expiresAt = expiresAt,
)

private fun String.toAction(): AccessPolicyAction =
    runCatching { AccessPolicyAction.valueOf(this) }.getOrDefault(AccessPolicyAction.BLOCK_INTERNET)
