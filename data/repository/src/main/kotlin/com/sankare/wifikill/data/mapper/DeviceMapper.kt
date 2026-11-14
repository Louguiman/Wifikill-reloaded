package com.sankare.wifikill.data.mapper

import com.sankare.wifikill.core.database.entity.DeviceEntity
import com.sankare.wifikill.core.database.entity.DeviceObservationEntity
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import com.sankare.wifikill.core.domain.model.DeviceType
import com.sankare.wifikill.core.domain.model.IdentityConfidence
import com.sankare.wifikill.core.domain.model.ObservationSource
import com.sankare.wifikill.core.domain.model.ReachabilityStatus

fun DeviceEntity.toDomain(): Device = Device(
    id = id,
    customName = customName,
    hostname = hostname,
    manufacturer = manufacturer,
    deviceType = runCatching { DeviceType.valueOf(deviceType) }.getOrDefault(DeviceType.UNKNOWN),
    identityConfidence = runCatching { IdentityConfidence.valueOf(identityConfidence) }
        .getOrDefault(IdentityConfidence.LOW),
    isTrusted = isTrusted,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Device.toEntity(): DeviceEntity = DeviceEntity(
    id = id,
    customName = customName,
    hostname = hostname,
    manufacturer = manufacturer,
    deviceType = deviceType.name,
    identityConfidence = identityConfidence.name,
    isTrusted = isTrusted,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DeviceObservationEntity.toDomain(): DeviceObservation = DeviceObservation(
    id = id,
    deviceId = deviceId,
    networkId = networkId,
    scanSessionId = scanSessionId,
    ipAddress = ipAddress,
    macAddress = macAddress,
    hostname = hostname,
    manufacturer = manufacturer,
    source = runCatching { ObservationSource.valueOf(source) }
        .getOrDefault(ObservationSource.LOCAL_DISCOVERY),
    reachability = runCatching { ReachabilityStatus.valueOf(status) }
        .getOrDefault(ReachabilityStatus.UNKNOWN),
    responseTimeMs = responseTimeMs,
    observedAt = observedAt,
)

fun DeviceObservation.toEntity(): DeviceObservationEntity = DeviceObservationEntity(
    id = id,
    deviceId = requireNotNull(deviceId) { "deviceId is required to persist an observation" },
    networkId = networkId,
    scanSessionId = scanSessionId,
    ipAddress = ipAddress,
    macAddress = macAddress,
    hostname = hostname,
    manufacturer = manufacturer,
    source = source.name,
    status = reachability.name,
    responseTimeMs = responseTimeMs,
    observedAt = observedAt,
)
