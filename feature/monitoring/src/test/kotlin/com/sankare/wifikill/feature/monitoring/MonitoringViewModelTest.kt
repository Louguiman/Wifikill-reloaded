package com.sankare.wifikill.feature.monitoring

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.StringProvider
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import com.sankare.wifikill.core.domain.model.TrafficSample
import com.sankare.wifikill.core.domain.model.TrafficSource
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveSettingsUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveTrafficUseCase
import com.sankare.wifikill.core.domain.usecase.RefreshTrafficUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringViewModelTest {

    private val observeCurrentNetwork = mockk<ObserveCurrentNetworkUseCase>()
    private val observeTraffic = mockk<ObserveTrafficUseCase>()
    private val observeSettings = mockk<ObserveSettingsUseCase>()
    private val refreshTraffic = mockk<RefreshTrafficUseCase>()
    private val strings = StringProvider { id, _ -> "resource:$id" }

    private val viewModel by lazy {
        MonitoringViewModel(
            observeCurrentNetwork = observeCurrentNetwork,
            observeTraffic = observeTraffic,
            observeSettings = observeSettings,
            refreshTraffic = refreshTraffic,
            strings = strings,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { observeCurrentNetwork() } returns flowOf(network())
        every { observeTraffic("network-1") } returns flowOf(listOf(sample()))
        every { observeSettings() } returns flowOf(AppSettings())
        coEvery { refreshTraffic("network-1") } returns AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state exposes latest source-aware sample`() = runTest {
        val state = viewModel.state.first { !it.isLoading && it.samples.isNotEmpty() }

        assertEquals("sample-1", state.latestSample?.id)
        assertEquals(TrafficSource.ANDROID_LOCAL, state.latestSample?.source)
        assertNull(state.latestSample?.deviceId)
        assertEquals(4_096L, state.latestSample?.bytesReceived)
    }

    @Test
    fun `starting monitoring refreshes and pausing stops the loop`() = runTest {
        viewModel.state.first { !it.isLoading }

        viewModel.startMonitoring()
        coVerify { refreshTraffic("network-1") }
        assertTrue(viewModel.state.value.isMonitoring)

        viewModel.pauseMonitoring()

        assertFalse(viewModel.state.value.isMonitoring)
    }

    @Test
    fun `unsupported traffic statistics surface unavailable without samples`() = runTest {
        every { observeTraffic("network-1") } returns flowOf(emptyList())
        coEvery { refreshTraffic("network-1") } returns AppResult.Failure(
            AppError(
                AppErrorCode.UNSUPPORTED_CAPABILITY,
                "Traffic statistics are unavailable on this device",
            ),
        )
        viewModel.state.first { !it.isLoading }

        viewModel.refreshNow()

        val state = viewModel.state.first { it.unavailableReason != null }
        assertTrue(state.samples.isEmpty())
        assertNull(state.latestSample)
        assertNull(state.error)
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

    private fun sample(): TrafficSample = TrafficSample(
        id = "sample-1",
        deviceId = null,
        networkId = "network-1",
        source = TrafficSource.ANDROID_LOCAL,
        bytesReceived = 4_096,
        bytesSent = 1_024,
        sampledAt = NOW,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
