package com.sankare.wifikill.core.domain.usecase

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.model.RetentionKind
import com.sankare.wifikill.core.domain.repository.LocalDataRepository
import com.sankare.wifikill.core.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<AppSettings> = repository.observeSettings()
}

class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(): AppSettings = repository.getSettings()
}

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val current = repository.getSettings()
        return repository.updateSettings(current.copy(onboardingCompleted = true))
    }
}

class SetRootModeEnabledUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean): AppResult<Unit> {
        val current = repository.getSettings()
        return repository.updateSettings(current.copy(rootModeEnabled = enabled))
    }
}

class UpdateMonitoringIntervalUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(seconds: Int): AppResult<Unit> {
        if (seconds !in AppSettings.MIN_MONITORING_INTERVAL_SECONDS..
            AppSettings.MAX_MONITORING_INTERVAL_SECONDS
        ) {
            return AppResult.Failure(
                AppError(
                    AppErrorCode.INVALID_ARGUMENT,
                    "Monitoring interval must be between " +
                        "${AppSettings.MIN_MONITORING_INTERVAL_SECONDS} and " +
                        "${AppSettings.MAX_MONITORING_INTERVAL_SECONDS} seconds",
                ),
            )
        }
        val current = repository.getSettings()
        return repository.updateSettings(current.copy(monitoringRefreshIntervalSeconds = seconds))
    }
}

class UpdateRetentionUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(kind: RetentionKind, days: Int): AppResult<Unit> {
        if (days !in AppSettings.MIN_RETENTION_DAYS..AppSettings.MAX_RETENTION_DAYS) {
            return AppResult.Failure(
                AppError(
                    AppErrorCode.INVALID_ARGUMENT,
                    "Retention must be between ${AppSettings.MIN_RETENTION_DAYS} and " +
                        "${AppSettings.MAX_RETENTION_DAYS} days",
                ),
            )
        }
        val current = repository.getSettings()
        val updated = when (kind) {
            RetentionKind.SCAN_HISTORY ->
                current.copy(scanHistoryRetentionDays = days)

            RetentionKind.DEVICE_OBSERVATIONS ->
                current.copy(deviceObservationRetentionDays = days)

            RetentionKind.TRAFFIC ->
                current.copy(trafficRetentionDays = days)

            RetentionKind.DIAGNOSTICS ->
                current.copy(diagnosticsRetentionDays = days)

            RetentionKind.POLICY_AUDIT ->
                current.copy(policyAuditRetentionDays = days)
        }
        return repository.updateSettings(updated)
    }
}

class DeleteLocalDataUseCase @Inject constructor(
    private val repository: LocalDataRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = repository.deleteAllLocalData()
}

class CleanupExpiredDataUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val localDataRepository: LocalDataRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> =
        localDataRepository.deleteExpiredData(settingsRepository.getSettings())
}
