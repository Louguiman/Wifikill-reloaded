package com.sankare.wifikill.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sankare.wifikill.core.database.WiFiKillDatabase
import com.sankare.wifikill.core.database.entity.DeviceEntity
import com.sankare.wifikill.core.database.entity.DeviceObservationEntity
import com.sankare.wifikill.core.database.entity.NetworkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceDaoTest {

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
    fun `observes only devices present on the requested network`() = runTest {
        val networkDao = database.networkDao()
        val deviceDao = database.deviceDao()
        val observationDao = database.deviceObservationDao()

        networkDao.upsert(network(id = "network-1"))
        networkDao.upsert(network(id = "network-2"))
        deviceDao.upsert(device(id = "device-1"))
        deviceDao.upsert(device(id = "device-2"))

        observationDao.insert(observation(id = "o1", deviceId = "device-1", networkId = "network-1"))
        observationDao.insert(observation(id = "o2", deviceId = "device-2", networkId = "network-2"))

        val devices = deviceDao.observeByNetwork("network-1").first()

        assertEquals(1, devices.size)
        assertEquals("device-1", devices.single().id)
    }

    @Test
    fun `finds device by normalized mac lookup`() = runTest {
        val deviceDao = database.deviceDao()
        val observationDao = database.deviceObservationDao()
        database.networkDao().upsert(network(id = "network-1"))
        deviceDao.upsert(device(id = "device-1"))
        observationDao.insert(
            observation(
                id = "o1",
                deviceId = "device-1",
                networkId = "network-1",
                macAddress = "aa:bb:cc:dd:ee:ff",
            ),
        )

        assertEquals("device-1", deviceDao.findDeviceIdByMac("aa:bb:cc:dd:ee:ff"))
    }

    private fun network(id: String) = NetworkEntity(
        id = id,
        networkType = "WIFI",
        isCurrent = id == "network-1",
        firstSeenAt = 1L,
        lastSeenAt = 1L,
    )

    private fun device(id: String) = DeviceEntity(
        id = id,
        identityConfidence = "LOW",
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun observation(
        id: String,
        deviceId: String,
        networkId: String,
        macAddress: String? = null,
    ) = DeviceObservationEntity(
        id = id,
        deviceId = deviceId,
        networkId = networkId,
        macAddress = macAddress,
        source = "LOCAL_DISCOVERY",
        status = "RESPONDING",
        observedAt = 1L,
    )
}
