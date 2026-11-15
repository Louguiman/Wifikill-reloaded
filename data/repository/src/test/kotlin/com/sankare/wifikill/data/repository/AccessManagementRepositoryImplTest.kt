package com.sankare.wifikill.data.repository

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
import com.sankare.wifikill.core.database.entity.DeviceEntity
import com.sankare.wifikill.core.database.entity.DeviceObservationEntity
import com.sankare.wifikill.core.database.entity.RouterEntity
import com.sankare.wifikill.core.domain.model.AccessPolicyAction
import com.sankare.wifikill.core.domain.model.AccessPolicyRequest
import com.sankare.wifikill.core.domain.model.AccessPolicyStatus
import com.sankare.wifikill.core.domain.model.PolicyOperationReceipt
import com.sankare.wifikill.core.domain.model.PolicyVerificationResult
import com.sankare.wifikill.core.domain.model.RouterAccessPolicy
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterConnectionStatus
import com.sankare.wifikill.core.domain.model.RouterInfo
import com.sankare.wifikill.core.domain.model.RouterProtocol
import com.sankare.wifikill.core.domain.service.AppEventBus
import com.sankare.wifikill.integrations.router.RouterAdapter
import com.sankare.wifikill.integrations.router.RouterAdapterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccessManagementRepositoryImplTest {

    private val policyDao = mockk<AccessPolicyDao>()
    private val routerDao = mockk<RouterDao>()
    private val deviceDao = mockk<DeviceDao>()
    private val observationDao = mockk<DeviceObservationDao>()
    private val adapterRegistry = mockk<RouterAdapterRegistry>()
    private val adapter = mockk<RouterAdapter>(relaxed = true)
    private val eventBus = mockk<AppEventBus>(relaxed = true)
    private val idGenerator = mockk<IdGenerator>()
    private val timeProvider = mockk<TimeProvider>()

    private val policies = mutableMapOf<String, AccessPolicyEntity>()
    private val statuses = mutableListOf<String>()

    private val repository = AccessManagementRepositoryImpl(
        policyDao = policyDao,
        routerDao = routerDao,
        deviceDao = deviceDao,
        observationDao = observationDao,
        adapterRegistry = adapterRegistry,
        eventBus = eventBus,
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
        every { timeProvider.nowMillis() } returns NOW
        every { idGenerator.newId() } returns POLICY_ID
        every { adapterRegistry.getAdapter(RouterAdapterType.OPENWRT) } returns adapter
        coEvery { routerDao.getById(ROUTER_ID) } returns router()
        coEvery { deviceDao.getById(DEVICE_ID) } returns device()
        coEvery { observationDao.getLatestForDevice(DEVICE_ID) } returns observation()
        coEvery { adapter.connect(any()) } returns AppResult.Success(routerInfo())
        coEvery { adapter.getCapabilities() } returns POLICY_CAPABILITIES
        coEvery { adapter.getAccessPolicies() } returns AppResult.Success(emptyList())
        coEvery { policyDao.upsert(any()) } answers {
            val entity = firstArg<AccessPolicyEntity>()
            policies[entity.id] = entity
            statuses += entity.status
        }
        coEvery { policyDao.getById(any()) } answers { policies[firstArg()] }
        coEvery { policyDao.updateStatusAndReference(any(), any(), any(), any()) } answers {
            val policyId = firstArg<String>()
            val status = secondArg<String>()
            statuses += status
            policies[policyId] = policies.getValue(policyId).copy(
                status = status,
                routerPolicyReference = thirdArg(),
                updatedAt = arg(3),
            )
        }
        coEvery { eventBus.emit(any()) } just runs
    }

    @Test
    fun `applyPolicy marks ACTIVE only after read-back verification`() = runTest {
        coEvery { adapter.applyAccessPolicy(any()) } returns AppResult.Success(
            PolicyOperationReceipt("op-1", "ref-1", NOW),
        )
        coEvery { adapter.verifyAccessPolicy(any()) } returns AppResult.Success(
            PolicyVerificationResult(
                isVerified = true,
                actualAction = AccessPolicyAction.BLOCK_INTERNET,
                checkedAt = NOW,
                details = null,
            ),
        )

        val result = repository.applyPolicy(request())

        assertTrue(result is AppResult.Success)
        val policy = (result as AppResult.Success).data
        assertEquals(AccessPolicyStatus.ACTIVE, policy.status)
        assertEquals("ref-1", policy.routerPolicyReference)
        assertEquals(listOf("PENDING", "APPLYING", "APPLYING", "ACTIVE"), statuses)
        coVerify {
            adapter.applyAccessPolicy(
                match {
                    it.targetMacAddress == "aa:bb:cc:dd:ee:01" &&
                        it.targetIpAddress == "192.168.1.20"
                },
            )
        }
    }

    @Test
    fun `applyPolicy stores UNKNOWN when verification does not match`() = runTest {
        coEvery { adapter.applyAccessPolicy(any()) } returns AppResult.Success(
            PolicyOperationReceipt("op-1", "ref-1", NOW),
        )
        coEvery { adapter.verifyAccessPolicy(any()) } returns AppResult.Success(
            PolicyVerificationResult(
                isVerified = false,
                actualAction = AccessPolicyAction.ALLOW_INTERNET,
                checkedAt = NOW,
                details = null,
            ),
        )

        val result = repository.applyPolicy(request())

        assertEquals(
            AppErrorCode.POLICY_VERIFICATION_FAILED,
            (result as AppResult.Failure).error.code,
        )
        assertEquals(AccessPolicyStatus.UNKNOWN.name, policies.getValue(POLICY_ID).status)
    }

    @Test
    fun `applyPolicy rejects routers without write capability`() = runTest {
        coEvery { adapter.getCapabilities() } returns setOf(RouterCapability.ROUTER_INFORMATION)

        val result = repository.applyPolicy(request())

        assertEquals(
            AppErrorCode.UNSUPPORTED_CAPABILITY,
            (result as AppResult.Failure).error.code,
        )
        assertEquals(AccessPolicyStatus.FAILED.name, policies.getValue(POLICY_ID).status)
        coVerify(exactly = 0) { adapter.applyAccessPolicy(any()) }
    }

    @Test
    fun `applyPolicy reuses an existing verified policy instead of duplicating`() = runTest {
        coEvery { adapter.getAccessPolicies() } returns AppResult.Success(
            listOf(
                RouterAccessPolicy(
                    id = "ref-existing",
                    targetIdentifier = "192.168.1.20",
                    action = AccessPolicyAction.BLOCK_INTERNET,
                    description = null,
                ),
            ),
        )

        val result = repository.applyPolicy(request())

        assertTrue(result is AppResult.Success)
        assertEquals("ref-existing", (result as AppResult.Success).data.routerPolicyReference)
        assertEquals(AccessPolicyStatus.ACTIVE.name, policies.getValue(POLICY_ID).status)
        coVerify(exactly = 0) { adapter.applyAccessPolicy(any()) }
    }

    @Test
    fun `removePolicy marks REMOVED only after absence is verified`() = runTest {
        policies[POLICY_ID] = AccessPolicyEntity(
            id = POLICY_ID,
            routerId = ROUTER_ID,
            deviceId = DEVICE_ID,
            action = AccessPolicyAction.BLOCK_INTERNET.name,
            status = AccessPolicyStatus.ACTIVE.name,
            routerPolicyReference = "ref-1",
            createdAt = NOW,
            updatedAt = NOW,
        )
        coEvery { adapter.getCapabilities() } returns POLICY_CAPABILITIES
        coEvery { adapter.removeAccessPolicy("ref-1") } returns AppResult.Success(
            PolicyOperationReceipt("op-remove", "ref-1", NOW),
        )
        coEvery { adapter.verifyAccessPolicy(any()) } returns AppResult.Success(
            PolicyVerificationResult(
                isVerified = true,
                actualAction = AccessPolicyAction.ALLOW_INTERNET,
                checkedAt = NOW,
                details = null,
            ),
        )

        val result = repository.removePolicy(POLICY_ID)

        assertTrue(result is AppResult.Success)
        assertEquals(AccessPolicyStatus.REMOVED.name, policies.getValue(POLICY_ID).status)
        assertTrue(statuses.contains("REMOVING"))
        assertTrue(statuses.contains("REMOVED"))
    }

    private fun request(): AccessPolicyRequest = AccessPolicyRequest(
        routerId = ROUTER_ID,
        deviceId = DEVICE_ID,
        action = AccessPolicyAction.BLOCK_INTERNET,
        reason = "test",
        expiresAt = null,
    )

    private fun router(): RouterEntity = RouterEntity(
        id = ROUTER_ID,
        networkId = "network-1",
        name = "Home",
        ipAddress = "192.168.1.1",
        port = 80,
        protocol = RouterProtocol.HTTP.name,
        adapterType = RouterAdapterType.OPENWRT.name,
        connectionStatus = RouterConnectionStatus.CONNECTED.name,
        credentialId = "credential-1",
        createdAt = NOW,
    )

    private fun device(): DeviceEntity = DeviceEntity(
        id = DEVICE_ID,
        hostname = "phone",
        identityConfidence = "HIGH",
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun observation(): DeviceObservationEntity = DeviceObservationEntity(
        id = "observation-1",
        deviceId = DEVICE_ID,
        networkId = "network-1",
        ipAddress = "192.168.1.20",
        macAddress = "aa:bb:cc:dd:ee:01",
        source = "ARP",
        status = "ONLINE",
        observedAt = NOW,
    )

    private fun routerInfo(): RouterInfo = RouterInfo(
        routerId = ROUTER_ID,
        manufacturer = "OpenWrt",
        model = "OpenWrt One",
        firmwareVersion = "23.05.5",
        hostname = "router",
        managementAddress = "192.168.1.1:80",
    )

    private companion object {
        const val POLICY_ID = "policy-1"
        const val ROUTER_ID = "router-1"
        const val DEVICE_ID = "device-1"
        const val NOW = 1_700_000_000_000L
        val POLICY_CAPABILITIES = setOf(
            RouterCapability.ROUTER_INFORMATION,
            RouterCapability.ACCESS_POLICY_READ,
            RouterCapability.ACCESS_POLICY_WRITE,
        )
    }
}
