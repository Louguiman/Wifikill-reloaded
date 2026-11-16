package com.sankare.wifikill.feature.settings

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.usecase.CompleteOnboardingUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val completeOnboarding = mockk<CompleteOnboardingUseCase>()

    private val viewModel by lazy {
        OnboardingViewModel(completeOnboarding)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { completeOnboarding() } returns AppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `completing onboarding persists and emits completion`() = runTest {
        viewModel.complete()

        coVerify { completeOnboarding() }
        assertTrue(viewModel.state.first { it.completed }.completed)
    }

    @Test
    fun `failure stays on onboarding with an error`() = runTest {
        coEvery { completeOnboarding() } returns AppResult.Failure(
            AppError(AppErrorCode.STORAGE_ERROR, "Unable to save"),
        )

        viewModel.complete()

        val state = viewModel.state.first { it.error != null }
        assertEquals("Unable to save", state.error)
        assertEquals(false, state.completed)
        assertNotNull(state.error)
    }
}
