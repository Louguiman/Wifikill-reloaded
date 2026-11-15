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
import com.sankare.wifikill.core.database.entity.RouterEntity
import com.sankare.wifikill.core.database.relation.RouterWithCapabilities
import com.sankare.wifikill.core.domain.model.AddRouterRequest
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterConnectionStatus
import com.sankare.wifikill.core.domain.model.RouterInfo
import com.sankare.wifikill.core.domain.model.RouterProtocol
import com.sankare.wifikill.core.domain.repository.CredentialProvider
import com.sankare.wifikill.core.domain.repository.RouterCredential
import com.sankare.wifikill.core.domain.service.AppEvent
import com.sankare.wifikill.core.domain.service.AppEventBus
import com.sankare.wifikill.core.network.NetworkContextProvider
import com.sankare.wifikill.integrations.router.RouterAdapter
import com.sankare.wifikill.integrations.router.RouterAdapterRegistry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterRepositoryImplTest {

    private val routerDao: RouterDao = mockk(relaxed = true)
    private val capabilityDao: RouterCapabilityDao = mockk(relaxed = true)
    private val networkDao: NetworkDao = mockk(relaxed = true)
    private val networkContextProvider: NetworkContextProvider = mockk()
    private val adapterRegistry: RouterAdapterRegistry = mockk()
    private val credentialProvider: CredentialProvider = mockk()
    private val eventBus: AppEventBus = mockk(relaxed = true)
    private val adapter: RouterAdapter = mockk(relaxed = true)

    private val network = NetworkContext(
        networkId = NETWORK_ID,
        ssid = "Lab",
        bssid = null,
        gatewayIp = "192.168.1.1",
        localIp = "192.168.1.10",
        subnet = "192.168.1.0/24",
        interfaceName = "wlan0",
        networkType = NetworkType.WIFI,
        isMetered = false,
        isValidated = true,
    )

    private fun repository(scheduler: TestCoroutineScheduler): RouterRepositoryImpl {
        every { adapterRegistry.getAdapter(RouterAdapterType.GENERIC) } returns adapter
        return RouterRepositoryImpl(
            routerDao = routerDao,
            capabilityDao = capabilityDao,
            networkDao = networkDao,
            networkContextProvider = networkContextProvider,
            adapterRegistry = adapterRegistry,
            credentialProvider = credentialProvider,
            eventBus = eventBus,
            timeProvider = object : TimeProvider {
                override fun nowMillis(): Long = NOW
            },
            idGenerator = object : IdGenerator {
                override fun newId(): String = ROUTER_ID
            },
            dispatchers = object : DispatchersProvider {
                override val io = StandardTestDispatcher(scheduler)
                override val default = StandardTestDispatcher(scheduler)
                override val main = StandardTestDispatcher(scheduler)
            },
        )
    }

    @Test
    fun `connect persists verified capabilities and status`() = runTest {
        coEvery { routerDao.getById(ROUTER_ID) } returns router()
        coEvery { adapter.connect(any()) } returns AppResult.Success(routerInfo())
        coEvery { adapter.getCapabilities() } returns setOf(RouterCapability.ROUTER_INFORMATION)
        val repository = repository(testScheduler)

        val result = repository.connectRouter(ROUTER_ID)

        assertTrue(result is AppResult.Success)
        coVerify {
            routerDao.updateInfo(
                ROUTER_ID,
                null,
                null,
                null,
                RouterConnectionStatus.CONNECTED.name,
                NOW,
            )
        }
        coVerify {
            capabilityDao.upsertAll(
                match { capabilities ->
                    capabilities.single().capabilityCode == RouterCapability.ROUTER_INFORMATION.name
                },
            )
        }
        coVerify {
            eventBus.emit(match { it is AppEvent.RouterConnectionChanged && it.connected })
        }
    }

    @Test
    fun `connect marks authentication failure`() = runTest {
        coEvery { routerDao.getById(ROUTER_ID) } returns router()
        coEvery { adapter.connect(any()) } returns AppResult.Failure(
            AppError(AppErrorCode.AUTHENTICATION_FAILED),
        )
        val repository = repository(testScheduler)

        val result = repository.connectRouter(ROUTER_ID)

        assertEquals(AppErrorCode.AUTHENTICATION_FAILED, (result as AppResult.Failure).error.code)
        coVerify {
            routerDao.updateStatus(
                ROUTER_ID,
                RouterConnectionStatus.AUTHENTICATION_FAILED.name,
                null,
            )
        }
    }

    @Test
    fun `connect marks unreachable after timeout`() = runTest {
        coEvery { routerDao.getById(ROUTER_ID) } returns router()
        coEvery { adapter.connect(any()) } coAnswers {
            delay(60_000)
            AppResult.Success(routerInfo())
        }
        val repository = repository(testScheduler)

        val result = repository.connectRouter(ROUTER_ID)

        assertEquals(AppErrorCode.ROUTER_UNREACHABLE, (result as AppResult.Failure).error.code)
        coVerify {
            routerDao.updateStatus(ROUTER_ID, RouterConnectionStatus.UNREACHABLE.name, null)
        }
    }

    @Test
    fun `add router stores credential and router then connects`() = runTest {
        coEvery { networkContextProvider.observeCurrentNetwork() } returns flowOf(network)
        coEvery { networkDao.getById(NETWORK_ID) } returns null
        coEvery { credentialProvider.save(any()) } returns AppResult.Success(CREDENTIAL_ID)
        coEvery { routerDao.getById(ROUTER_ID) } returns router()
        coEvery { adapter.connect(any()) } returns AppResult.Success(routerInfo())
        coEvery { adapter.getCapabilities() } returns emptySet()
        coEvery {
            routerDao.getWithCapabilities(ROUTER_ID)
        } returns RouterWithCapabilities(router(), emptyList())
        val repository = repository(testScheduler)

        val result = repository.addRouter(
            AddRouterRequest(
                networkId = NETWORK_ID,
                name = "Lab router",
                host = "192.168.1.1",
                port = 443,
                protocol = RouterProtocol.HTTPS,
                adapterType = RouterAdapterType.GENERIC,
                username = "admin",
                password = "secret",
            ),
        )

        assertTrue(result is AppResult.Success)
        coVerify { credentialProvider.save(RouterCredential("admin", "secret")) }
        coVerify { networkDao.markCurrent(any()) }
        coVerify {
            routerDao.upsert(
                match { it.id == ROUTER_ID && it.credentialId == CREDENTIAL_ID },
            )
        }
    }

    @Test
    fun `add router rejects mismatched network context`() = runTest {
        coEvery { networkContextProvider.observeCurrentNetwork() } returns flowOf(network)
        val repository = repository(testScheduler)

        val result = repository.addRouter(
            AddRouterRequest(
                networkId = "other-network",
                name = "Lab router",
                host = "192.168.1.1",
                port = 443,
                protocol = RouterProtocol.HTTPS,
                adapterType = RouterAdapterType.GENERIC,
                username = "admin",
                password = "secret",
            ),
        )

        assertEquals(AppErrorCode.INVALID_NETWORK_CONTEXT, (result as AppResult.Failure).error.code)
        coVerify(exactly = 0) { credentialProvider.save(any()) }
    }

    @Test
    fun `remove router deletes credential and rows`() = runTest {
        coEvery { routerDao.getById(ROUTER_ID) } returns router()
        coEvery { credentialProvider.delete(CREDENTIAL_ID) } returns AppResult.Success(Unit)
        coEvery { adapter.disconnect() } just Runs
        val repository = repository(testScheduler)

        val result = repository.removeRouter(ROUTER_ID)

        assertTrue(result is AppResult.Success)
        coVerify { credentialProvider.delete(CREDENTIAL_ID) }
        coVerify { capabilityDao.deleteByRouter(ROUTER_ID) }
        coVerify { routerDao.deleteById(ROUTER_ID) }
    }

    private fun router(): RouterEntity = RouterEntity(
        id = ROUTER_ID,
        networkId = NETWORK_ID,
        name = "Lab router",
        ipAddress = "192.168.1.1",
        port = 443,
        protocol = RouterProtocol.HTTPS.name,
        adapterType = RouterAdapterType.GENERIC.name,
        connectionStatus = RouterConnectionStatus.UNKNOWN.name,
        credentialId = CREDENTIAL_ID,
        createdAt = NOW,
    )

    private fun routerInfo(): RouterInfo = RouterInfo(
        routerId = ROUTER_ID,
        manufacturer = null,
        model = null,
        firmwareVersion = null,
        hostname = null,
        managementAddress = "192.168.1.1:443",
    )

    private companion object {
        const val NETWORK_ID = "network-1"
        const val ROUTER_ID = "router-1"
        const val CREDENTIAL_ID = "credential-1"
        const val NOW = 1_000L
    }
}
