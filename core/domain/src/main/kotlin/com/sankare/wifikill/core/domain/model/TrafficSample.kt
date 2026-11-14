package com.sankare.wifikill.core.domain.model

data class TrafficSample(
    val id: String,
    val deviceId: String?,
    val networkId: String,
    val source: TrafficSource,
    val bytesReceived: Long?,
    val bytesSent: Long?,
    val sampledAt: Long,
)

enum class TrafficSource {
    ROUTER_TELEMETRY,
    ANDROID_LOCAL,
    UNKNOWN,
}
