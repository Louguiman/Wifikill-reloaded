package com.sankare.wifikill.integrations.openwrt

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class UbusClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun login(
        endpoint: String,
        username: String,
        password: String,
    ): AppResult<String> {
        val params = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        return when (val outcome = call(endpoint, ZERO_SESSION, "session", "login", params)) {
            is AppResult.Success -> {
                val token = outcome.data?.get("ubus_rpc_session")?.jsonPrimitive?.content
                if (token.isNullOrBlank()) {
                    AppResult.Failure(
                        AppError(AppErrorCode.AUTHENTICATION_FAILED, "Router did not create a session"),
                    )
                } else {
                    AppResult.Success(token)
                }
            }

            is AppResult.Failure -> when (outcome.error.code) {
                AppErrorCode.PERMISSION_DENIED -> AppResult.Failure(
                    AppError(AppErrorCode.AUTHENTICATION_FAILED, "Router rejected the credentials"),
                )

                else -> AppResult.Failure(outcome.error)
            }
        }
    }

    fun validate(endpoint: String, session: String): AppResult<Unit> =
        when (val outcome = call(endpoint, session, "session", "login", buildJsonObject { })) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Failure(outcome.error)
        }

    fun call(
        endpoint: String,
        session: String,
        objectName: String,
        method: String,
        params: JsonObject,
    ): AppResult<JsonObject?> {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "call")
            put(
                "params",
                buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive(session))
                    add(kotlinx.serialization.json.JsonPrimitive(objectName))
                    add(kotlinx.serialization.json.JsonPrimitive(method))
                    add(params)
                },
            )
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            return AppResult.Failure(
                AppError(
                    AppErrorCode.ROUTER_UNREACHABLE,
                    "Router did not respond",
                    retryable = true,
                ),
            )
        }

        response.use { result ->
            if (result.code == 401 || result.code == 403) {
                return AppResult.Failure(
                    AppError(AppErrorCode.AUTHENTICATION_FAILED, "Router rejected the request"),
                )
            }
            if (!result.isSuccessful) {
                return AppResult.Failure(
                    AppError(
                        AppErrorCode.ROUTER_UNREACHABLE,
                        "Router returned HTTP ${result.code}",
                        retryable = result.code >= 500,
                    ),
                )
            }
            val body = result.body?.string()
            if (body.isNullOrBlank()) {
                return AppResult.Failure(
                    AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router returned an empty response"),
                )
            }
            return parse(body)
        }
    }

    private fun parse(body: String): AppResult<JsonObject?> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse {
            return AppResult.Failure(
                AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router returned an invalid response"),
            )
        }
        root["error"]?.let { error ->
            val message = runCatching {
                error.jsonObject["message"]?.jsonPrimitive?.content
            }.getOrNull()
            return AppResult.Failure(
                AppError(AppErrorCode.ROUTER_UNREACHABLE, message ?: "Router request failed"),
            )
        }
        val result = root["result"] as? JsonArray
            ?: return AppResult.Failure(
                AppError(AppErrorCode.ROUTER_UNREACHABLE, "Router returned no result"),
            )
        val status = result.getOrNull(0)?.jsonPrimitive?.intOrNull ?: -1
        if (status != 0) {
            return AppResult.Failure(ubusError(status))
        }
        return AppResult.Success(result.getOrNull(1)?.let { it as? JsonObject })
    }

    private fun ubusError(status: Int): AppError = when (status) {
        5 -> AppError(AppErrorCode.UNSUPPORTED_CAPABILITY, "Router objects are unavailable")
        6 -> AppError(AppErrorCode.PERMISSION_DENIED, "Router denied access")
        7 -> AppError(AppErrorCode.UNSUPPORTED_CAPABILITY, "Router request is not supported")
        else -> AppError(AppErrorCode.UNKNOWN, "Router request failed ($status)")
    }

    private companion object {
        const val ZERO_SESSION = "00000000000000000000000000000000"
        const val TIMEOUT_SECONDS = 8L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
