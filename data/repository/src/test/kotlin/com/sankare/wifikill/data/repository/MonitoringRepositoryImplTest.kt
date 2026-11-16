package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.NetworkDao
import com.sankare.wifikill.core.database.dao.TrafficSampleDao
import com.sankare.wifikill.core.database.entity.TrafficSampleEntity
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType
import com.sankare.wifikill.core.domain.model.TrafficSource
import com.sankare.wifikill.core.domain.repository.SettingsRepository
import com.sankare.wifikill.core.network.NetworkContextProvider
import com.sankare.wifikill.core.network.traffic.LocalTrafficCounters
import com.sankare.wifikill.core.network.traffic.LocalTrafficReader
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MonitoringRepositoryImplTest {

    private val trafficSampleDao = mockk<TrafficSampleDao>()
    private val networkDao = mockk<NetworkDao>()
    private val networkContextProvider = mockk<NetworkContextProvider>()
    private val trafficReader = mockk<LocalTrafficReader>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val timeProvider = mockk<TimeProvider>()
    private val idGenerator = mockk<IdGenerator>()
    private val inserted = mutableListOf<TrafficSampleEntity>()

    private val repository = MonitoringRepositoryImpl(
        trafficSampleDao = trafficSampleDao,
        networkDao = networkDao,
        networkContextProvider = networkContextProvider,
        trafficReader = trafficReader,
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
        coEvery { trafficSampleDao.insertAll(any()) } answers {
            inserted += firstArg<List<TrafficSampleEntity>>()
        }
        coEvery { trafficSampleDao.deleteOlderThan(any()) } returns 0
        coEvery { settingsRepository.getSettings() } returns AppSettings()
        every { timeProvider.nowMillis() } returns NOW
        every { idGenerator.newId() } returns SAMPLE_ID
    }

    @Test
    fun `refresh stores a labeled local-device sample without inventing a device`() = runTest {
        every { trafficReader.read() } returns LocalTrafficCounters(
            bytesReceived = 4_096,
            bytesSent = 1_024,
        )

        val result = repository.refreshTraffic(NETWORK_ID)

        assertTrue(result is AppResult.Success)
        assertEquals(1, inserted.size)
        val sample = inserted.single()
        assertEquals(NETWORK_ID, sample.networkId)
        assertNull(sample.deviceId)
        assertEquals(TrafficSource.ANDROID_LOCAL.name, sample.source)
        assertEquals(4_096L, sample.bytesReceived)
        assertEquals(1_024L, sample.bytesSent)
        assertEquals(NOW, sample.sampledAt)
    }

    @Test
    fun `missing counter stays null instead of becoming zero`() = runTest {
        every { trafficReader.read() } returns LocalTrafficCounters(
            bytesReceived = 2_048,
            bytesSent = null,
        )

        val result = repository.refreshTraffic(NETWORK_ID)

        assertTrue(result is AppResult.Success)
        assertEquals(2_048L, inserted.single().bytesReceived)
        assertNull(inserted.single().bytesSent)
    }

    @Test
    fun `unsupported counters fail without storing a sample`() = runTest {
        every { trafficReader.read() } returns LocalTrafficCounters(
            bytesReceived = null,
            bytesSent = null,
        )

        val result = repository.refreshTraffic(NETWORK_ID)

        assertEquals(
            AppErrorCode.UNSUPPORTED_CAPABILITY,
            (result as AppResult.Failure).error.code,
        )
        coVerify(exactly = 0) { trafficSampleDao.insertAll(any()) }
    }

    @Test
    fun `changed network context fails without storing a sample`() = runTest {
        every { trafficReader.read() } returns LocalTrafficCounters(1, 1)

        val result = repository.refreshTraffic("other-network")

        assertEquals(
            AppErrorCode.INVALID_NETWORK_CONTEXT,
            (result as AppResult.Failure).error.code,
        )
        coVerify(exactly = 0) { trafficSampleDao.insertAll(any()) }
    }

    @Test
    fun `observeTraffic maps persisted samples with source labels`() = runTest {
        coEvery { trafficSampleDao.observeByNetwork(NETWORK_ID) } returns flowOf(
            listOf(
                TrafficSampleEntity(
                    id = SAMPLE_ID,
                    deviceId = null,
                    networkId = NETWORK_ID,
                    source = TrafficSource.ANDROID_LOCAL.name,
                    bytesReceived = 10,
                    bytesSent = 5,
                    sampledAt = NOW,
                ),
            ),
        )

        val samples = repository.observeTraffic(NETWORK_ID).toList().first()

        assertEquals(1, samples.size)
        assertEquals(TrafficSource.ANDROID_LOCAL, samples.single().source)
        assertNull(samples.single().deviceId)
    }

    private fun context() = NetworkContext(
        networkId = NETWORK_ID,
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

    private companion object {
        const val NETWORK_ID = "network-1"
        const val SAMPLE_ID = "sample-1"
        const val NOW = 1_700_000_000_000L
    }
}
