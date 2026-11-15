package com.sankare.wifikill.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sankare.wifikill.core.database.WiFiKillDatabase
import com.sankare.wifikill.core.database.entity.NetworkEntity
import com.sankare.wifikill.core.database.entity.RouterCapabilityEntity
import com.sankare.wifikill.core.database.entity.RouterEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RouterDaoTest {

    private lateinit var database: WiFiKillDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WiFiKillDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observes routers with their verified capabilities`() = runTest {
        val routerDao = database.routerDao()
        val capabilityDao = database.routerCapabilityDao()
        database.networkDao().upsert(network())
        routerDao.upsert(router())

        capabilityDao.upsertAll(
            listOf(
                capability(id = "c1", code = "ROUTER_INFORMATION"),
                capability(id = "c2", code = "DEVICE_DISCOVERY"),
            ),
        )

        val routers = routerDao.observeByNetworkWithCapabilities(NETWORK_ID).first()

        assertEquals(1, routers.size)
        val router = routers.single()
        assertEquals(8443, router.router.port)
        assertEquals("HTTPS", router.router.protocol)
        assertEquals(
            setOf("ROUTER_INFORMATION", "DEVICE_DISCOVERY"),
            router.capabilities.map { it.capabilityCode }.toSet(),
        )
    }

    @Test
    fun `updates connection info and status`() = runTest {
        val routerDao = database.routerDao()
        database.networkDao().upsert(network())
        routerDao.upsert(router())

        routerDao.updateInfo(
            routerId = ROUTER_ID,
            manufacturer = "OpenWrt",
            model = "Generic",
            firmwareVersion = "23.05",
            status = "CONNECTED",
            lastConnectedAt = 99L,
        )

        val updated = routerDao.getById(ROUTER_ID)

        assertEquals("CONNECTED", updated?.connectionStatus)
        assertEquals("OpenWrt", updated?.manufacturer)
        assertEquals("23.05", updated?.firmwareVersion)
        assertEquals(99L, updated?.lastConnectedAt)
    }

    @Test
    fun `deletes router after capabilities are removed`() = runTest {
        val routerDao = database.routerDao()
        database.networkDao().upsert(network())
        routerDao.upsert(router())
        database.routerCapabilityDao().upsertAll(
            listOf(capability(id = "c1", code = "ROUTER_INFORMATION")),
        )

        database.routerCapabilityDao().deleteByRouter(ROUTER_ID)
        routerDao.deleteById(ROUTER_ID)

        assertNull(routerDao.getById(ROUTER_ID))
    }

    private fun network() = NetworkEntity(
        id = NETWORK_ID,
        networkType = "WIFI",
        isCurrent = true,
        firstSeenAt = 1L,
        lastSeenAt = 1L,
    )

    private fun router() = RouterEntity(
        id = ROUTER_ID,
        networkId = NETWORK_ID,
        name = "Lab router",
        ipAddress = "192.168.1.1",
        port = 8443,
        protocol = "HTTPS",
        adapterType = "GENERIC",
        connectionStatus = "UNKNOWN",
        credentialId = "credential-1",
        createdAt = 1L,
    )

    private fun capability(id: String, code: String) = RouterCapabilityEntity(
        id = id,
        routerId = ROUTER_ID,
        capabilityCode = code,
        isSupported = true,
        detectedAt = 1L,
    )

    private companion object {
        const val NETWORK_ID = "network-1"
        const val ROUTER_ID = "router-1"
    }
}
