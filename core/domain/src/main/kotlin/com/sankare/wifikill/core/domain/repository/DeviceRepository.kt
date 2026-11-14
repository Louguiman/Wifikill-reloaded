package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    fun observeDevices(networkId: String): Flow<List<Device>>

    fun observeDevice(deviceId: String): Flow<Device?>

    suspend fun getDevice(deviceId: String): Device?

    suspend fun updateCustomName(deviceId: String, name: String?): AppResult<Unit>

    suspend fun updateTrustedStatus(deviceId: String, isTrusted: Boolean): AppResult<Unit>

    suspend fun updateNotes(deviceId: String, notes: String?): AppResult<Unit>

    fun observeDeviceHistory(deviceId: String): Flow<List<DeviceObservation>>
}
