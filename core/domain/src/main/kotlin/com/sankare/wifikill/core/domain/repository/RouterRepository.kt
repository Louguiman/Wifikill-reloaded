package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AddRouterRequest
import com.sankare.wifikill.core.domain.model.Router
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterInfo
import kotlinx.coroutines.flow.Flow

interface RouterRepository {
    fun observeRouters(networkId: String): Flow<List<Router>>

    suspend fun getRouter(routerId: String): Router?

    suspend fun addRouter(request: AddRouterRequest): AppResult<Router>

    suspend fun connectRouter(routerId: String): AppResult<RouterInfo>

    suspend fun disconnectRouter(routerId: String): AppResult<Unit>

    suspend fun getCapabilities(routerId: String): Set<RouterCapability>

    suspend fun refreshCapabilities(routerId: String): AppResult<Set<RouterCapability>>

    suspend fun removeRouter(routerId: String): AppResult<Unit>
}
