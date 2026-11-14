package com.sankare.wifikill.core.domain.service

import com.sankare.wifikill.core.domain.model.AccessPolicyStatus
import kotlinx.coroutines.flow.SharedFlow

sealed interface AppEvent {
    data class NetworkChanged(val networkId: String?) : AppEvent

    data class RouterConnectionChanged(val routerId: String, val connected: Boolean) : AppEvent

    data class DeviceDiscovered(val deviceId: String) : AppEvent

    data class ScanCompleted(val sessionId: String) : AppEvent

    data class AccessPolicyChanged(
        val policyId: String,
        val status: AccessPolicyStatus,
    ) : AppEvent
}

interface AppEventBus {
    val events: SharedFlow<AppEvent>

    suspend fun emit(event: AppEvent)
}
