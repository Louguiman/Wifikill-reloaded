package com.sankare.wifikill.integrations.openwrt

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.domain.model.AccessPolicyAction
import com.sankare.wifikill.core.domain.model.AccessPolicyRequest
import com.sankare.wifikill.core.domain.model.PolicyOperationReceipt
import com.sankare.wifikill.core.domain.model.PolicyVerificationRequest
import com.sankare.wifikill.core.domain.model.PolicyVerificationResult
import com.sankare.wifikill.core.domain.model.RouterAccessPolicy
import com.sankare.wifikill.core.domain.model.RouterAdapterType
import com.sankare.wifikill.core.domain.model.RouterCapability
import com.sankare.wifikill.core.domain.model.RouterConnectionConfig
import com.sankare.wifikill.core.domain.model.RouterDevice
import com.sankare.wifikill.core.domain.model.RouterInfo
import com.sankare.wifikill.core.domain.model.RouterProtocol
import com.sankare.wifikill.core.domain.repository.CredentialProvider
import com.sankare.wifikill.integrations.router.RouterAdapter
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class OpenWrtAdapter(
    private val credentialProvider: CredentialProvider,
    private val dispatchers: DispatchersProvider,
    private val timeProvider: TimeProvider,
    private val client: UbusClient = UbusClient(),
) : RouterAdapter {

    override val adapterType: RouterAdapterType = RouterAdapterType.OPENWRT

    @Volatile
    private var session: Session? = null

    override suspend fun connect(config: RouterConnectionConfig): AppResult<RouterInfo> =
        withContext(dispatchers.io) {
            val scheme = when (config.protocol) {
                RouterProtocol.HTTP -> "http"
                RouterProtocol.HTTPS -> "https"
                else -> return@withContext AppResult.Failure(
                    AppError(
                        AppErrorCode.UNSUPPORTED_CAPABILITY,
                        "OpenWrt adapter supports HTTP and HTTPS only",
                    ),
                )
            }
            val credential = when (val saved = credentialProvider.get(config.credentialId)) {
                is AppResult.Success -> saved.data
                is AppResult.Failure -> return@withContext AppResult.Failure(saved.error)
            }
            val endpoint = "$scheme://${config.host}:${config.port}/ubus"
            val token = when (
                val login = client.login(endpoint, credential.username, credential.password)
            ) {
                is AppResult.Success -> login.data
                is AppResult.Failure -> {
                    session = null
                    return@withContext AppResult.Failure(login.error)
                }
            }
            session = null

            val board = when (
                val result = client.call(endpoint, token, "system", "board", JsonObject(emptyMap()))
            ) {
                is AppResult.Success -> result.data ?: JsonObject(emptyMap())
                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            val system = when (
                val result = client.call(endpoint, token, "system", "info", JsonObject(emptyMap()))
            ) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> null
            }
            val release = runCatching { system?.get("release")?.jsonObject }.getOrNull()
            val info = RouterInfo(
                routerId = config.routerId,
                manufacturer = "OpenWrt",
                model = board.string("model") ?: board.string("board_name") ?: board.string("system"),
                firmwareVersion = release?.string("description")
                    ?: release?.string("version")
                    ?: system?.string("kernel"),
                hostname = board.string("hostname") ?: system?.string("hostname"),
                managementAddress = "${config.host}:${config.port}",
            )
            session = Session(
                endpoint = endpoint,
                token = token,
                info = info,
            )
            AppResult.Success(info)
        }

    override suspend fun getRouterInfo(): AppResult<RouterInfo> {
        val current = session
            ?: return AppResult.Failure(
                AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router is not connected"),
            )
        return AppResult.Success(current.info)
    }

    override suspend fun discoverDevices(): AppResult<List<RouterDevice>> =
        withContext(dispatchers.io) {
            val current = session
                ?: return@withContext notConnected()
            val payload = when (
                val result = call(current, "dhcp", "ipv4leases", JsonObject(emptyMap()))
            ) {
                is AppResult.Success -> result.data ?: JsonObject(emptyMap())
                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            val devices = mutableListOf<RouterDevice>()
            payload["device"]?.asObject()?.values?.forEach { group ->
                val leases = group.asObject()?.get("leases") as? JsonArray
                leases?.forEach { element ->
                    val lease = element.asObject() ?: return@forEach
                    val expires = lease["expires"]?.jsonPrimitive?.longOrNull
                    devices += RouterDevice(
                        externalId = lease.string("mac"),
                        ipAddress = lease.string("ip"),
                        macAddress = lease.string("mac"),
                        hostname = lease.string("hostname"),
                        interfaceName = null,
                        connectionType = "DHCP",
                        isActive = lease["valid"]?.jsonPrimitive?.booleanOrNull,
                        leaseExpiresAt = expires?.takeIf { it > 0 }
                            ?.let { timeProvider.nowMillis() + it * 1000 },
                    )
                }
            }
            AppResult.Success(devices)
        }

    override suspend fun getCapabilities(): Set<RouterCapability> =
        withContext(dispatchers.io) {
            val current = session ?: return@withContext emptySet()
            current.capabilities?.let { return@withContext it }

            val capabilities = linkedSetOf(RouterCapability.ROUTER_INFORMATION)
            if (call(current, "dhcp", "ipv4leases", JsonObject(emptyMap())) is AppResult.Success) {
                capabilities += RouterCapability.DEVICE_DISCOVERY
                capabilities += RouterCapability.DHCP_CLIENTS
            }
            if (uciGet(current, config = "firewall", type = null, section = null) is AppResult.Success) {
                capabilities += RouterCapability.ACCESS_POLICY_READ
                if (probePolicyWrite(current)) {
                    capabilities += RouterCapability.ACCESS_POLICY_WRITE
                }
            }
            val verified = capabilities.toSet()
            session = current.copy(capabilities = verified)
            verified
        }

    override suspend fun getAccessPolicies(): AppResult<List<RouterAccessPolicy>> =
        withContext(dispatchers.io) {
            val current = session
                ?: return@withContext notConnected()
            val rules = when (val result = firewallRules(current)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            AppResult.Success(
                rules.mapNotNull { (section, values) ->
                    val name = values.string("name") ?: section
                    if (!name.startsWith(BLOCK_NAME_PREFIX)) return@mapNotNull null
                    RouterAccessPolicy(
                        id = section,
                        targetIdentifier = values.string("src_mac")
                            ?: values.string("src_ip")
                            ?: "",
                        action = AccessPolicyAction.BLOCK_INTERNET,
                        description = name,
                    )
                },
            )
        }

    override suspend fun applyAccessPolicy(
        request: AccessPolicyRequest,
    ): AppResult<PolicyOperationReceipt> = withContext(dispatchers.io) {
        val current = session
            ?: return@withContext notConnected()
        val targetMac = request.targetMacAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val targetIp = request.targetIpAddress?.trim()?.takeIf { it.isNotEmpty() }
        if (targetMac == null && targetIp == null) {
            return@withContext AppResult.Failure(
                AppError(
                    AppErrorCode.UNSUPPORTED_CAPABILITY,
                    "The device has no known IP or MAC address",
                ),
            )
        }
        val section = policySection(request.deviceId)
        val submittedAt = timeProvider.nowMillis()

        when (request.action) {
            AccessPolicyAction.BLOCK_INTERNET -> {
                val values = buildJsonObject {
                    put("name", "$BLOCK_NAME_PREFIX${shortId(request.deviceId)}")
                    if (targetMac != null) put("src_mac", targetMac) else put("src_ip", targetIp)
                    put("dest", "wan")
                    put("proto", "all")
                    put("target", "REJECT")
                    put("family", "ipv4")
                    put("enabled", "1")
                }
                val set = uciCall(
                    current,
                    "set",
                    buildJsonObject {
                        put("config", "firewall")
                        put("type", "rule")
                        put("section", section)
                        put("values", values)
                    },
                )
                if (set is AppResult.Failure) return@withContext AppResult.Failure(set.error)
                val committed = commitFirewall(current)
                if (committed is AppResult.Failure) {
                    return@withContext AppResult.Failure(committed.error)
                }
                AppResult.Success(
                    PolicyOperationReceipt(
                        operationId = "openwrt-$section-$submittedAt",
                        routerPolicyReference = section,
                        submittedAt = submittedAt,
                    ),
                )
            }

            AccessPolicyAction.ALLOW_INTERNET -> {
                val deleted = uciCall(
                    current,
                    "delete",
                    buildJsonObject {
                        put("config", "firewall")
                        put("type", "rule")
                        put("section", section)
                    },
                )
                if (deleted is AppResult.Failure &&
                    deleted.error.code != AppErrorCode.UNSUPPORTED_CAPABILITY
                ) {
                    return@withContext AppResult.Failure(deleted.error)
                }
                val committed = commitFirewall(current)
                if (committed is AppResult.Failure) {
                    return@withContext AppResult.Failure(committed.error)
                }
                AppResult.Success(
                    PolicyOperationReceipt(
                        operationId = "openwrt-$section-$submittedAt",
                        routerPolicyReference = section,
                        submittedAt = submittedAt,
                    ),
                )
            }
        }
    }

    override suspend fun removeAccessPolicy(policyId: String): AppResult<PolicyOperationReceipt> =
        withContext(dispatchers.io) {
            val current = session
                ?: return@withContext notConnected()
            val deleted = uciCall(
                current,
                "delete",
                buildJsonObject {
                    put("config", "firewall")
                    put("type", "rule")
                    put("section", policyId)
                },
            )
            if (deleted is AppResult.Failure &&
                deleted.error.code != AppErrorCode.UNSUPPORTED_CAPABILITY
            ) {
                return@withContext AppResult.Failure(deleted.error)
            }
            val committed = commitFirewall(current)
            if (committed is AppResult.Failure) {
                return@withContext AppResult.Failure(committed.error)
            }
            AppResult.Success(
                PolicyOperationReceipt(
                    operationId = "openwrt-remove-$policyId-${timeProvider.nowMillis()}",
                    routerPolicyReference = policyId,
                    submittedAt = timeProvider.nowMillis(),
                ),
            )
        }

    override suspend fun verifyAccessPolicy(
        request: PolicyVerificationRequest,
    ): AppResult<PolicyVerificationResult> = withContext(dispatchers.io) {
        val current = session
            ?: return@withContext notConnected()
        val checkedAt = timeProvider.nowMillis()
        val targetMac = request.targetMacAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val targetIp = request.targetIpAddress?.trim()?.takeIf { it.isNotEmpty() }

        val existing = when {
            !request.routerPolicyReference.isNullOrBlank() -> {
                val payload = uciGet(
                    current,
                    config = "firewall",
                    type = "rule",
                    section = request.routerPolicyReference,
                )
                when (payload) {
                    is AppResult.Success -> {
                        val values = payload.data?.get("values").asObject() ?: payload.data
                        if (values == null) emptyList() else listOf(
                            request.routerPolicyReference to values,
                        )
                    }

                    is AppResult.Failure -> if (
                        payload.error.code == AppErrorCode.UNSUPPORTED_CAPABILITY
                    ) {
                        emptyList()
                    } else {
                        return@withContext AppResult.Failure(payload.error)
                    }
                }
            }

            else -> {
                val rules = when (val result = firewallRules(current)) {
                    is AppResult.Success -> result.data
                    is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
                }
                rules.filter { (_, values) ->
                    matchesTarget(values, targetMac, targetIp)
                }
            }
        }

        val blocked = existing.any { (_, values) ->
            matchesTarget(values, targetMac, targetIp) &&
                values.string("target") == "REJECT" &&
                values.string("enabled") != "0"
        }
        val verified = when (request.expectedAction) {
            AccessPolicyAction.BLOCK_INTERNET -> blocked
            AccessPolicyAction.ALLOW_INTERNET -> !blocked
        }
        AppResult.Success(
            PolicyVerificationResult(
                isVerified = verified,
                actualAction = if (blocked) {
                    AccessPolicyAction.BLOCK_INTERNET
                } else {
                    AccessPolicyAction.ALLOW_INTERNET
                },
                checkedAt = checkedAt,
                details = if (verified) "Router state matches the requested policy" else {
                    "Router state does not match the requested policy"
                },
            ),
        )
    }

    override suspend fun disconnect() {
        session = null
    }

    private fun probePolicyWrite(current: Session): Boolean {
        val params = buildJsonObject {
            put("config", "firewall")
            put("type", "rule")
            put("section", PROBE_SECTION)
            put(
                "values",
                buildJsonObject {
                    put("name", "wifikill-capability-probe")
                    put("target", "REJECT")
                },
            )
        }
        val set = uciCall(current, "set", params)
        if (set is AppResult.Failure) return false
        uciCall(
            current,
            "delete",
            buildJsonObject {
                put("config", "firewall")
                put("type", "rule")
                put("section", PROBE_SECTION)
            },
        )
        return true
    }

    private fun commitFirewall(current: Session): AppResult<Unit> {
        val commit = uciCall(
            current,
            "commit",
            buildJsonObject { put("config", "firewall") },
        )
        if (commit is AppResult.Failure) return AppResult.Failure(commit.error)
        val apply = uciCall(
            current,
            "apply",
            buildJsonObject { put("rollback", false) },
        )
        return when (apply) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> apply
        }
    }

    private fun firewallRules(
        current: Session,
    ): AppResult<List<Pair<String, JsonObject>>> = when (
        val payload = uciGet(current, config = "firewall", type = "rule", section = null)
    ) {
        is AppResult.Success -> {
            val values = payload.data?.get("values").asObject() ?: payload.data
            AppResult.Success(
                values?.mapNotNull { (key, value) ->
                    value.asObject()?.let { key to it }
                } ?: emptyList(),
            )
        }

        is AppResult.Failure -> AppResult.Failure(payload.error)
    }

    private fun uciGet(
        current: Session,
        config: String,
        type: String?,
        section: String?,
    ): AppResult<JsonObject?> {
        val params = buildJsonObject {
            put("config", config)
            type?.let { put("type", it) }
            section?.let { put("section", it) }
        }
        return uciCall(current, "get", params)
    }

    private fun uciCall(
        current: Session,
        method: String,
        params: JsonObject,
    ): AppResult<JsonObject?> = client.call(current.endpoint, current.token, "uci", method, params)

    private fun call(
        current: Session,
        objectName: String,
        method: String,
        params: JsonObject,
    ): AppResult<JsonObject?> = client.call(current.endpoint, current.token, objectName, method, params)

    private fun matchesTarget(
        values: JsonObject,
        targetMac: String?,
        targetIp: String?,
    ): Boolean {
        val mac = values.string("src_mac")?.lowercase()
        val ip = values.string("src_ip")
        return when {
            targetMac != null && mac == targetMac -> true
            targetIp != null && ip == targetIp -> true
            else -> false
        }
    }

    private fun notConnected(): AppResult.Failure = AppResult.Failure(
        AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router is not connected"),
    )

    private fun policySection(deviceId: String): String = "wifikill_${shortId(deviceId)}"

    private fun shortId(raw: String): String {
        val sanitized = raw.filter { it.isLetterOrDigit() }
        return (sanitized.ifEmpty { raw.hashCode().toUInt().toString(16) }).take(24)
    }

    private data class Session(
        val endpoint: String,
        val token: String,
        val info: RouterInfo,
        val capabilities: Set<RouterCapability>? = null,
    )

    private companion object {
        const val BLOCK_NAME_PREFIX = "wifikill-block-"
        const val PROBE_SECTION = "wifikill_probe"
    }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

private fun kotlinx.serialization.json.JsonElement?.asObject(): JsonObject? =
    runCatching { this?.jsonObject }.getOrNull()
