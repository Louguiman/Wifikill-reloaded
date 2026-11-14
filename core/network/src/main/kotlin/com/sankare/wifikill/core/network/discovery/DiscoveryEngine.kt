package com.sankare.wifikill.core.network.discovery

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.ObservationSource
import kotlinx.coroutines.flow.Flow

interface DiscoveryEngine {
    fun discover(
        context: NetworkContext,
        sources: Set<ObservationSource>,
        timeoutMs: Long,
    ): Flow<DiscoveryEvent>
}

sealed interface DiscoveryEvent {
    data class SourceStarted(val source: ObservationSource) : DiscoveryEvent

    data class HostFound(
        val source: ObservationSource,
        val host: DiscoveredHost,
    ) : DiscoveryEvent

    data class SourceCompleted(
        val source: ObservationSource,
        val devicesFound: Int,
    ) : DiscoveryEvent

    data class SourceUnavailable(val source: ObservationSource) : DiscoveryEvent

    data class SourceFailed(
        val source: ObservationSource,
        val error: AppError,
    ) : DiscoveryEvent
}
