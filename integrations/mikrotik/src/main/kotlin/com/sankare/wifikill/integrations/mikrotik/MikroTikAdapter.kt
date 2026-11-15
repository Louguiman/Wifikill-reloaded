package com.sankare.wifikill.integrations.mikrotik

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
import com.sankare.wifikill.core.domain.repository.RouterCredential
import com.sankare.wifikill.integrations.router.RouterAdapter
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class MikroTikAdapter(
    private val credentialProvider: CredentialProvider,
    private val dispatchers: DispatchersProvider,
    private val timeProvider: TimeProvider,
    private val client: RouterOsClient = RouterOsClient(),
) : RouterAdapter {

    override val adapterType: RouterAdapterType = RouterAdapterType.MIKROTIK

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

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
                        "MikroTik adapter supports HTTP and HTTPS REST only",
                    ),
                )
            }
            val credential = when (val saved = credentialProvider.get(config.credentialId)) {
                is AppResult.Success -> saved.data
                is AppResult.Failure -> return@withContext AppResult.Failure(saved.error)
            }
            val baseUrl = "$scheme://${config.host}:${config.port}/rest"
            session = null

            val resource = when (
                val result = client.execute("GET", "$baseUrl/system/resource", credential)
            ) {
                is AppResult.Success -> parseObject(result.data)
                    ?: return@withContext invalidResponse()

                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            val identity = when (
                val result = client.execute("GET", "$baseUrl/system/identity", credential)
            ) {
                is AppResult.Success -> parseObject(result.data)
                is AppResult.Failure -> null
            }
            val info = RouterInfo(
                routerId = config.routerId,
                manufacturer = "MikroTik",
                model = resource.string("board-name") ?: resource.string("architecture-name"),
                firmwareVersion = resource.string("version"),
                hostname = identity?.string("name"),
                managementAddress = "${config.host}:${config.port}",
            )
            session = Session(
                baseUrl = baseUrl,
                credential = credential,
                info = info,
            )
            AppResult.Success(info)
        }

    override suspend fun getRouterInfo(): AppResult<RouterInfo> {
        val current = session
            ?: return notConnected()
        return AppResult.Success(current.info)
    }

    override suspend fun discoverDevices(): AppResult<List<RouterDevice>> =
        withContext(dispatchers.io) {
            val current = session
                ?: return@withContext notConnected()
            val body = when (val result = execute(current, "GET", "/ip/dhcp-server/lease")) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            AppResult.Success(
                parseArray(body).mapNotNull { lease ->
                    val externalId = lease.string(".id") ?: return@mapNotNull null
                    RouterDevice(
                        externalId = externalId,
                        ipAddress = lease.string("address"),
                        macAddress = lease.string("mac-address"),
                        hostname = lease.string("host-name") ?: lease.string("comment"),
                        interfaceName = lease.string("server"),
                        connectionType = "DHCP",
                        isActive = lease.string("status")?.let { it == "bound" },
                        leaseExpiresAt = null,
                    )
                },
            )
        }

    override suspend fun getCapabilities(): Set<RouterCapability> =
        withContext(dispatchers.io) {
            val current = session ?: return@withContext emptySet()
            current.capabilities?.let { return@withContext it }

            val capabilities = linkedSetOf(RouterCapability.ROUTER_INFORMATION)
            if (execute(current, "GET", "/ip/dhcp-server/lease") is AppResult.Success) {
                capabilities += RouterCapability.DEVICE_DISCOVERY
                capabilities += RouterCapability.DHCP_CLIENTS
            }
            if (execute(current, "GET", ADDRESS_LIST_PATH) is AppResult.Success) {
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
            val entries = when (val result = blockEntries(current)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            AppResult.Success(
                entries.mapNotNull { entry ->
                    val id = entry.string(".id") ?: return@mapNotNull null
                    RouterAccessPolicy(
                        id = id,
                        targetIdentifier = entry.string("address") ?: "",
                        action = AccessPolicyAction.BLOCK_INTERNET,
                        description = entry.string("comment"),
                    )
                },
            )
        }

    override suspend fun applyAccessPolicy(
        request: AccessPolicyRequest,
    ): AppResult<PolicyOperationReceipt> = withContext(dispatchers.io) {
        val current = session
            ?: return@withContext notConnected()
        val targetIp = request.targetIpAddress?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@withContext AppResult.Failure(
                AppError(
                    AppErrorCode.UNSUPPORTED_CAPABILITY,
                    "MikroTik policies require a known device IP address",
                ),
            )
        val normalizedIp = normalizeIp(targetIp)
        val submittedAt = timeProvider.nowMillis()

        when (request.action) {
            AccessPolicyAction.BLOCK_INTERNET -> {
                when (val filter = ensureBlockFilter(current)) {
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> return@withContext AppResult.Failure(filter.error)
                }
                val existing = when (val result = blockEntries(current)) {
                    is AppResult.Success -> result.data.firstOrNull { entry ->
                        normalizeIp(entry.string("address").orEmpty()) == normalizedIp
                    }

                    is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
                }
                val reference = if (existing != null) {
                    existing.string(".id")
                } else {
                    val created = execute(
                        current,
                        "PUT",
                        ADDRESS_LIST_PATH,
                        buildJsonObject {
                            put("list", BLOCK_LIST)
                            put("address", "$normalizedIp/32")
                            put("comment", "wifikill:${request.deviceId}")
                        }.toString(),
                    )
                    when (created) {
                        is AppResult.Success -> parseObject(created.data)?.string(".id")
                            ?: return@withContext AppResult.Failure(
                                AppError(
                                    AppErrorCode.UNKNOWN,
                                    "Router did not return a policy reference",
                                ),
                            )

                        is AppResult.Failure -> return@withContext AppResult.Failure(created.error)
                    }
                }
                AppResult.Success(
                    PolicyOperationReceipt(
                        operationId = "mikrotik-$reference-$submittedAt",
                        routerPolicyReference = reference,
                        submittedAt = submittedAt,
                    ),
                )
            }

            AccessPolicyAction.ALLOW_INTERNET -> {
                val entries = when (val result = blockEntries(current)) {
                    is AppResult.Success -> result.data.filter { entry ->
                        normalizeIp(entry.string("address").orEmpty()) == normalizedIp
                    }

                    is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
                }
                var reference: String? = null
                entries.forEach { entry ->
                    val id = entry.string(".id") ?: return@forEach
                    when (val deleted = execute(current, "DELETE", "$ADDRESS_LIST_PATH/$id")) {
                        is AppResult.Success -> reference = reference ?: id
                        is AppResult.Failure -> return@withContext AppResult.Failure(deleted.error)
                    }
                }
                AppResult.Success(
                    PolicyOperationReceipt(
                        operationId = "mikrotik-$normalizedIp-$submittedAt",
                        routerPolicyReference = reference,
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
            when (val deleted = execute(current, "DELETE", "$ADDRESS_LIST_PATH/$policyId")) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> if (deleted.error.code != AppErrorCode.UNSUPPORTED_CAPABILITY) {
                    return@withContext AppResult.Failure(deleted.error)
                }
            }
            AppResult.Success(
                PolicyOperationReceipt(
                    operationId = "mikrotik-remove-$policyId-${timeProvider.nowMillis()}",
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
        val entries = when (val result = blockEntries(current)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
        }
        val targetIp = request.targetIpAddress?.trim()?.takeIf { it.isNotEmpty() }
        val reference = request.routerPolicyReference
        val matched = entries.filter { entry ->
            when {
                !reference.isNullOrBlank() -> entry.string(".id") == reference
                targetIp != null -> normalizeIp(entry.string("address").orEmpty()) ==
                    normalizeIp(targetIp)

                else -> false
            }
        }
        val blocked = matched.isNotEmpty()
        val filterPresent = if (request.expectedAction == AccessPolicyAction.BLOCK_INTERNET) {
            when (val filter = managedFilter(current)) {
                is AppResult.Success -> filter.data != null
                is AppResult.Failure -> return@withContext AppResult.Failure(filter.error)
            }
        } else {
            false
        }
        val verified = when (request.expectedAction) {
            AccessPolicyAction.BLOCK_INTERNET -> blocked && filterPresent
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
                checkedAt = timeProvider.nowMillis(),
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
        val created = execute(
            current,
            "PUT",
            ADDRESS_LIST_PATH,
            buildJsonObject {
                put("list", PROBE_LIST)
                put("address", PROBE_ADDRESS)
                put("comment", PROBE_COMMENT)
            }.toString(),
        )
        val body = when (created) {
            is AppResult.Success -> created.data
            is AppResult.Failure -> return false
        }
        val id = parseObject(body)?.string(".id")
        if (id != null) {
            execute(current, "DELETE", "$ADDRESS_LIST_PATH/$id")
        }
        return true
    }

    private fun ensureBlockFilter(current: Session): AppResult<Unit> {
        val existing = when (val result = managedFilter(current)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return AppResult.Failure(result.error)
        }
        if (existing != null) return AppResult.Success(Unit)
        val created = execute(
            current,
            "PUT",
            FILTER_PATH,
            buildJsonObject {
                put("chain", "forward")
                put("action", "drop")
                put("src-address-list", BLOCK_LIST)
                put("comment", MANAGED_FILTER_COMMENT)
            }.toString(),
        )
        return when (created) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Failure(created.error)
        }
    }

    private fun managedFilter(current: Session): AppResult<JsonObject?> {
        val body = when (val result = execute(current, "GET", FILTER_PATH)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return AppResult.Failure(result.error)
        }
        return AppResult.Success(
            parseArray(body).firstOrNull { rule ->
                rule.string("comment") == MANAGED_FILTER_COMMENT &&
                    rule.string("src-address-list") == BLOCK_LIST
            },
        )
    }

    private fun blockEntries(current: Session): AppResult<List<JsonObject>> {
        val body = when (val result = execute(current, "GET", ADDRESS_LIST_PATH)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return AppResult.Failure(result.error)
        }
        return AppResult.Success(
            parseArray(body).filter { it.string("list") == BLOCK_LIST },
        )
    }

    private fun execute(
        current: Session,
        method: String,
        path: String,
        body: String? = null,
    ): AppResult<String?> = client.execute(
        method = method,
        url = "${current.baseUrl}$path",
        credential = current.credential,
        body = body,
    )

    private fun parseObject(body: String?): JsonObject? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            when (val element = json.parseToJsonElement(body)) {
                is JsonObject -> element
                is JsonArray -> element.firstNotNullOfOrNull { it as? JsonObject }
                else -> null
            }
        }.getOrNull()
    }

    private fun parseArray(body: String?): List<JsonObject> {
        if (body.isNullOrBlank()) return emptyList()
        return runCatching {
            when (val element = json.parseToJsonElement(body)) {
                is JsonArray -> element.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(element)
                else -> emptyList()
            }
        }.getOrNull() ?: emptyList()
    }

    private fun normalizeIp(value: String): String =
        value.substringBefore("/").trim()

    private fun notConnected(): AppResult.Failure = AppResult.Failure(
        AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router is not connected"),
    )

    private fun invalidResponse(): AppResult.Failure = AppResult.Failure(
        AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router returned an invalid response"),
    )

    private data class Session(
        val baseUrl: String,
        val credential: RouterCredential,
        val info: RouterInfo,
        val capabilities: Set<RouterCapability>? = null,
    )

    private companion object {
        const val ADDRESS_LIST_PATH = "/ip/firewall/address-list"
        const val FILTER_PATH = "/ip/firewall/filter"
        const val BLOCK_LIST = "wifikill-blocked"
        const val MANAGED_FILTER_COMMENT = "wifikill-managed-block"
        const val PROBE_LIST = "wifikill-probe"
        const val PROBE_ADDRESS = "192.0.2.1/32"
        const val PROBE_COMMENT = "wifikill-capability-probe"
    }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
