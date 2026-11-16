package com.sankare.wifikill.core.domain.model

data class NetworkContext(
    val networkId: String?,
    val ssid: String?,
    val bssid: String?,
    val gatewayIp: String?,
    val localIp: String?,
    val subnet: String?,
    val interfaceName: String?,
    val networkType: NetworkType,
    val isMetered: Boolean,
    val isValidated: Boolean,
    val dnsServers: List<String> = emptyList(),
)

enum class NetworkType {
    WIFI,
    ETHERNET,
    CELLULAR,
    OTHER,
    UNKNOWN,
}
