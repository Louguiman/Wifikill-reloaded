package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.DiagnosticResultDao
import com.sankare.wifikill.core.database.dao.NetworkDao
import com.sankare.wifikill.core.domain.model.DiagnosticExecutionEvent
import com.sankare.wifikill.core.domain.model.DiagnosticRequest
import com.sankare.wifikill.core.domain.model.DiagnosticResult
import com.sankare.wifikill.core.domain.model.DiagnosticStatus
import com.sankare.wifikill.core.domain.model.DiagnosticType
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.repository.DiagnosticsRepository
import com.sankare.wifikill.core.domain.repository.SettingsRepository
import com.sankare.wifikill.core.network.NetworkContextProvider
import com.sankare.wifikill.core.network.diagnostics.DiagnosticProbe
import com.sankare.wifikill.data.mapper.toDomain
import com.sankare.wifikill.data.mapper.toEntity
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class DiagnosticsRepositoryImpl @Inject constructor(
    private val networkContextProvider: NetworkContextProvider,
    private val probe: DiagnosticProbe,
    private val networkDao: NetworkDao,
    private val resultDao: DiagnosticResultDao,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    private val dispatchers: DispatchersProvider,
) : DiagnosticsRepository {

    override fun execute(request: DiagnosticRequest): Flow<DiagnosticExecutionEvent> = flow {
        emit(DiagnosticExecutionEvent.Started)

        val context = networkContextProvider.observeCurrentNetwork().first()
        if (context == null) {
            emit(
                DiagnosticExecutionEvent.Failed(
                    AppError(AppErrorCode.NETWORK_UNAVAILABLE, "No active network"),
                ),
            )
            return@flow
        }
        if (context.networkId == null || context.networkId != request.networkId) {
            emit(
                DiagnosticExecutionEvent.Failed(
                    AppError(
                        AppErrorCode.INVALID_NETWORK_CONTEXT,
                        "Network context changed",
                    ),
                ),
            )
            return@flow
        }

        val target = resolveTarget(request, context)
        emit(DiagnosticExecutionEvent.Progress(progressMessage(request.type, target)))

        persistNetwork(context)
        val outcome = runDiagnostic(request.type, target, context)
        val executedAt = timeProvider.nowMillis()
        val result = DiagnosticResult(
            id = idGenerator.newId(),
            networkId = request.networkId,
            type = request.type,
            target = target,
            status = outcome.status,
            latencyMs = outcome.latencyMs,
            resultMessage = outcome.message,
            executedAt = executedAt,
        )
        resultDao.insert(result.toEntity())
        val retentionDays = settingsRepository.getSettings().diagnosticsRetentionDays
        resultDao.deleteOlderThan(executedAt - retentionDays.toLong() * MILLIS_PER_DAY)

        emit(DiagnosticExecutionEvent.Completed(result))
    }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(
                DiagnosticExecutionEvent.Failed(
                    AppError(AppErrorCode.UNKNOWN, throwable.message ?: "Diagnostic failed"),
                ),
            )
        }
        .flowOn(dispatchers.io)

    override fun observeHistory(networkId: String): Flow<List<DiagnosticResult>> =
        resultDao.observeByNetwork(networkId)
            .map { results -> results.map { it.toDomain() } }

    private suspend fun persistNetwork(context: NetworkContext) {
        val networkId = requireNotNull(context.networkId)
        networkDao.markCurrent(
            context.toEntity(
                existing = networkDao.getById(networkId),
                now = timeProvider.nowMillis(),
            ),
        )
    }

    private suspend fun runDiagnostic(
        type: DiagnosticType,
        target: String?,
        context: NetworkContext,
    ): Outcome = when (type) {
        DiagnosticType.GATEWAY_REACHABILITY -> checkGateway(target)
        DiagnosticType.DNS_RESOLUTION -> checkDns(target)
        DiagnosticType.INTERNET_CONNECTIVITY -> checkInternet(target)
        DiagnosticType.LATENCY -> measureLatency(target)
        DiagnosticType.PACKET_LOSS -> measurePacketLoss(target)
        DiagnosticType.NETWORK_CONFIGURATION -> describeConfiguration(context)
    }

    private suspend fun checkGateway(target: String?): Outcome {
        if (target == null) {
            return Outcome(
                DiagnosticStatus.UNAVAILABLE,
                null,
                "Gateway address is unavailable",
            )
        }
        val ping = probe.ping(target, PING_TIMEOUT_MS)
        if (ping.reachable) {
            return Outcome(
                DiagnosticStatus.SUCCESS,
                ping.latencyMs,
                "Gateway $target responded to the reachability probe",
            )
        }
        GATEWAY_TCP_PORTS.forEach { port ->
            val tcp = probe.tcpConnect(target, port, TCP_TIMEOUT_MS)
            if (tcp.reachable) {
                return Outcome(
                    DiagnosticStatus.SUCCESS,
                    tcp.latencyMs,
                    "Gateway $target responded on TCP port $port",
                )
            }
        }
        return Outcome(
            DiagnosticStatus.FAILED,
            null,
            "Gateway $target did not respond. Check the gateway address and local network.",
        )
    }

    private suspend fun checkDns(target: String?): Outcome {
        val host = target ?: DEFAULT_DNS_TARGET
        val dns = probe.resolve(host, DNS_TIMEOUT_MS)
        return if (dns.resolved) {
            val addresses = dns.addresses.take(MAX_REPORTED_ADDRESSES).joinToString(", ")
            Outcome(
                DiagnosticStatus.SUCCESS,
                dns.latencyMs,
                "Resolved $host to ${addresses.ifEmpty { "an address" }}",
            )
        } else {
            Outcome(
                DiagnosticStatus.FAILED,
                dns.latencyMs,
                dns.detail ?: "DNS resolution failed for $host",
            )
        }
    }

    private suspend fun checkInternet(target: String?): Outcome {
        val host = target ?: DEFAULT_INTERNET_TARGET
        val tcp = probe.tcpConnect(host, INTERNET_PORT, TCP_TIMEOUT_MS)
        return if (tcp.reachable) {
            Outcome(
                DiagnosticStatus.SUCCESS,
                tcp.latencyMs,
                "Connected to internet endpoint $host:$INTERNET_PORT",
            )
        } else {
            Outcome(
                DiagnosticStatus.FAILED,
                tcp.latencyMs,
                "Could not reach internet endpoint $host:$INTERNET_PORT",
            )
        }
    }

    private suspend fun measureLatency(target: String?): Outcome {
        if (target == null) {
            return Outcome(DiagnosticStatus.UNAVAILABLE, null, "No latency target is available")
        }
        val ping = probe.ping(target, PING_TIMEOUT_MS)
        return if (ping.reachable) {
            Outcome(
                DiagnosticStatus.SUCCESS,
                ping.latencyMs,
                "Response latency to $target: ${ping.latencyMs ?: "unknown"} ms",
            )
        } else {
            Outcome(
                DiagnosticStatus.FAILED,
                null,
                "No response from $target",
            )
        }
    }

    private suspend fun measurePacketLoss(target: String?): Outcome {
        if (target == null) {
            return Outcome(
                DiagnosticStatus.UNAVAILABLE,
                null,
                "No packet-loss target is available",
            )
        }
        val probes = (1..PACKET_PROBE_COUNT).map {
            probe.ping(target, PACKET_PROBE_TIMEOUT_MS)
        }
        val responded = probes.count { it.reachable }
        val latencies = probes.mapNotNull { it.latencyMs }.filter { it >= 0 }
        val averageLatency = latencies.takeIf { it.isNotEmpty() }?.average()?.toLong()
        val status = when {
            responded == PACKET_PROBE_COUNT -> DiagnosticStatus.SUCCESS
            responded > 0 -> DiagnosticStatus.WARNING
            else -> DiagnosticStatus.FAILED
        }
        val message = buildString {
            append("$responded/$PACKET_PROBE_COUNT probes responded from $target")
            averageLatency?.let { append(" • average ${it} ms") }
        }
        return Outcome(status, averageLatency, message)
    }

    private fun describeConfiguration(context: NetworkContext): Outcome {
        val values: List<Pair<String, String>> = listOf(
            "Local IP" to (context.localIp ?: "unknown"),
            "Subnet" to (context.subnet ?: "unknown"),
            "Gateway" to (context.gatewayIp ?: "unknown"),
            "DNS" to (
                context.dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: "unavailable"
                ),
        )
        val available = context.localIp != null ||
            context.subnet != null ||
            context.gatewayIp != null ||
            context.dnsServers.isNotEmpty()
        if (!available) {
            return Outcome(
                DiagnosticStatus.UNAVAILABLE,
                null,
                "The active network did not report configuration details",
            )
        }
        return Outcome(
            DiagnosticStatus.SUCCESS,
            null,
            values.joinToString(" • ") { (label, value) -> "$label: $value" },
        )
    }

    private fun resolveTarget(request: DiagnosticRequest, context: NetworkContext): String? {
        val requested = request.target?.trim()?.takeIf { it.isNotEmpty() }
        return when (request.type) {
            DiagnosticType.GATEWAY_REACHABILITY,
            DiagnosticType.LATENCY,
            DiagnosticType.PACKET_LOSS,
            -> requested ?: context.gatewayIp

            DiagnosticType.DNS_RESOLUTION -> requested ?: DEFAULT_DNS_TARGET
            DiagnosticType.INTERNET_CONNECTIVITY -> requested ?: DEFAULT_INTERNET_TARGET
            DiagnosticType.NETWORK_CONFIGURATION -> requested ?: context.localIp
        }
    }

    private fun progressMessage(type: DiagnosticType, target: String?): String = when (type) {
        DiagnosticType.GATEWAY_REACHABILITY -> "Checking gateway ${target ?: "address"}"
        DiagnosticType.DNS_RESOLUTION -> "Resolving ${target ?: DEFAULT_DNS_TARGET}"
        DiagnosticType.INTERNET_CONNECTIVITY ->
            "Checking internet connection to ${target ?: DEFAULT_INTERNET_TARGET}"

        DiagnosticType.LATENCY -> "Measuring latency to ${target ?: "gateway"}"
        DiagnosticType.PACKET_LOSS -> "Probing ${target ?: "gateway"} for packet loss"
        DiagnosticType.NETWORK_CONFIGURATION -> "Reading active network configuration"
    }

    private data class Outcome(
        val status: DiagnosticStatus,
        val latencyMs: Long?,
        val message: String,
    )

    private companion object {
        const val PING_TIMEOUT_MS = 1_500
        const val TCP_TIMEOUT_MS = 1_500
        const val DNS_TIMEOUT_MS = 3_000
        const val PACKET_PROBE_TIMEOUT_MS = 700
        const val PACKET_PROBE_COUNT = 3
        const val MAX_REPORTED_ADDRESSES = 2
        const val INTERNET_PORT = 443
        const val DEFAULT_DNS_TARGET = "example.com"
        const val DEFAULT_INTERNET_TARGET = "1.1.1.1"
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1_000
        val GATEWAY_TCP_PORTS = listOf(80, 443, 53)
    }
}
