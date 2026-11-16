package com.sankare.wifikill.data.mapper

import com.sankare.wifikill.core.database.entity.TrafficSampleEntity
import com.sankare.wifikill.core.domain.model.TrafficSample
import com.sankare.wifikill.core.domain.model.TrafficSource

fun TrafficSampleEntity.toDomain(): TrafficSample = TrafficSample(
    id = id,
    deviceId = deviceId,
    networkId = networkId,
    source = runCatching { TrafficSource.valueOf(source) }.getOrDefault(TrafficSource.UNKNOWN),
    bytesReceived = bytesReceived,
    bytesSent = bytesSent,
    sampledAt = sampledAt,
)

fun TrafficSample.toEntity(): TrafficSampleEntity = TrafficSampleEntity(
    id = id,
    deviceId = deviceId,
    networkId = networkId,
    source = source.name,
    bytesReceived = bytesReceived,
    bytesSent = bytesSent,
    sampledAt = sampledAt,
)
