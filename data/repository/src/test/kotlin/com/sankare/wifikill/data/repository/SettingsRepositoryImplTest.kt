package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.AppSettingsDao
import com.sankare.wifikill.core.database.entity.AppSettingsEntity
import com.sankare.wifikill.core.domain.model.AppSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryImplTest {

    private val settingsDao = mockk<AppSettingsDao>()
    private val timeProvider = mockk<TimeProvider>()
    private val repository = SettingsRepositoryImpl(
        settingsDao = settingsDao,
        timeProvider = timeProvider,
        dispatchers = object : DispatchersProvider {
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val main = Dispatchers.Unconfined
        },
    )

    @Test
    fun `returns documented defaults when no settings are stored`() = runTest {
        every { settingsDao.observeAll() } returns flowOf(emptyList())
        coEvery { settingsDao.getAll() } returns emptyList()

        assertEquals(AppSettings(), repository.observeSettings().first())
        assertEquals(AppSettings(), repository.getSettings())
    }

    @Test
    fun `maps stored values and keeps missing values at defaults`() = runTest {
        every { settingsDao.observeAll() } returns flowOf(
            listOf(
                entity("onboarding_completed", "true"),
                entity("monitoring_interval_seconds", "12"),
                entity("traffic_retention_days", "14"),
                entity("root_mode_enabled", "true"),
            ),
        )

        val settings = repository.observeSettings().first()

        assertEquals(true, settings.onboardingCompleted)
        assertEquals(12, settings.monitoringRefreshIntervalSeconds)
        assertEquals(14, settings.trafficRetentionDays)
        assertEquals(true, settings.rootModeEnabled)
        assertEquals(
            AppSettings.DEFAULT_DIAGNOSTICS_RETENTION_DAYS,
            settings.diagnosticsRetentionDays,
        )
    }

    @Test
    fun `clamps invalid stored values instead of trusting them`() = runTest {
        every { settingsDao.observeAll() } returns flowOf(
            listOf(
                entity("monitoring_interval_seconds", "999"),
                entity("traffic_retention_days", "0"),
                entity("onboarding_completed", "not-a-boolean"),
            ),
        )

        val settings = repository.observeSettings().first()

        assertEquals(
            AppSettings.MAX_MONITORING_INTERVAL_SECONDS,
            settings.monitoringRefreshIntervalSeconds,
        )
        assertEquals(AppSettings.MIN_RETENTION_DAYS, settings.trafficRetentionDays)
        assertEquals(false, settings.onboardingCompleted)
    }

    @Test
    fun `update persists every setting with the same timestamp`() = runTest {
        every { timeProvider.nowMillis() } returns NOW
        coEvery { settingsDao.upsertAll(any()) } returns Unit
        val captured = slot<List<AppSettingsEntity>>()

        val result = repository.updateSettings(
            AppSettings(
                onboardingCompleted = true,
                monitoringRefreshIntervalSeconds = 10,
                rootModeEnabled = true,
            ),
        )

        assertTrue(result is AppResult.Success)
        coVerify { settingsDao.upsertAll(capture(captured)) }
        assertEquals(8, captured.captured.size)
        assertTrue(captured.captured.all { it.updatedAt == NOW })
        assertEquals(
            "10",
            captured.captured.first { it.settingKey == "monitoring_interval_seconds" }
                .settingValue,
        )
    }

    @Test
    fun `storage failure is returned as a structured error`() = runTest {
        every { timeProvider.nowMillis() } returns NOW
        coEvery { settingsDao.upsertAll(any()) } throws IllegalStateException("disk full")

        val result = repository.updateSettings(AppSettings())

        assertEquals(
            AppErrorCode.STORAGE_ERROR,
            (result as AppResult.Failure).error.code,
        )
    }

    private fun entity(key: String, value: String) = AppSettingsEntity(
        settingKey = key,
        settingValue = value,
        updatedAt = NOW,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
