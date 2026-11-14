package com.sankare.wifikill.core.domain.model

import com.sankare.wifikill.core.common.AppError

sealed interface ScanEvent {
    data class Started(
        val sessionId: String,
        val startedAt: Long,
    ) : ScanEvent

    data class SourceStarted(
        val source: ObservationSource,
    ) : ScanEvent

    data class SourceCompleted(
        val source: ObservationSource,
        val devicesFound: Int,
    ) : ScanEvent

    data class DeviceDiscovered(
        val observation: DeviceObservation,
    ) : ScanEvent

    data class Progress(
        val completedSources: Int,
        val totalSources: Int,
    ) : ScanEvent

    data class Completed(
        val sessionId: String,
        val devicesFound: Int,
        val completedAt: Long,
    ) : ScanEvent

    data class Failed(
        val sessionId: String,
        val error: AppError,
    ) : ScanEvent
}

data class ScanRequest(
    val networkId: String,
    val sources: Set<ObservationSource>,
    val timeoutMs: Long = 30_000,
    val includeRouterInventory: Boolean = true,
)
