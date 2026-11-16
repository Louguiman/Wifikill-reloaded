package com.sankare.wifikill.data.mapper

import com.sankare.wifikill.core.database.entity.DiagnosticResultEntity
import com.sankare.wifikill.core.domain.model.DiagnosticResult
import com.sankare.wifikill.core.domain.model.DiagnosticStatus
import com.sankare.wifikill.core.domain.model.DiagnosticType

fun DiagnosticResultEntity.toDomain(): DiagnosticResult = DiagnosticResult(
    id = id,
    networkId = networkId,
    type = runCatching { DiagnosticType.valueOf(diagnosticType) }
        .getOrDefault(DiagnosticType.NETWORK_CONFIGURATION),
    target = target,
    status = runCatching { DiagnosticStatus.valueOf(status) }
        .getOrDefault(DiagnosticStatus.FAILED),
    latencyMs = latencyMs,
    resultMessage = resultMessage,
    executedAt = executedAt,
)

fun DiagnosticResult.toEntity(): DiagnosticResultEntity = DiagnosticResultEntity(
    id = id,
    networkId = networkId,
    diagnosticType = type.name,
    target = target,
    status = status.name,
    latencyMs = latencyMs,
    resultMessage = resultMessage,
    executedAt = executedAt,
)
