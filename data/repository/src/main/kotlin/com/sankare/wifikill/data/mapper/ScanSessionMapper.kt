package com.sankare.wifikill.data.mapper

import com.sankare.wifikill.core.database.entity.ScanSessionEntity
import com.sankare.wifikill.core.domain.model.ScanSession
import com.sankare.wifikill.core.domain.model.ScanStatus

fun ScanSessionEntity.toDomain(): ScanSession = ScanSession(
    id = id,
    networkId = networkId,
    startedAt = startedAt,
    completedAt = completedAt,
    status = runCatching { ScanStatus.valueOf(status) }.getOrDefault(ScanStatus.FAILED),
    devicesFound = devicesFound,
    scanMethod = scanMethod,
    errorMessage = errorMessage,
)
