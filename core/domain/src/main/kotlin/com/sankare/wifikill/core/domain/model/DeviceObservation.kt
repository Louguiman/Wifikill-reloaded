package com.sankare.wifikill.core.domain.model

data class DeviceObservation(
    val id: String,
    val deviceId: String?,
    val networkId: String,
    val scanSessionId: String?,
    val ipAddress: String?,
    val macAddress: String?,
    val hostname: String?,
    val manufacturer: String?,
    val source: ObservationSource,
    val reachability: ReachabilityStatus,
    val responseTimeMs: Long?,
    val observedAt: Long,
)

enum class ObservationSource {
    ANDROID_NETWORK,
    LOCAL_DISCOVERY,
    ROUTER_INVENTORY,
    MANUAL,
}

enum class ReachabilityStatus {
    RESPONDING,
    NOT_RESPONDING,
    UNKNOWN,
}
