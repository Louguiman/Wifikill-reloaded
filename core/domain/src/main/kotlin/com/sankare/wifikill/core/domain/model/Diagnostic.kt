package com.sankare.wifikill.core.domain.model

import com.sankare.wifikill.core.common.AppError

data class DiagnosticRequest(
    val networkId: String,
    val type: DiagnosticType,
    val target: String?,
)

enum class DiagnosticType {
    GATEWAY_REACHABILITY,
    DNS_RESOLUTION,
    INTERNET_CONNECTIVITY,
    LATENCY,
    PACKET_LOSS,
    NETWORK_CONFIGURATION,
}

enum class DiagnosticStatus {
    RUNNING,
    SUCCESS,
    WARNING,
    FAILED,
    UNAVAILABLE,
}

data class DiagnosticResult(
    val id: String,
    val networkId: String,
    val type: DiagnosticType,
    val target: String?,
    val status: DiagnosticStatus,
    val latencyMs: Long?,
    val resultMessage: String?,
    val executedAt: Long,
)

sealed interface DiagnosticExecutionEvent {
    data object Started : DiagnosticExecutionEvent

    data class Progress(val message: String) : DiagnosticExecutionEvent

    data class Completed(val result: DiagnosticResult) : DiagnosticExecutionEvent

    data class Failed(val error: AppError) : DiagnosticExecutionEvent
}
