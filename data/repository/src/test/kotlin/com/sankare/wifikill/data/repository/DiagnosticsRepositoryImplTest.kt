package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.DiagnosticResultDao
import com.sankare.wifikill.core.database.dao.NetworkDao
import com.sankare.wifikill.core.database.entity.DiagnosticResultEntity
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.model.DiagnosticExecutionEvent
import com.sankare.wifikill.core.domain.model.DiagnosticRequest
import com.sankare.wifikill.core.domain.model.DiagnosticStatus
import com.sankare.wifikill.core.domain.model.DiagnosticType
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import com.sankare.wifikill.core.domain.repository.SettingsRepository
import com.sankare.wifikill.core.network.NetworkContextProvider
import com.sankare.wifikill.core.network.diagnostics.DiagnosticProbe
import com.sankare.wifikill.core.network.diagnostics.DnsOutcome
import com.sankare.wifikill.core.network.diagnostics.ProbeOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsRepositoryImplTest {

    private val networkContextProvider = mockk<NetworkContextProvider>()
    private val probe = mockk<DiagnosticProbe>()
    private val networkDao = mockk<NetworkDao>()
    private val resultDao = mockk<DiagnosticResultDao>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val idGenerator = mockk<IdGenerator>()
    private val inserted = mutableListOf<DiagnosticResultEntity>()

    private val repository = DiagnosticsRepositoryImpl(
        networkContextProvider = networkContextProvider,
        probe = probe,
        networkDao = networkDao,
        resultDao = resultDao,
        settingsRepository = settingsRepository,
        timeProvider = timeProvider,
        idGenerator = idGenerator,
        dispatchers = object : DispatchersProvider {
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val main = Dispatchers.Unconfined
        },
    )

    @Before
    fun setUp() {
        every { networkContextProvider.observeCurrentNetwork() } returns flowOf(context())
        coEvery { networkDao.getById(NETWORK_ID) } returns null
        coEvery { networkDao.markCurrent(any()) } just runs
        coEvery { resultDao.insert(any()) } answers {
            inserted += firstArg<DiagnosticResultEntity>()
        }
        coEvery { resultDao.deleteOlderThan(any()) } returns 0
        coEvery { settingsRepository.getSettings() } returns AppSettings()
        every { timeProvider.nowMillis() } returns NOW
        every { idGenerator.newId() } returns RESULT_ID
    }

    @Test
    fun `gateway success does not imply internet success`() = runTest {
        coEvery { probe.ping(GATEWAY, any()) } returns ProbeOutcome(true, 12)
        coEvery { probe.tcpConnect(INTERNET_TARGET, any(), any()) } returns ProbeOutcome(
            reachable = false,
            latencyMs = null,
            detail = "Connection refused",
        )

        val gatewayEvents = repository.execute(request(DiagnosticType.GATEWAY_REACHABILITY))
            .toList()
        val internetEvents = repository.execute(request(DiagnosticType.INTERNET_CONNECTIVITY))
            .toList()

        val gateway = completedResult(gatewayEvents)
        val internet = completedResult(internetEvents)
        assertEquals(DiagnosticStatus.SUCCESS, gateway.status)
        assertEquals(GATEWAY, gateway.target)
        assertEquals(DiagnosticStatus.FAILED, internet.status)
        assertEquals(INTERNET_TARGET, internet.target)
        assertEquals(2, inserted.size)
    }

    @Test
    fun `gateway falls back to a TCP probe when reachability fails`() = runTest {
        coEvery { probe.ping(GATEWAY, any()) } returns ProbeOutcome(false, null)
        coEvery { probe.tcpConnect(GATEWAY, 80, any()) } returns ProbeOutcome(false, null)
        coEvery { probe.tcpConnect(GATEWAY, 443, any()) } returns ProbeOutcome(true, 25)

        val result = completedResult(
            repository.execute(request(DiagnosticType.GATEWAY_REACHABILITY)).toList(),
        )

        assertEquals(DiagnosticStatus.SUCCESS, result.status)
        assertEquals(25L, result.latencyMs)
        assertTrue(result.resultMessage.orEmpty().contains("TCP port 443"))
    }

    @Test
    fun `dns failure is reported as an actionable failed result`() = runTest {
        coEvery { probe.resolve(DEFAULT_DNS_TARGET, any()) } returns DnsOutcome(
            resolved = false,
            detail = "DNS resolution failed",
        )

        val result = completedResult(
            repository.execute(request(DiagnosticType.DNS_RESOLUTION)).toList(),
        )

        assertEquals(DiagnosticStatus.FAILED, result.status)
        assertEquals(DEFAULT_DNS_TARGET, result.target)
        assertEquals("DNS resolution failed", result.resultMessage)
    }

    @Test
    fun `partial packet loss is a warning with observed responses only`() = runTest {
        coEvery { probe.ping(GATEWAY, any()) } returnsMany listOf(
            ProbeOutcome(true, 10),
            ProbeOutcome(false, null),
            ProbeOutcome(true, 30),
        )

        val result = completedResult(
            repository.execute(request(DiagnosticType.PACKET_LOSS)).toList(),
        )

        assertEquals(DiagnosticStatus.WARNING, result.status)
        assertEquals(20L, result.latencyMs)
        assertTrue(result.resultMessage.orEmpty().contains("2/3 probes responded"))
    }

    @Test
    fun `network configuration includes DNS servers when reported`() = runTest {
        val result = completedResult(
            repository.execute(request(DiagnosticType.NETWORK_CONFIGURATION)).toList(),
        )

        assertEquals(DiagnosticStatus.SUCCESS, result.status)
        assertTrue(result.resultMessage.orEmpty().contains("DNS: 1.1.1.1, 8.8.8.8"))
    }

    @Test
    fun `changed network context fails without storing a result`() = runTest {
        val events = repository.execute(
            DiagnosticRequest(
                networkId = "other-network",
                type = DiagnosticType.GATEWAY_REACHABILITY,
                target = null,
            ),
        ).toList()

        assertEquals(2, events.size)
        assertTrue(events.first() is DiagnosticExecutionEvent.Started)
        assertEquals(
            AppErrorCode.INVALID_NETWORK_CONTEXT,
            (events.last() as DiagnosticExecutionEvent.Failed).error.code,
        )
        coVerify(exactly = 0) { resultDao.insert(any()) }
    }

    @Test
    fun `history maps persisted diagnostic results`() = runTest {
        coEvery { resultDao.observeByNetwork(NETWORK_ID) } returns flowOf(
            listOf(
                DiagnosticResultEntity(
                    id = RESULT_ID,
                    networkId = NETWORK_ID,
                    diagnosticType = DiagnosticType.DNS_RESOLUTION.name,
                    target = DEFAULT_DNS_TARGET,
                    status = DiagnosticStatus.SUCCESS.name,
                    latencyMs = 14,
                    resultMessage = "Resolved",
                    executedAt = NOW,
                ),
            ),
        )

        val history = repository.observeHistory(NETWORK_ID).toList().first()

        assertEquals(1, history.size)
        assertEquals(DiagnosticType.DNS_RESOLUTION, history.first().type)
        assertEquals(DiagnosticStatus.SUCCESS, history.first().status)
    }

    private fun completedResult(events: List<DiagnosticExecutionEvent>) =
        (events.last() as DiagnosticExecutionEvent.Completed).result

    private fun request(type: DiagnosticType) = DiagnosticRequest(
        networkId = NETWORK_ID,
        type = type,
        target = null,
    )

    private fun context() = NetworkContext(
        networkId = NETWORK_ID,
        ssid = "HomeLab",
        bssid = null,
        gatewayIp = GATEWAY,
        localIp = "192.168.1.42",
        subnet = "192.168.1.0/24",
        interfaceName = "wlan0",
        networkType = NetworkType.WIFI,
        isMetered = false,
        isValidated = true,
        dnsServers = listOf("1.1.1.1", "8.8.8.8"),
    )

    private companion object {
        const val NETWORK_ID = "network-1"
        const val RESULT_ID = "result-1"
        const val GATEWAY = "192.168.1.1"
        const val DEFAULT_DNS_TARGET = "example.com"
        const val INTERNET_TARGET = "1.1.1.1"
        const val NOW = 1_700_000_000_000L
    }
}
