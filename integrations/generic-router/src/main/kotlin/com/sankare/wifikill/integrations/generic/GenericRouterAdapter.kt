package com.sankare.wifikill.integrations.generic

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class GenericRouterAdapter @Inject constructor(
    private val credentialProvider: CredentialProvider,
    private val dispatchers: DispatchersProvider,
) : RouterAdapter {

    override val adapterType: RouterAdapterType = RouterAdapterType.GENERIC

    @Volatile
    private var session: Session? = null

    override suspend fun connect(config: RouterConnectionConfig): AppResult<RouterInfo> =
        withContext(dispatchers.io) {
            val credential = when (val result = credentialProvider.get(config.credentialId)) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> return@withContext AppResult.Failure(result.error)
            }
            val scheme = when (config.protocol) {
                RouterProtocol.HTTP -> "http"
                RouterProtocol.HTTPS -> "https"
                else -> return@withContext AppResult.Failure(
                    AppError(
                        AppErrorCode.UNSUPPORTED_CAPABILITY,
                        "Generic adapter supports HTTP and HTTPS only",
                    ),
                )
            }
            coroutineContext.ensureActive()
            val url = runCatching { URL("$scheme://${config.host}:${config.port}/") }
                .getOrElse {
                    return@withContext AppResult.Failure(
                        AppError(AppErrorCode.ROUTER_UNREACHABLE, "Invalid router address"),
                    )
                }
            val outcome = runCatching { probe(config, url, credential) }
                .getOrElse { error ->
                    if (error is IOException) {
                        AppResult.Failure(
                            AppError(
                                AppErrorCode.ROUTER_UNREACHABLE,
                                "Router did not respond",
                                retryable = true,
                            ),
                        )
                    } else {
                        throw error
                    }
                }
            if (outcome is AppResult.Success) {
                session = Session(config, outcome.data)
            } else {
                session = null
            }
            outcome
        }

    private fun probe(
        config: RouterConnectionConfig,
        url: URL,
        credential: RouterCredential,
    ): AppResult<RouterInfo> {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Authorization", basicAuth(credential))
            setRequestProperty("Accept", "text/html,application/json")
        }
        return try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN,
                -> AppResult.Failure(
                    AppError(AppErrorCode.AUTHENTICATION_FAILED, "Router rejected the credentials"),
                )

                in 200..299 -> AppResult.Success(
                    RouterInfo(
                        routerId = config.routerId,
                        manufacturer = null,
                        model = null,
                        firmwareVersion = null,
                        hostname = null,
                        managementAddress = "${config.host}:${config.port}",
                    ),
                )

                else -> AppResult.Failure(
                    AppError(
                        AppErrorCode.ROUTER_UNREACHABLE,
                        "Router returned HTTP $code",
                        retryable = true,
                    ),
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun getRouterInfo(): AppResult<RouterInfo> {
        val current = session
            ?: return AppResult.Failure(
                AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router is not connected"),
            )
        return AppResult.Success(current.info)
    }

    override suspend fun discoverDevices(): AppResult<List<RouterDevice>> =
        AppResult.Failure(
            AppError(
                AppErrorCode.UNSUPPORTED_CAPABILITY,
                "Generic adapter does not expose a device inventory",
            ),
        )

    override suspend fun getCapabilities(): Set<RouterCapability> =
        if (session != null) {
            setOf(RouterCapability.ROUTER_INFORMATION)
        } else {
            emptySet()
        }

    override suspend fun getAccessPolicies(): AppResult<List<RouterAccessPolicy>> =
        unsupported()

    override suspend fun applyAccessPolicy(
        request: AccessPolicyRequest,
    ): AppResult<PolicyOperationReceipt> = unsupported()

    override suspend fun removeAccessPolicy(
        policyId: String,
    ): AppResult<PolicyOperationReceipt> = unsupported()

    override suspend fun verifyAccessPolicy(
        request: PolicyVerificationRequest,
    ): AppResult<PolicyVerificationResult> = unsupported()

    override suspend fun disconnect() {
        session = null
    }

    private fun <T> unsupported(): AppResult<T> = AppResult.Failure(
        AppError(
            AppErrorCode.UNSUPPORTED_CAPABILITY,
            "Generic adapter does not support access policies",
        ),
    )

    private fun basicAuth(credential: RouterCredential): String {
        val raw = "${credential.username}:${credential.password}".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.getEncoder().encodeToString(raw)
    }

    private data class Session(
        val config: RouterConnectionConfig,
        val info: RouterInfo,
    )

    private companion object {
        const val TIMEOUT_MS = 8_000
    }
}
