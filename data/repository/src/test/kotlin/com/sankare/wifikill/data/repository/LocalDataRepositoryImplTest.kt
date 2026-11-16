package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.AccessPolicyDao
import com.sankare.wifikill.core.database.dao.DeviceDao
import com.sankare.wifikill.core.database.dao.DeviceObservationDao
import com.sankare.wifikill.core.database.dao.DiagnosticResultDao
import com.sankare.wifikill.core.database.dao.NetworkDao
import com.sankare.wifikill.core.database.dao.RouterCapabilityDao
import com.sankare.wifikill.core.database.dao.RouterDao
import com.sankare.wifikill.core.database.dao.ScanSessionDao
import com.sankare.wifikill.core.database.dao.TrafficSampleDao
import com.sankare.wifikill.core.database.entity.RouterEntity
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.repository.CredentialProvider
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
import org.junit.Test

class LocalDataRepositoryImplTest {

    private val accessPolicyDao = mockk<AccessPolicyDao>()
    private val trafficSampleDao = mockk<TrafficSampleDao>()
    private val observationDao = mockk<DeviceObservationDao>()
    private val diagnosticResultDao = mockk<DiagnosticResultDao>()
    private val scanSessionDao = mockk<ScanSessionDao>()
    private val routerCapabilityDao = mockk<RouterCapabilityDao>()
    private val deviceDao = mockk<DeviceDao>()
    private val routerDao = mockk<RouterDao>()
    private val networkDao = mockk<NetworkDao>()
    private val credentialProvider = mockk<CredentialProvider>()
    private val timeProvider = mockk<TimeProvider>()

    private val repository = LocalDataRepositoryImpl(
        accessPolicyDao = accessPolicyDao,
        trafficSampleDao = trafficSampleDao,
        observationDao = observationDao,
        diagnosticResultDao = diagnosticResultDao,
        scanSessionDao = scanSessionDao,
        routerCapabilityDao = routerCapabilityDao,
        deviceDao = deviceDao,
        routerDao = routerDao,
        networkDao = networkDao,
        credentialProvider = credentialProvider,
        timeProvider = timeProvider,
        dispatchers = object : DispatchersProvider {
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val main = Dispatchers.Unconfined
        },
    )

    @Test
    fun `deletes credentials before relational history without touching settings`() = runTest {
        coEvery { routerDao.getAll() } returns listOf(
            router("router-1", "credential-1"),
            router("router-2", null),
        )
        coEvery { credentialProvider.delete("credential-1") } returns AppResult.Success(Unit)
        stubDeletes()

        val result = repository.deleteAllLocalData()

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { credentialProvider.delete("credential-1") }
        coVerify(exactly = 1) {
            accessPolicyDao.deleteAll()
            trafficSampleDao.deleteAll()
            observationDao.deleteAll()
            diagnosticResultDao.deleteAll()
            scanSessionDao.deleteAll()
            routerCapabilityDao.deleteAll()
            deviceDao.deleteAll()
            routerDao.deleteAll()
            networkDao.deleteAll()
        }
    }

    @Test
    fun `credential deletion failure is reported after local history is removed`() = runTest {
        coEvery { routerDao.getAll() } returns listOf(router("router-1", "credential-1"))
        coEvery { credentialProvider.delete("credential-1") } returns AppResult.Failure(
            com.sankare.wifikill.core.common.AppError(
                AppErrorCode.STORAGE_ERROR,
                "not found",
            ),
        )
        stubDeletes()

        val result = repository.deleteAllLocalData()

        assertEquals(
            AppErrorCode.STORAGE_ERROR,
            (result as AppResult.Failure).error.code,
        )
        coVerify { networkDao.deleteAll() }
    }

    @Test
    fun `database failure is returned as a structured error`() = runTest {
        coEvery { routerDao.getAll() } returns emptyList()
        coEvery { accessPolicyDao.deleteAll() } throws IllegalStateException("locked")

        val result = repository.deleteAllLocalData()

        assertEquals(
            AppErrorCode.STORAGE_ERROR,
            (result as AppResult.Failure).error.code,
        )
    }

    @Test
    fun `expired cleanup uses configured retention windows`() = runTest {
        every { timeProvider.nowMillis() } returns NOW
        coEvery { scanSessionDao.deleteOlderThan(any()) } returns 0
        coEvery { observationDao.deleteOlderThan(any()) } returns 0
        coEvery { trafficSampleDao.deleteOlderThan(any()) } returns 0
        coEvery { diagnosticResultDao.deleteOlderThan(any()) } returns 0

        val result = repository.deleteExpiredData(
            AppSettings(
                scanHistoryRetentionDays = 10,
                deviceObservationRetentionDays = 20,
                trafficRetentionDays = 7,
                diagnosticsRetentionDays = 30,
            ),
        )

        assertTrue(result is AppResult.Success)
        coVerify { scanSessionDao.deleteOlderThan(NOW - 10L * DAY_MS) }
        coVerify { observationDao.deleteOlderThan(NOW - 20L * DAY_MS) }
        coVerify { trafficSampleDao.deleteOlderThan(NOW - 7L * DAY_MS) }
        coVerify { diagnosticResultDao.deleteOlderThan(NOW - 30L * DAY_MS) }
    }

    @Test
    fun `expired cleanup failure is structured`() = runTest {
        every { timeProvider.nowMillis() } returns NOW
        coEvery { scanSessionDao.deleteOlderThan(any()) } throws IllegalStateException("locked")

        val result = repository.deleteExpiredData(AppSettings())

        assertEquals(
            AppErrorCode.STORAGE_ERROR,
            (result as AppResult.Failure).error.code,
        )
    }

    private fun stubDeletes() {
        coEvery { accessPolicyDao.deleteAll() } just runs
        coEvery { trafficSampleDao.deleteAll() } just runs
        coEvery { observationDao.deleteAll() } just runs
        coEvery { diagnosticResultDao.deleteAll() } just runs
        coEvery { scanSessionDao.deleteAll() } just runs
        coEvery { routerCapabilityDao.deleteAll() } just runs
        coEvery { deviceDao.deleteAll() } just runs
        coEvery { routerDao.deleteAll() } just runs
        coEvery { networkDao.deleteAll() } just runs
    }

    private fun router(id: String, credentialId: String?) = RouterEntity(
        id = id,
        networkId = "network-1",
        name = "Router",
        ipAddress = "192.168.1.1",
        adapterType = "OPENWRT",
        connectionStatus = "CONNECTED",
        credentialId = credentialId,
        createdAt = 0,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val DAY_MS = 24L * 60 * 60 * 1_000
    }
}
