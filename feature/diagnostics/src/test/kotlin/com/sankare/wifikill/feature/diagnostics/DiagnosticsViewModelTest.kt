package com.sankare.wifikill.feature.diagnostics

import com.sankare.wifikill.core.domain.model.DiagnosticExecutionEvent
import com.sankare.wifikill.core.domain.model.DiagnosticRequest
import com.sankare.wifikill.core.domain.model.DiagnosticResult
import com.sankare.wifikill.core.domain.model.DiagnosticStatus
import com.sankare.wifikill.core.domain.model.DiagnosticType
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import com.sankare.wifikill.core.domain.model.RootOperation
import com.sankare.wifikill.core.domain.model.RootOperationResult
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.StringProvider
import com.sankare.wifikill.core.domain.usecase.ExecuteRootOperationUseCase
import com.sankare.wifikill.core.domain.usecase.GetRootAvailabilityUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveDiagnosticHistoryUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveSettingsUseCase
import com.sankare.wifikill.core.domain.usecase.RunDiagnosticsUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    private val observeCurrentNetwork = mockk<ObserveCurrentNetworkUseCase>()
    private val observeHistory = mockk<ObserveDiagnosticHistoryUseCase>()
    private val observeSettings = mockk<ObserveSettingsUseCase>()
    private val checkRootAvailability = mockk<GetRootAvailabilityUseCase>()
    private val executeRootOperation = mockk<ExecuteRootOperationUseCase>()
    private val runDiagnostics = mockk<RunDiagnosticsUseCase>()
    private val strings = StringProvider { id, _ -> "resource:$id" }

    private val viewModel by lazy {
        DiagnosticsViewModel(
            observeCurrentNetwork = observeCurrentNetwork,
            observeHistory = observeHistory,
            observeSettings = observeSettings,
            checkRootAvailability = checkRootAvailability,
            executeRootOperationUseCase = executeRootOperation,
            runDiagnostics = runDiagnostics,
            strings = strings,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { observeCurrentNetwork() } returns flowOf(network())
        every { observeHistory("network-1") } returns flowOf(emptyList())
        every { observeSettings() } returns flowOf(AppSettings())
        coEvery { checkRootAvailability() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `completed diagnostic updates latest result`() = runTest {
        every { runDiagnostics(any()) } returns flowOf(
            DiagnosticExecutionEvent.Started,
            DiagnosticExecutionEvent.Completed(result()),
        )
        viewModel.state.first { !it.isLoading }

        viewModel.runDiagnostic(DiagnosticType.GATEWAY_REACHABILITY)

        val state = viewModel.state.first { it.latestResult != null }
        assertEquals(DiagnosticType.GATEWAY_REACHABILITY, state.latestResult?.type)
        assertEquals(DiagnosticStatus.SUCCESS, state.latestResult?.status)
        assertFalse(state.isBusy)
    }

    @Test
    fun `run all executes every diagnostic type`() = runTest {
        every { runDiagnostics(any()) } returns flowOf(
            DiagnosticExecutionEvent.Started,
            DiagnosticExecutionEvent.Completed(result()),
        )
        viewModel.state.first { !it.isLoading }

        viewModel.runAllDiagnostics()

        DIAGNOSTIC_TYPES.forEach { type ->
            verify {
                runDiagnostics(
                    match {
                        it.networkId == "network-1" && it.type == type
                    },
                )
            }
        }
        assertFalse(viewModel.state.value.isRunningAll)
    }

    @Test
    fun `run without a network reports an actionable error`() = runTest {
        every { observeCurrentNetwork() } returns flowOf(null)
        viewModel.state.first { !it.isLoading }

        viewModel.runDiagnostic(DiagnosticType.DNS_RESOLUTION)

        val state = viewModel.state.first { it.error != null }
        assertNotNull(state.error)
        assertFalse(state.isBusy)
    }

    @Test
    fun `root operation exposes only the returned allowlisted output`() = runTest {
        every { observeSettings() } returns flowOf(AppSettings(rootModeEnabled = true))
        coEvery { checkRootAvailability() } returns true
        coEvery { executeRootOperation(RootOperation.INSPECT_LOCAL_INTERFACE) } returns
            AppResult.Success(
                RootOperationResult(
                    operation = RootOperation.INSPECT_LOCAL_INTERFACE,
                    output = "1: lo: <LOOPBACK>",
                    executedAt = NOW,
                ),
            )
        viewModel.state.first { it.rootModeEnabled && it.rootAvailable == true }

        viewModel.executeRootOperation(RootOperation.INSPECT_LOCAL_INTERFACE)

        val state = viewModel.state.first { it.rootResult != null }
        assertEquals("1: lo: <LOOPBACK>", state.rootResult?.output)
        assertFalse(state.rootBusy)
    }

    private fun network(): NetworkContext = NetworkContext(
        networkId = "network-1",
        ssid = "HomeLab",
        bssid = null,
        gatewayIp = "192.168.1.1",
        localIp = "192.168.1.42",
        subnet = "192.168.1.0/24",
        interfaceName = "wlan0",
        networkType = NetworkType.WIFI,
        isMetered = false,
        isValidated = true,
    )

    private fun result(): DiagnosticResult = DiagnosticResult(
        id = "result-1",
        networkId = "network-1",
        type = DiagnosticType.GATEWAY_REACHABILITY,
        target = "192.168.1.1",
        status = DiagnosticStatus.SUCCESS,
        latencyMs = 12,
        resultMessage = "Gateway responded",
        executedAt = NOW,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
