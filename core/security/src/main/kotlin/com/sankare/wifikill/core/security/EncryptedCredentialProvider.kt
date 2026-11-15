package com.sankare.wifikill.core.security

import android.content.Context
import android.util.Base64
import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.domain.repository.CredentialProvider
import com.sankare.wifikill.core.domain.repository.RouterCredential
import java.util.UUID
import kotlinx.coroutines.withContext
import org.json.JSONObject

class EncryptedCredentialProvider(
    context: Context,
    private val keystoreManager: KeystoreManager,
    private val dispatchers: DispatchersProvider,
) : CredentialProvider {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override suspend fun save(credential: RouterCredential): AppResult<String> =
        withContext(dispatchers.io) {
            runCatching {
                val credentialId = UUID.randomUUID().toString()
                val plaintext = JSONObject()
                    .put(KEY_USERNAME, credential.username)
                    .put(KEY_PASSWORD, credential.password)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                val payload = keystoreManager.encrypt(plaintext)
                val encoded = buildString {
                    append(Base64.encodeToString(payload.iv, Base64.NO_WRAP))
                    append(SEPARATOR)
                    append(Base64.encodeToString(payload.ciphertext, Base64.NO_WRAP))
                }
                val stored = preferences.edit()
                    .putString(credentialId, encoded)
                    .commit()
                if (!stored) {
                    null
                } else {
                    credentialId
                }
            }.fold(
                onSuccess = { credentialId ->
                    if (credentialId == null) {
                        AppResult.Failure(AppError(AppErrorCode.STORAGE_ERROR, "Unable to store credential"))
                    } else {
                        AppResult.Success(credentialId)
                    }
                },
                onFailure = {
                    AppResult.Failure(AppError(AppErrorCode.STORAGE_ERROR, "Unable to store credential"))
                },
            )
        }

    override suspend fun get(credentialId: String): AppResult<RouterCredential> =
        withContext(dispatchers.io) {
            val encoded = preferences.getString(credentialId, null)
                ?: return@withContext AppResult.Failure(
                    AppError(AppErrorCode.STORAGE_ERROR, "Credential not found"),
                )
            runCatching {
                val parts = encoded.split(SEPARATOR)
                require(parts.size == 2)
                val payload = EncryptedPayload(
                    iv = Base64.decode(parts[0], Base64.NO_WRAP),
                    ciphertext = Base64.decode(parts[1], Base64.NO_WRAP),
                )
                val json = JSONObject(String(keystoreManager.decrypt(payload), Charsets.UTF_8))
                RouterCredential(
                    username = json.getString(KEY_USERNAME),
                    password = json.getString(KEY_PASSWORD),
                )
            }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = {
                    AppResult.Failure(AppError(AppErrorCode.STORAGE_ERROR, "Credential unavailable"))
                },
            )
        }

    override suspend fun delete(credentialId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val removed = preferences.edit().remove(credentialId).commit()
            if (removed) {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(AppError(AppErrorCode.STORAGE_ERROR, "Unable to delete credential"))
            }
        }

    private companion object {
        const val PREFERENCES_NAME = "wifikill_router_credentials"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val SEPARATOR = ":"
    }
}
