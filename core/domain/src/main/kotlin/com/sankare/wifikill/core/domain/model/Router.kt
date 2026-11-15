package com.sankare.wifikill.core.domain.model

enum class RouterAdapterType {
    OPENWRT,
    MIKROTIK,
    TP_LINK,
    HUAWEI,
    GENERIC,
}

enum class RouterProtocol {
    HTTP,
    HTTPS,
    ROUTER_API,
    UNKNOWN,
}

enum class RouterCapability {
    ROUTER_INFORMATION,
    DEVICE_DISCOVERY,
    DHCP_CLIENTS,
    WIFI_CLIENTS,
    ACCESS_POLICY_READ,
    ACCESS_POLICY_WRITE,
    TRAFFIC_STATISTICS,
    WIFI_CONFIGURATION,
}

enum class RouterConnectionStatus {
    UNKNOWN,
    CONNECTING,
    CONNECTED,
    AUTHENTICATION_FAILED,
    UNREACHABLE,
    DISCONNECTED,
}

data class Router(
    val id: String,
    val networkId: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: RouterProtocol,
    val adapterType: RouterAdapterType,
    val credentialId: String?,
    val manufacturer: String?,
    val model: String?,
    val firmwareVersion: String?,
    val connectionStatus: RouterConnectionStatus,
    val capabilities: Set<RouterCapability>,
    val lastConnectedAt: Long?,
    val createdAt: Long,
)

data class RouterInfo(
    val routerId: String,
    val manufacturer: String?,
    val model: String?,
    val firmwareVersion: String?,
    val hostname: String?,
    val managementAddress: String,
)

data class RouterDevice(
    val externalId: String?,
    val ipAddress: String?,
    val macAddress: String?,
    val hostname: String?,
    val interfaceName: String?,
    val connectionType: String?,
    val isActive: Boolean?,
    val leaseExpiresAt: Long?,
)

data class RouterConnectionConfig(
    val routerId: String,
    val host: String,
    val port: Int,
    val protocol: RouterProtocol,
    val credentialId: String,
)

data class AddRouterRequest(
    val networkId: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: RouterProtocol,
    val adapterType: RouterAdapterType,
    val username: String,
    val password: String,
)

data class RouterAccessPolicy(
    val id: String,
    val targetIdentifier: String,
    val action: AccessPolicyAction,
    val description: String?,
)

data class PolicyOperationReceipt(
    val operationId: String,
    val routerPolicyReference: String?,
    val submittedAt: Long,
)

data class PolicyVerificationRequest(
    val operationId: String,
    val routerPolicyReference: String?,
    val expectedAction: AccessPolicyAction,
    val deviceId: String,
    val targetMacAddress: String? = null,
    val targetIpAddress: String? = null,
)
