package com.sankare.wifikill.core.network

import com.sankare.wifikill.core.domain.model.NetworkContext
import kotlinx.coroutines.flow.Flow

interface NetworkContextProvider {
    fun observeCurrentNetwork(): Flow<NetworkContext?>
}
