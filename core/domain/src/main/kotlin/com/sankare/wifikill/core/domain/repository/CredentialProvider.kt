package com.sankare.wifikill.core.domain.repository

import com.sankare.wifikill.core.common.AppResult

data class RouterCredential(
    val username: String,
    val password: String,
)

interface CredentialProvider {
    suspend fun save(credential: RouterCredential): AppResult<String>

    suspend fun get(credentialId: String): AppResult<RouterCredential>

    suspend fun delete(credentialId: String): AppResult<Unit>
}
