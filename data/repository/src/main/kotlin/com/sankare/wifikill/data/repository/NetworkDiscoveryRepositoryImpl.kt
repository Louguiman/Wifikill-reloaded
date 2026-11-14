package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.ApplicationScope
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.DeviceDao
import com.sankare.wifikill.core.database.dao.DeviceObservationDao
import com.sankare.wifikill.core.database.dao.NetworkDao
import com.sankare.wifikill.core.database.dao.ScanSessionDao
import com.sankare.wifikill.core.database.entity.DeviceEntity
import com.sankare.wifikill.core.database.entity.ScanSessionEntity
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import com.sankare.wifikill.core.domain.model.DeviceType
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import com.sankare.wifikill.core.domain.model.ObservationSource
import com.sankare.wifikill.core.domain.model.ReachabilityStatus
import com.sankare.wifikill.core.domain.model.ScanEvent
import com.sankare.wifikill.core.domain.model.ScanRequest
import com.sankare.wifikill.core.domain.model.ScanSession
import com.sankare.wifikill.core.domain.model.ScanStatus
import com.sankare.wifikill.core.domain.repository.NetworkDiscoveryRepository
import com.sankare.wifikill.core.domain.service.AppEvent
import com.sankare.wifikill.core.domain.service.AppEventBus
import com.sankare.wifikill.core.domain.service.DeviceIdentityResolver
import com.sankare.wifikill.core.network.NetworkContextProvider
import com.sankare.wifikill.core.network.discovery.DiscoveredHost
import com.sankare.wifikill.core.network.discovery.DiscoveryEngine
import com.sankare.wifikill.core.network.discovery.DiscoveryEvent
import com.sankare.wifikill.data.mapper.toDomain
import com.sankare.wifikill.data.mapper.toEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NetworkDiscoveryRepositoryImpl @Inject constructor(
    private val discoveryEngine: DiscoveryEngine,
    private val networkContextProvider: NetworkContextProvider,
    private val networkDao: NetworkDao,
    private val deviceDao: DeviceDao,
    private val observationDao: DeviceObservationDao,
    private val scanSessionDao: ScanSessionDao,
    private val identityResolver: DeviceIdentityResolver,
    private val eventBus: AppEventBus,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    private val dispatchersProvider: DispatchersProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : NetworkDiscoveryRepository {

    private val sessionEvents = ConcurrentHashMap<String, MutableSharedFlow<ScanEvent>>()
    private val runningScans = ConcurrentHashMap<String, Job>()

    override fun observeCurrentNetwork(): Flow<NetworkContext?> =
        networkContextProvider.observeCurrentNetwork()

    override fun observeScanEvents(sessionId: String): Flow<ScanEvent> =
        eventsFor(sessionId).asSharedFlow()

    override suspend fun startScan(request: ScanRequest): AppResult<String> {
        val context = networkContextProvider.observeCurrentNetwork().first()
            ?: return AppResult.Failure(
                AppError(AppErrorCode.NETWORK_UNAVAILABLE, "No active network"),
            )

        if (context.networkType == NetworkType.CELLULAR) {
            return AppResult.Failure(
                AppError(AppErrorCode.INVALID_NETWORK_CONTEXT, "Cellular networks are not supported"),
            )
        }

        val networkId = context.networkId
            ?: return AppResult.Failure(
                AppError(AppErrorCode.INVALID_NETWORK_CONTEXT, "Network identity is unavailable"),
            )

        if (request.networkId != networkId) {
            return AppResult.Failure(
                AppError(AppErrorCode.INVALID_NETWORK_CONTEXT, "Network context changed"),
            )
        }

        val now = timeProvider.nowMillis()
        val existingNetwork = networkDao.getById(networkId)
        networkDao.markCurrent(context.toEntity(existingNetwork, now))

        val sessionId = idGenerator.newId()
        scanSessionDao.upsert(
            ScanSessionEntity(
                id = sessionId,
                networkId = networkId,
                startedAt = now,
                status = ScanStatus.RUNNING.name,
                scanMethod = "LOCAL_DISCOVERY",
            ),
        )

        val events = eventsFor(sessionId)
        events.tryEmit(ScanEvent.Started(sessionId, now))

        val job = applicationScope.launch(
            context = dispatchersProvider.io,
            start = CoroutineStart.LAZY,
        ) {
            runScan(sessionId, networkId, context, request)
        }
        runningScans[sessionId] = job
        job.start()

        return AppResult.Success(sessionId)
    }

    override suspend fun cancelScan(sessionId: String): AppResult<Unit> {
        val session = scanSessionDao.getById(sessionId)
        if (session != null && session.status != ScanStatus.RUNNING.name) {
            return AppResult.Success(Unit)
        }

        runningScans[sessionId]?.cancel()

        val completedAt = timeProvider.nowMillis()
        scanSessionDao.updateResult(
            sessionId = sessionId,
            status = ScanStatus.CANCELLED.name,
            completedAt = completedAt,
            devicesFound = scanSessionDao.getById(sessionId)?.devicesFound ?: 0,
            errorMessage = "Cancelled",
        )
        eventsFor(sessionId).tryEmit(
            ScanEvent.Failed(
                sessionId,
                AppError(AppErrorCode.OPERATION_CANCELLED, "Scan cancelled"),
            ),
        )
        return AppResult.Success(Unit)
    }

    override suspend fun getScan(sessionId: String): ScanSession? =
        scanSessionDao.getById(sessionId)?.toDomain()

    override fun observeDevices(networkId: String): Flow<List<Device>> =
        deviceDao.observeByNetwork(networkId).map { devices -> devices.map { it.toDomain() } }

    private suspend fun runScan(
        sessionId: String,
        networkId: String,
        context: NetworkContext,
        request: ScanRequest,
    ) {
        var devicesFound = 0
        var successfulSources = 0
        var lastError: AppError? = null

        try {
            discoveryEngine
                .discover(context, request.sources, request.timeoutMs)
                .collect { event ->
                    when (event) {
                        is DiscoveryEvent.SourceStarted -> emit(
                            sessionId,
                            ScanEvent.SourceStarted(event.source),
                        )

                        is DiscoveryEvent.HostFound -> {
                            val observation = persistObservation(
                                networkId = networkId,
                                sessionId = sessionId,
                                source = event.source,
                                host = event.host,
                            )
                            if (observation != null) {
                                devicesFound++
                                emit(sessionId, ScanEvent.DeviceDiscovered(observation))
                                observation.deviceId?.let { deviceId ->
                                    eventBus.emit(AppEvent.DeviceDiscovered(deviceId))
                                }
                            }
                        }

                        is DiscoveryEvent.SourceCompleted -> {
                            successfulSources++
                            emit(
                                sessionId,
                                ScanEvent.SourceCompleted(event.source, event.devicesFound),
                            )
                        }

                        is DiscoveryEvent.SourceUnavailable -> Unit

                        is DiscoveryEvent.SourceFailed -> {
                            lastError = event.error
                        }
                    }
                }

            val completedAt = timeProvider.nowMillis()
            if (successfulSources == 0 && lastError != null) {
                val error = requireNotNull(lastError)
                scanSessionDao.updateResult(
                    sessionId = sessionId,
                    status = ScanStatus.FAILED.name,
                    completedAt = completedAt,
                    devicesFound = devicesFound,
                    errorMessage = error.message ?: error.code.name,
                )
                emit(sessionId, ScanEvent.Failed(sessionId, error))
            } else {
                scanSessionDao.updateResult(
                    sessionId = sessionId,
                    status = ScanStatus.COMPLETED.name,
                    completedAt = completedAt,
                    devicesFound = devicesFound,
                    errorMessage = null,
                )
                emit(sessionId, ScanEvent.Completed(sessionId, devicesFound, completedAt))
                eventBus.emit(AppEvent.ScanCompleted(sessionId))
            }
        } catch (e: CancellationException) {
            scanSessionDao.updateResult(
                sessionId = sessionId,
                status = ScanStatus.CANCELLED.name,
                completedAt = timeProvider.nowMillis(),
                devicesFound = devicesFound,
                errorMessage = "Cancelled",
            )
            emit(
                sessionId,
                ScanEvent.Failed(
                    sessionId,
                    AppError(AppErrorCode.OPERATION_CANCELLED, "Scan cancelled"),
                ),
            )
            throw e
        } catch (e: Exception) {
            val error = AppError(AppErrorCode.UNKNOWN, e.message)
            scanSessionDao.updateResult(
                sessionId = sessionId,
                status = ScanStatus.FAILED.name,
                completedAt = timeProvider.nowMillis(),
                devicesFound = devicesFound,
                errorMessage = error.message,
            )
            emit(sessionId, ScanEvent.Failed(sessionId, error))
        } finally {
            runningScans.remove(sessionId)
            applicationScope.launch {
                delay(SESSION_EVENT_RETENTION_MS)
                sessionEvents.remove(sessionId)
            }
        }
    }

    private suspend fun persistObservation(
        networkId: String,
        sessionId: String,
        source: ObservationSource,
        host: DiscoveredHost,
    ): DeviceObservation? {
        val observedAt = timeProvider.nowMillis()
        val unresolved = DeviceObservation(
            id = idGenerator.newId(),
            deviceId = null,
            networkId = networkId,
            scanSessionId = sessionId,
            ipAddress = host.ipAddress,
            macAddress = host.macAddress,
            hostname = host.hostname,
            manufacturer = host.manufacturer,
            source = source,
            reachability = ReachabilityStatus.RESPONDING,
            responseTimeMs = host.responseTimeMs,
            observedAt = observedAt,
        )

        val deviceId = resolveDeviceId(unresolved) ?: return null
        val observation = unresolved.copy(deviceId = deviceId)
        observationDao.insert(observation.toEntity())
        return observation
    }

    private suspend fun resolveDeviceId(observation: DeviceObservation): String? {
        val macAddress = observation.macAddress
            ?.trim()
            ?.lowercase()
            ?.replace('-', ':')
            ?.takeIf { it.isNotBlank() }

        if (macAddress != null) {
            val existingDeviceId = deviceDao.findDeviceIdByMac(macAddress)
            if (existingDeviceId != null && deviceDao.getById(existingDeviceId) != null) {
                return existingDeviceId
            }
        }

        val candidates = observation.hostname
            ?.let { deviceDao.findByHostname(it) }
            .orEmpty()
            .map { it.toDomain() }

        val resolution = identityResolver.resolve(observation, candidates)
        if (!resolution.shouldCreateNewDevice && resolution.deviceId != null) {
            return resolution.deviceId
        }

        val now = timeProvider.nowMillis()
        val deviceId = resolution.deviceId ?: idGenerator.newId()
        deviceDao.upsert(
            DeviceEntity(
                id = deviceId,
                hostname = observation.hostname,
                manufacturer = observation.manufacturer,
                deviceType = DeviceType.UNKNOWN.name,
                identityConfidence = resolution.confidence.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return deviceId
    }

    private fun eventsFor(sessionId: String): MutableSharedFlow<ScanEvent> =
        sessionEvents.getOrPut(sessionId) {
            MutableSharedFlow(
                replay = SESSION_EVENT_REPLAY,
                extraBufferCapacity = SESSION_EVENT_REPLAY,
            )
        }

    private fun emit(sessionId: String, event: ScanEvent) {
        eventsFor(sessionId).tryEmit(event)
    }

    private companion object {
        const val SESSION_EVENT_REPLAY = 128
        const val SESSION_EVENT_RETENTION_MS = 5 * 60 * 1000L
    }
}
