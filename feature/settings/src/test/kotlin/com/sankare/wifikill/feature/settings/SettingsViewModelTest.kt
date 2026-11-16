package com.sankare.wifikill.feature.settings

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.model.RetentionKind
import com.sankare.wifikill.core.domain.usecase.DeleteLocalDataUseCase
import com.sankare.wifikill.core.domain.usecase.GetRootAvailabilityUseCase
import com.sankare.wifikill.core.domain.usecase.ObserveSettingsUseCase
import com.sankare.wifikill.core.domain.usecase.SetRootModeEnabledUseCase
import com.sankare.wifikill.core.domain.usecase.UpdateMonitoringIntervalUseCase
import com.sankare.wifikill.core.domain.usecase.UpdateRetentionUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val observeSettings = mockk<ObserveSettingsUseCase>()
    private val checkRootAvailability = mockk<GetRootAvailabilityUseCase>()
    private val setRootMode = mockk<SetRootModeEnabledUseCase>()
    private val updateMonitoringInterval = mockk<UpdateMonitoringIntervalUseCase>()
    private val updateRetention = mockk<UpdateRetentionUseCase>()
    private val deleteLocalData = mockk<DeleteLocalDataUseCase>()

    private val viewModel by lazy {
        SettingsViewModel(
            observeSettings = observeSettings,
            checkRootAvailability = checkRootAvailability,
            setRootMode = setRootMode,
            updateMonitoringInterval = updateMonitoringInterval,
            updateRetention = updateRetention,
            deleteLocalDataUseCase = deleteLocalData,
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { observeSettings() } returns flowOf(AppSettings())
        coEvery { checkRootAvailability() } returns false
        coEvery { setRootMode(any()) } returns AppResult.Success(Unit)
        coEvery { updateMonitoringInterval(any()) } returns AppResult.Success(Unit)
        coEvery { updateRetention(any(), any()) } returns AppResult.Success(Unit)
        coEvery { deleteLocalData() } returns AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads settings and reports root availability`() = runTest {
        every { observeSettings() } returns flowOf(
            AppSettings(monitoringRefreshIntervalSeconds = 15, rootModeEnabled = true),
        )
        coEvery { checkRootAvailability() } returns true

        val state = viewModel.state.first {
            !it.isLoading && it.rootAvailable != null
        }

        assertEquals(15, state.settings.monitoringRefreshIntervalSeconds)
        assertEquals(true, state.settings.rootModeEnabled)
        assertEquals(true, state.rootAvailable)
    }

    @Test
    fun `updates monitoring interval through the use case`() = runTest {
        viewModel.state.first { !it.isLoading }

        viewModel.setMonitoringInterval(20)

        coVerify { updateMonitoringInterval(20) }
    }

    @Test
    fun `updates retention through the use case`() = runTest {
        viewModel.state.first { !it.isLoading }

        viewModel.setRetention(RetentionKind.TRAFFIC, 14)

        coVerify { updateRetention(RetentionKind.TRAFFIC, 14) }
    }

    @Test
    fun `refuses to enable root mode when root is unavailable`() = runTest {
        viewModel.state.first { !it.isLoading && it.rootAvailable != null }

        viewModel.setRootModeEnabled(true)

        coVerify(exactly = 0) { setRootMode(true) }
        val state = viewModel.state.first { it.error != null }
        assertNotNull(state.error)
    }

    @Test
    fun `deletes local data and reports success`() = runTest {
        viewModel.state.first { !it.isLoading }

        viewModel.deleteLocalData()

        coVerify { deleteLocalData() }
        val state = viewModel.state.first { it.message != null }
        assertEquals("LOCAL_DATA_DELETED", state.message)
    }

    @Test
    fun `delete failure surfaces a sanitized error`() = runTest {
        coEvery { deleteLocalData() } returns AppResult.Failure(
            AppError(AppErrorCode.STORAGE_ERROR, "Unable to delete local data"),
        )
        viewModel.state.first { !it.isLoading }

        viewModel.deleteLocalData()

        val state = viewModel.state.first { it.error != null }
        assertEquals("Unable to delete local data", state.error)
    }
}
