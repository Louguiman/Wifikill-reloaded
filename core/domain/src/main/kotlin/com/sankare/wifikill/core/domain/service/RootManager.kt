package com.sankare.wifikill.core.domain.service

import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.RootCapability
import com.sankare.wifikill.core.domain.model.RootOperation
import com.sankare.wifikill.core.domain.model.RootOperationResult

interface RootManager {
    suspend fun isAvailable(): Boolean

    suspend fun getCapabilities(): Set<RootCapability>

    suspend fun execute(operation: RootOperation): AppResult<RootOperationResult>
}
