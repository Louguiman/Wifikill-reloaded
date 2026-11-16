package com.sankare.wifikill.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

data class AppError(
    val code: AppErrorCode,
    val message: String? = null,
    val retryable: Boolean = false
)

enum class AppErrorCode {
    NETWORK_UNAVAILABLE,
    PERMISSION_DENIED,
    INVALID_NETWORK_CONTEXT,
    SCAN_TIMEOUT,
    ROUTER_UNREACHABLE,
    AUTHENTICATION_FAILED,
    UNSUPPORTED_CAPABILITY,
    POLICY_VERIFICATION_FAILED,
    OPERATION_CANCELLED,
    STORAGE_ERROR,
    INVALID_ARGUMENT,
    ROOT_UNAVAILABLE,
    ROOT_OPERATION_FAILED,
    UNKNOWN,
}
