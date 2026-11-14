package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.ScanEvent
import com.sankare.wifikill.core.domain.model.ScanRequest
import com.sankare.wifikill.core.domain.model.ScanSession
import kotlinx.coroutines.flow.Flow

interface NetworkDiscoveryRepository {
    fun observeCurrentNetwork(): Flow<NetworkContext?>

    fun observeScanEvents(sessionId: String): Flow<ScanEvent>

    suspend fun startScan(request: ScanRequest): AppResult<String>

    suspend fun cancelScan(sessionId: String): AppResult<Unit>

    suspend fun getScan(sessionId: String): ScanSession?

    fun observeDevices(networkId: String): Flow<List<Device>>
}
