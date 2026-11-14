package com.sankare.wifikill.data.mapper

import com.sankare.wifikill.core.database.entity.NetworkEntity
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType

fun NetworkEntity.toDomain(): NetworkContext = NetworkContext(
    networkId = id,
    ssid = ssid,
    bssid = bssid,
    gatewayIp = gatewayIp,
    localIp = null,
    subnet = subnet,
    interfaceName = interfaceName,
    networkType = runCatching { NetworkType.valueOf(networkType) }.getOrDefault(NetworkType.UNKNOWN),
    isMetered = false,
    isValidated = false,
)

fun NetworkContext.toEntity(existing: NetworkEntity?, now: Long): NetworkEntity = NetworkEntity(
    id = requireNotNull(networkId) { "networkId is required to persist a network" },
    ssid = ssid ?: existing?.ssid,
    bssid = bssid ?: existing?.bssid,
    gatewayIp = gatewayIp ?: existing?.gatewayIp,
    subnet = subnet ?: existing?.subnet,
    interfaceName = interfaceName ?: existing?.interfaceName,
    networkType = networkType.name,
    isCurrent = true,
    firstSeenAt = existing?.firstSeenAt ?: now,
    lastSeenAt = now,
)
