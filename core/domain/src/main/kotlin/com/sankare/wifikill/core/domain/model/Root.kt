package com.sankare.wifikill.core.domain.model

enum class RootCapability {
    ADVANCED_NETWORK_DIAGNOSTICS,
    LOCAL_INTERFACE_INSPECTION,
}

enum class RootOperation {
    INSPECT_LOCAL_INTERFACE,
    READ_NETWORK_CONFIGURATION,
}

data class RootOperationResult(
    val operation: RootOperation,
    val output: String?,
    val executedAt: Long,
)
