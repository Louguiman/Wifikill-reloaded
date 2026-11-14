package com.sankare.wifikill.core.network.discovery

data class DiscoveredHost(
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val hostname: String? = null,
    val manufacturer: String? = null,
    val responseTimeMs: Long? = null,
)
