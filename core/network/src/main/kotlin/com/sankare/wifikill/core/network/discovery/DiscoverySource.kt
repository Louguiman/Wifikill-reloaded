package com.sankare.wifikill.core.network.discovery

import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.ObservationSource
import kotlinx.coroutines.flow.Flow

interface DiscoverySource {
    val source: ObservationSource

    suspend fun isAvailable(context: NetworkContext): Boolean

    fun discover(context: NetworkContext): Flow<DiscoveredHost>
}
