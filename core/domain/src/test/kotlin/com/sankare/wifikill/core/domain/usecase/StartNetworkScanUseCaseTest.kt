package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.ObservationSource
import com.sankare.wifikill.core.domain.model.ScanRequest
import com.sankare.wifikill.core.domain.repository.NetworkDiscoveryRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartNetworkScanUseCaseTest {

    private val repository = mockk<NetworkDiscoveryRepository>(relaxed = true)
    private val useCase = StartNetworkScanUseCase(repository)

    @Test
    fun `blank network id returns invalid network context`() = runTest {
        val result = useCase(
            ScanRequest(
                networkId = "  ",
                sources = setOf(ObservationSource.LOCAL_DISCOVERY),
            ),
        )

        assertTrue(result is AppResult.Failure)
        assertEquals(
            AppErrorCode.INVALID_NETWORK_CONTEXT,
            (result as AppResult.Failure).error.code,
        )
    }
}
