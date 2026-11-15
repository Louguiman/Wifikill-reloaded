package com.sankare.wifikill.integrations.mikrotik

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.repository.RouterCredential
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RouterOsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
) {
    fun execute(
        method: String,
        url: String,
        credential: RouterCredential,
        body: String? = null,
    ): AppResult<String?> {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", basicAuth(credential))
            .header("Accept", "application/json")
        val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "PUT" -> builder.put(requestBody ?: "{}".toRequestBody(JSON_MEDIA_TYPE))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody(JSON_MEDIA_TYPE))
            else -> return AppResult.Failure(
                AppError(AppErrorCode.UNSUPPORTED_CAPABILITY, "Unsupported HTTP method"),
            )
        }

        val response = try {
            client.newCall(builder.build()).execute()
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
            when {
                result.code == 401 || result.code == 403 -> return AppResult.Failure(
                    AppError(AppErrorCode.AUTHENTICATION_FAILED, "Router rejected the credentials"),
                )

                result.code == 404 -> return AppResult.Failure(
                    AppError(AppErrorCode.UNSUPPORTED_CAPABILITY, "Router resource is unavailable"),
                )

                !result.isSuccessful -> return AppResult.Failure(
                    AppError(
                        AppErrorCode.ROUTER_UNREACHABLE,
                        "Router returned HTTP ${result.code}",
                        retryable = result.code >= 500,
                    ),
                )

                else -> {
                    val content = result.body?.string()
                    return AppResult.Success(content?.takeIf { it.isNotBlank() })
                }
            }
        }
    }

    private fun basicAuth(credential: RouterCredential): String {
        val raw = "${credential.username}:${credential.password}".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.getEncoder().encodeToString(raw)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 8L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
