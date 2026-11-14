package com.sankare.wifikill.core.domain.model

data class ScanSession(
    val id: String,
    val networkId: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: ScanStatus,
    val devicesFound: Int,
    val scanMethod: String,
    val errorMessage: String?,
)

enum class ScanStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}
