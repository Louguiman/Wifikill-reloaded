package com.sankare.wifikill.data.mapper

import com.sankare.wifikill.core.database.entity.RouterCapabilityEntity
import com.sankare.wifikill.core.database.entity.RouterEntity
import com.sankare.wifikill.core.database.relation.RouterWithCapabilities
import com.sankare.wifikill.core.domain.model.Router
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterConnectionStatus
import com.sankare.wifikill.core.domain.model.RouterProtocol

fun RouterWithCapabilities.toDomain(): Router = router.toDomain(
    capabilities = capabilities
        .filter { it.isSupported }
        .mapNotNull { runCatching { RouterCapability.valueOf(it.capabilityCode) }.getOrNull() }
        .toSet(),
)

fun RouterEntity.toDomain(capabilities: Set<RouterCapability>): Router = Router(
    id = id,
    networkId = networkId,
    name = name,
    host = ipAddress,
    port = port,
    protocol = runCatching { RouterProtocol.valueOf(protocol) }
        .getOrDefault(RouterProtocol.UNKNOWN),
    adapterType = runCatching { RouterAdapterType.valueOf(adapterType) }
        .getOrDefault(RouterAdapterType.GENERIC),
    credentialId = credentialId,
    manufacturer = manufacturer,
    model = model,
    firmwareVersion = firmwareVersion,
    connectionStatus = runCatching { RouterConnectionStatus.valueOf(connectionStatus) }
        .getOrDefault(RouterConnectionStatus.UNKNOWN),
    capabilities = capabilities,
    lastConnectedAt = lastConnectedAt,
    createdAt = createdAt,
)

fun RouterCapabilityEntity.toDomain(): RouterCapability? =
    if (!isSupported) null else runCatching { RouterCapability.valueOf(capabilityCode) }.getOrNull()
