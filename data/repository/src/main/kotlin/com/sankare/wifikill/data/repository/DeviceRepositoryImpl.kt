package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.DeviceDao
import com.sankare.wifikill.core.database.dao.DeviceObservationDao
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import com.sankare.wifikill.core.domain.repository.DeviceRepository
import com.sankare.wifikill.data.mapper.toDomain
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeviceRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao,
    private val observationDao: DeviceObservationDao,
    private val timeProvider: TimeProvider,
) : DeviceRepository {

    override fun observeDevices(networkId: String): Flow<List<Device>> =
        deviceDao.observeByNetwork(networkId).map { devices -> devices.map { it.toDomain() } }

    override fun observeDevice(deviceId: String): Flow<Device?> =
        deviceDao.observeById(deviceId).map { it?.toDomain() }

    override suspend fun getDevice(deviceId: String): Device? =
        deviceDao.getById(deviceId)?.toDomain()

    override suspend fun updateCustomName(deviceId: String, name: String?): AppResult<Unit> =
        updateDevice(deviceId) { now -> deviceDao.updateCustomName(deviceId, name, now) }

    override suspend fun updateTrustedStatus(deviceId: String, isTrusted: Boolean): AppResult<Unit> =
        updateDevice(deviceId) { now -> deviceDao.updateTrusted(deviceId, isTrusted, now) }

    override suspend fun updateNotes(deviceId: String, notes: String?): AppResult<Unit> =
        updateDevice(deviceId) { now -> deviceDao.updateNotes(deviceId, notes, now) }

    override fun observeDeviceHistory(deviceId: String): Flow<List<DeviceObservation>> =
        observationDao.observeHistory(deviceId).map { observations ->
            observations.map { it.toDomain() }
        }

    private suspend fun updateDevice(
        deviceId: String,
        update: suspend (updatedAt: Long) -> Unit,
    ): AppResult<Unit> {
        deviceDao.getById(deviceId)
            ?: return AppResult.Failure(AppError(AppErrorCode.UNKNOWN, "Device not found"))
        update(timeProvider.nowMillis())
        return AppResult.Success(Unit)
    }
}
