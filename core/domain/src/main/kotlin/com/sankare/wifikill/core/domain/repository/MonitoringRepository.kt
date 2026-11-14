package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.TrafficSample
import kotlinx.coroutines.flow.Flow

interface MonitoringRepository {
    fun observeTraffic(networkId: String): Flow<List<TrafficSample>>

    suspend fun refreshTraffic(networkId: String): AppResult<Unit>

    suspend fun getLatestSample(deviceId: String): TrafficSample?
}
