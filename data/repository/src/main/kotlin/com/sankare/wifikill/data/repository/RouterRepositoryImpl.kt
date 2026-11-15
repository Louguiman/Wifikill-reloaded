package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.NetworkDao
import com.sankare.wifikill.core.database.dao.RouterCapabilityDao
import com.sankare.wifikill.core.database.dao.RouterDao
import com.sankare.wifikill.core.database.entity.RouterCapabilityEntity
import com.sankare.wifikill.core.database.entity.RouterEntity
import com.sankare.wifikill.core.domain.model.AddRouterRequest
import com.sankare.wifikill.core.domain.model.Router
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterConnectionConfig
import com.sankare.wifikill.core.domain.model.RouterConnectionStatus
import com.sankare.wifikill.core.domain.model.RouterInfo
import com.sankare.wifikill.core.domain.model.RouterProtocol
import com.sankare.wifikill.core.domain.repository.CredentialProvider
import com.sankare.wifikill.core.domain.repository.RouterCredential
import com.sankare.wifikill.core.domain.repository.RouterRepository
import com.sankare.wifikill.core.domain.service.AppEvent
import com.sankare.wifikill.core.domain.service.AppEventBus
import com.sankare.wifikill.core.network.NetworkContextProvider
import com.sankare.wifikill.data.mapper.toDomain
import com.sankare.wifikill.data.mapper.toEntity
import com.sankare.wifikill.integrations.router.RouterAdapter
import com.sankare.wifikill.integrations.router.RouterAdapterRegistry
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class RouterRepositoryImpl @Inject constructor(
    private val routerDao: RouterDao,
    private val capabilityDao: RouterCapabilityDao,
    private val networkDao: NetworkDao,
    private val networkContextProvider: NetworkContextProvider,
    private val adapterRegistry: RouterAdapterRegistry,
    private val credentialProvider: CredentialProvider,
    private val eventBus: AppEventBus,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatchersProvider,
) : RouterRepository {

    override fun observeRouters(networkId: String): Flow<List<Router>> =
        routerDao.observeByNetworkWithCapabilities(networkId).map { routers ->
            routers.map { it.toDomain() }
        }

    override suspend fun getRouter(routerId: String): Router? =
        withContext(dispatchers.io) {
            routerDao.getWithCapabilities(routerId)?.toDomain()
        }

    override suspend fun addRouter(request: AddRouterRequest): AppResult<Router> =
        withContext(dispatchers.io) {
            val context = networkContextProvider.observeCurrentNetwork().first()
                ?: return@withContext AppResult.Failure(
                    AppError(AppErrorCode.NETWORK_UNAVAILABLE, "No active network"),
                )
            if (context.networkId != request.networkId) {
                return@withContext AppResult.Failure(
                    AppError(AppErrorCode.INVALID_NETWORK_CONTEXT, "Network context changed"),
                )
            }

            val now = timeProvider.nowMillis()
            val existingNetwork = networkDao.getById(request.networkId)
            networkDao.markCurrent(context.toEntity(existingNetwork, now))

            val credentialId = when (
                val saved = credentialProvider.save(
                    RouterCredential(
                        username = request.username,
                        password = request.password,
                    ),
                )
            ) {
                is AppResult.Success -> saved.data
                is AppResult.Failure -> return@withContext AppResult.Failure(saved.error)
            }

            val routerId = idGenerator.newId()
            routerDao.upsert(
                RouterEntity(
                    id = routerId,
                    networkId = request.networkId,
                    name = request.name,
                    ipAddress = request.host,
                    port = request.port,
                    protocol = request.protocol.name,
                    adapterType = request.adapterType.name,
                    connectionStatus = RouterConnectionStatus.UNKNOWN.name,
                    credentialId = credentialId,
                    createdAt = now,
                ),
            )

            connectInternal(routerId)
            val router = routerDao.getWithCapabilities(routerId)?.toDomain()
                ?: return@withContext AppResult.Failure(
                    AppError(AppErrorCode.STORAGE_ERROR, "Router could not be loaded"),
                )
            AppResult.Success(router)
        }

    override suspend fun connectRouter(routerId: String): AppResult<RouterInfo> =
        withContext(dispatchers.io) { connectInternal(routerId) }

    private suspend fun connectInternal(routerId: String): AppResult<RouterInfo> {
        val router = routerDao.getById(routerId)
            ?: return AppResult.Failure(
                AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router is not known"),
            )
        val adapterType = runCatching { RouterAdapterType.valueOf(router.adapterType) }
            .getOrNull()
            ?: return AppResult.Failure(
                AppError(AppErrorCode.UNSUPPORTED_CAPABILITY, "Unknown router adapter"),
            )
        val adapter = adapterRegistry.getAdapter(adapterType)
            ?: return AppResult.Failure(
                AppError(AppErrorCode.UNSUPPORTED_CAPABILITY, "Router adapter is not available"),
            )
        val credentialId = router.credentialId
            ?: return AppResult.Failure(
                AppError(AppErrorCode.AUTHENTICATION_FAILED, "Router credentials are missing"),
            )

        routerDao.updateStatus(
            routerId = routerId,
            status = RouterConnectionStatus.CONNECTING.name,
            lastConnectedAt = router.lastConnectedAt,
        )

        val config = RouterConnectionConfig(
            routerId = routerId,
            host = router.ipAddress,
            port = router.port,
            protocol = runCatching { RouterProtocol.valueOf(router.protocol) }
                .getOrDefault(RouterProtocol.UNKNOWN),
            credentialId = credentialId,
        )

        val outcome = try {
            withTimeout(CONNECT_TIMEOUT_MS) {
                adapter.connect(config)
            }
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
                AppError(AppErrorCode.UNKNOWN, "Router connection failed"),
            )
        }

        return when (outcome) {
            is AppResult.Success -> {
                val now = timeProvider.nowMillis()
                persistCapabilities(routerId, adapter, now)
                routerDao.updateInfo(
                    routerId = routerId,
                    manufacturer = outcome.data.manufacturer,
                    model = outcome.data.model,
                    firmwareVersion = outcome.data.firmwareVersion,
                    status = RouterConnectionStatus.CONNECTED.name,
                    lastConnectedAt = now,
                )
                eventBus.emit(AppEvent.RouterConnectionChanged(routerId, connected = true))
                outcome
            }

            is AppResult.Failure -> {
                markFailure(router, outcome.error.code)
                AppResult.Failure(outcome.error)
            }
        }
    }

    override suspend fun disconnectRouter(routerId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val router = routerDao.getById(routerId)
                ?: return@withContext AppResult.Failure(
                    AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router is not known"),
                )
            runCatching { resolveAdapter(router)?.disconnect() }
            routerDao.updateStatus(
                routerId = routerId,
                status = RouterConnectionStatus.DISCONNECTED.name,
                lastConnectedAt = router.lastConnectedAt,
            )
            capabilityDao.deleteByRouter(routerId)
            eventBus.emit(AppEvent.RouterConnectionChanged(routerId, connected = false))
            AppResult.Success(Unit)
        }

    override suspend fun getCapabilities(routerId: String): Set<RouterCapability> =
        withContext(dispatchers.io) {
            capabilityDao.getByRouter(routerId)
                .mapNotNull { it.toDomain() }
                .toSet()
        }

    override suspend fun refreshCapabilities(
        routerId: String,
    ): AppResult<Set<RouterCapability>> = withContext(dispatchers.io) {
        val router = routerDao.getById(routerId)
            ?: return@withContext AppResult.Failure(
                AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router is not known"),
            )
        val adapter = resolveAdapter(router)
            ?: return@withContext AppResult.Failure(
                AppError(AppErrorCode.UNSUPPORTED_CAPABILITY, "Router adapter is not available"),
            )
        val capabilities = try {
            withTimeout(CAPABILITY_TIMEOUT_MS) { adapter.getCapabilities() }
        } catch (timeout: TimeoutCancellationException) {
            return@withContext AppResult.Failure(
                AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router did not respond in time", retryable = true),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        persistCapabilities(routerId, capabilities, timeProvider.nowMillis())
        AppResult.Success(capabilities)
    }

    override suspend fun removeRouter(routerId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val router = routerDao.getById(routerId) ?: return@withContext AppResult.Success(Unit)
            val credentialId = router.credentialId
            if (credentialId != null) {
                when (val deleted = credentialProvider.delete(credentialId)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> return@withContext AppResult.Failure(deleted.error)
                }
            }
            runCatching { resolveAdapter(router)?.disconnect() }
            capabilityDao.deleteByRouter(routerId)
            routerDao.deleteById(routerId)
            eventBus.emit(AppEvent.RouterConnectionChanged(routerId, connected = false))
            AppResult.Success(Unit)
        }

    private suspend fun persistCapabilities(
        routerId: String,
        adapter: RouterAdapter,
        now: Long,
    ) {
        persistCapabilities(routerId, adapter.getCapabilities(), now)
    }

    private suspend fun persistCapabilities(
        routerId: String,
        capabilities: Set<RouterCapability>,
        now: Long,
    ) {
        capabilityDao.deleteByRouter(routerId)
        if (capabilities.isNotEmpty()) {
            capabilityDao.upsertAll(
                capabilities.map { capability ->
                    RouterCapabilityEntity(
                        id = idGenerator.newId(),
                        routerId = routerId,
                        capabilityCode = capability.name,
                        isSupported = true,
                        detectedAt = now,
                    )
                },
            )
        }
    }

    private suspend fun markFailure(router: RouterEntity, code: AppErrorCode) {
        val status = when (code) {
            AppErrorCode.AUTHENTICATION_FAILED -> RouterConnectionStatus.AUTHENTICATION_FAILED
            AppErrorCode.ROUTER_UNREACHABLE -> RouterConnectionStatus.UNREACHABLE
            else -> RouterConnectionStatus.UNKNOWN
        }
        routerDao.updateStatus(
            routerId = router.id,
            status = status.name,
            lastConnectedAt = router.lastConnectedAt,
        )
        eventBus.emit(AppEvent.RouterConnectionChanged(router.id, connected = false))
    }

    private fun resolveAdapter(router: RouterEntity): RouterAdapter? {
        val type = runCatching { RouterAdapterType.valueOf(router.adapterType) }.getOrNull()
            ?: return null
        return adapterRegistry.getAdapter(type)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val CAPABILITY_TIMEOUT_MS = 10_000L
    }
}
