package com.sankare.wifikill.core.domain.model

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val monitoringRefreshIntervalSeconds: Int = DEFAULT_MONITORING_INTERVAL_SECONDS,
    val scanHistoryRetentionDays: Int = DEFAULT_SCAN_HISTORY_RETENTION_DAYS,
    val deviceObservationRetentionDays: Int = DEFAULT_OBSERVATION_RETENTION_DAYS,
    val trafficRetentionDays: Int = DEFAULT_TRAFFIC_RETENTION_DAYS,
    val diagnosticsRetentionDays: Int = DEFAULT_DIAGNOSTICS_RETENTION_DAYS,
    val policyAuditRetentionDays: Int = DEFAULT_POLICY_AUDIT_RETENTION_DAYS,
    val rootModeEnabled: Boolean = false,
) {
    companion object {
        const val MIN_MONITORING_INTERVAL_SECONDS = 2
        const val MAX_MONITORING_INTERVAL_SECONDS = 60
        const val MIN_RETENTION_DAYS = 1
        const val MAX_RETENTION_DAYS = 365

        const val DEFAULT_MONITORING_INTERVAL_SECONDS = 5
        const val DEFAULT_SCAN_HISTORY_RETENTION_DAYS = 30
        const val DEFAULT_OBSERVATION_RETENTION_DAYS = 30
        const val DEFAULT_TRAFFIC_RETENTION_DAYS = 7
        const val DEFAULT_DIAGNOSTICS_RETENTION_DAYS = 30
        const val DEFAULT_POLICY_AUDIT_RETENTION_DAYS = 90
    }
}

enum class RetentionKind {
    SCAN_HISTORY,
    DEVICE_OBSERVATIONS,
    TRAFFIC,
    DIAGNOSTICS,
    POLICY_AUDIT,
}
