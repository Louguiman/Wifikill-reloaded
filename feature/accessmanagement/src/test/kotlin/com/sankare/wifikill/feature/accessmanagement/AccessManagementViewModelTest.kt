package com.sankare.wifikill.feature.accessmanagement

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AccessPolicy
import com.sankare.wifikill.core.domain.model.AccessPolicyAction
import com.sankare.wifikill.core.domain.model.AccessPolicyStatus
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceType
import com.sankare.wifikill.core.domain.model.IdentityConfidence
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import com.sankare.wifikill.core.domain.model.Router
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.model.RouterConnectionStatus
import com.sankare.wifikill.core.domain.model.RouterProtocol
import com.sankare.wifikill.core.domain.usecase.ApplyAccessPolicyUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveAccessPoliciesUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveCurrentNetworkUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveDevicesUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveRoutersUseCase
import com.sankare.wifikill.core.domain.usecase.RemoveAccessPolicyUseCase
import com.sankare.wifikill.core.domain.usecase.VerifyAccessPolicyUseCase
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessManagementViewModelTest {

    private val observeCurrentNetwork = mockk<ObserveCurrentNetworkUseCase>()
    private val observeRouters = mockk<ObserveRoutersUseCase>()
    private val observeDevices = mockk<ObserveDevicesUseCase>()
    private val observePolicies = mockk<ObserveAccessPoliciesUseCase>()
    private val applyPolicy = mockk<ApplyAccessPolicyUseCase>()
    private val removePolicy = mockk<RemoveAccessPolicyUseCase>()
    private val verifyPolicy = mockk<VerifyAccessPolicyUseCase>()

    private val viewModel by lazy {
        AccessManagementViewModel(
            observeCurrentNetwork = observeCurrentNetwork,
            observeRouters = observeRouters,
            observeDevices = observeDevices,
            observePolicies = observePolicies,
            applyPolicy = applyPolicy,
            removePolicy = removePolicy,
            verifyPolicy = verifyPolicy,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { observeCurrentNetwork() } returns flowOf(network())
        every { observeRouters("network-1") } returns flowOf(listOf(router()))
        every { observeDevices("network-1") } returns flowOf(listOf(device()))
        every { observePolicies("router-1") } returns flowOf(listOf(policy()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state derives defaults for router device and policies`() = runTest {
        val state = viewModel.state.first { !it.isLoading }

        assertEquals("router-1", state.selectedRouterId)
        assertEquals("device-1", state.selectedDeviceId)
        assertEquals(1, state.policies.size)
    }

    @Test
    fun `apply sends the selected router and device`() = runTest {
        viewModel.state.first { !it.isLoading }
        coEvery { applyPolicy(any()) } returns AppResult.Success(policy())

        viewModel.apply()

        coVerify {
            applyPolicy(
                match {
                    it.routerId == "router-1" &&
                        it.deviceId == "device-1" &&
                        it.action == AccessPolicyAction.BLOCK_INTERNET
                },
            )
        }
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

    private fun router(): Router = Router(
        id = "router-1",
        networkId = "network-1",
        name = "Home",
        host = "192.168.1.1",
        port = 80,
        protocol = RouterProtocol.HTTP,
        adapterType = RouterAdapterType.OPENWRT,
        credentialId = "credential-1",
        manufacturer = "OpenWrt",
        model = "OpenWrt One",
        firmwareVersion = "23.05.5",
        connectionStatus = RouterConnectionStatus.CONNECTED,
        capabilities = emptySet(),
        lastConnectedAt = NOW,
        createdAt = NOW,
    )

    private fun device(): Device = Device(
        id = "device-1",
        customName = "Phone",
        hostname = "phone",
        manufacturer = null,
        deviceType = DeviceType.PHONE,
        identityConfidence = IdentityConfidence.HIGH,
        isTrusted = false,
        notes = null,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun policy(): AccessPolicy = AccessPolicy(
        id = "policy-1",
        routerId = "router-1",
        deviceId = "device-1",
        action = AccessPolicyAction.BLOCK_INTERNET,
        status = AccessPolicyStatus.ACTIVE,
        routerPolicyReference = "ref-1",
        reason = null,
        createdAt = NOW,
        updatedAt = NOW,
        expiresAt = null,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
