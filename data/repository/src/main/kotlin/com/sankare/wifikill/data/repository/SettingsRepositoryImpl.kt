package com.sankare.wifikill.data.repository

import com.sankare.wifikill.core.common.AppError
import com.sankare.wifikill.core.common.AppErrorCode
import com.sankare.wifikill.core.common.AppResult
import com.sankare.wifikill.core.common.DispatchersProvider
import com.sankare.wifikill.core.common.TimeProvider
import com.sankare.wifikill.core.database.dao.AppSettingsDao
import com.sankare.wifikill.core.database.entity.AppSettingsEntity
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl @Inject constructor(
    private val settingsDao: AppSettingsDao,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatchersProvider,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        settingsDao.observeAll().map { entities -> entities.toSettings() }

    override suspend fun getSettings(): AppSettings =
        withContext(dispatchers.io) { settingsDao.getAll().toSettings() }

    override suspend fun updateSettings(settings: AppSettings): AppResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val now = timeProvider.nowMillis()
                settingsDao.upsertAll(settings.toEntities(now))
            }.fold(
                onSuccess = { AppResult.Success(Unit) },
                onFailure = {
                    AppResult.Failure(
                        AppError(AppErrorCode.STORAGE_ERROR, "Unable to save settings"),
                    )
                },
            )
        }

    private fun List<AppSettingsEntity>.toSettings(): AppSettings {
        val values = associate { it.settingKey to it.settingValue }
        return AppSettings(
            onboardingCompleted = values.boolean(KEY_ONBOARDING_COMPLETED, false),
            monitoringRefreshIntervalSeconds = values.int(
                KEY_MONITORING_INTERVAL_SECONDS,
                AppSettings.DEFAULT_MONITORING_INTERVAL_SECONDS,
            ).coerceIn(
                AppSettings.MIN_MONITORING_INTERVAL_SECONDS,
                AppSettings.MAX_MONITORING_INTERVAL_SECONDS,
            ),
            scanHistoryRetentionDays = values.int(
                KEY_SCAN_HISTORY_RETENTION_DAYS,
                AppSettings.DEFAULT_SCAN_HISTORY_RETENTION_DAYS,
            ).coerceIn(AppSettings.MIN_RETENTION_DAYS, AppSettings.MAX_RETENTION_DAYS),
            deviceObservationRetentionDays = values.int(
                KEY_OBSERVATION_RETENTION_DAYS,
                AppSettings.DEFAULT_OBSERVATION_RETENTION_DAYS,
            ).coerceIn(AppSettings.MIN_RETENTION_DAYS, AppSettings.MAX_RETENTION_DAYS),
            trafficRetentionDays = values.int(
                KEY_TRAFFIC_RETENTION_DAYS,
                AppSettings.DEFAULT_TRAFFIC_RETENTION_DAYS,
            ).coerceIn(AppSettings.MIN_RETENTION_DAYS, AppSettings.MAX_RETENTION_DAYS),
            diagnosticsRetentionDays = values.int(
                KEY_DIAGNOSTICS_RETENTION_DAYS,
                AppSettings.DEFAULT_DIAGNOSTICS_RETENTION_DAYS,
            ).coerceIn(AppSettings.MIN_RETENTION_DAYS, AppSettings.MAX_RETENTION_DAYS),
            policyAuditRetentionDays = values.int(
                KEY_POLICY_AUDIT_RETENTION_DAYS,
                AppSettings.DEFAULT_POLICY_AUDIT_RETENTION_DAYS,
            ).coerceIn(AppSettings.MIN_RETENTION_DAYS, AppSettings.MAX_RETENTION_DAYS),
            rootModeEnabled = values.boolean(KEY_ROOT_MODE_ENABLED, false),
        )
    }

    private fun AppSettings.toEntities(updatedAt: Long): List<AppSettingsEntity> = listOf(
        entity(KEY_ONBOARDING_COMPLETED, onboardingCompleted.toString(), updatedAt),
        entity(
            KEY_MONITORING_INTERVAL_SECONDS,
            monitoringRefreshIntervalSeconds.toString(),
            updatedAt,
        ),
        entity(
            KEY_SCAN_HISTORY_RETENTION_DAYS,
            scanHistoryRetentionDays.toString(),
            updatedAt,
        ),
        entity(
            KEY_OBSERVATION_RETENTION_DAYS,
            deviceObservationRetentionDays.toString(),
            updatedAt,
        ),
        entity(KEY_TRAFFIC_RETENTION_DAYS, trafficRetentionDays.toString(), updatedAt),
        entity(
            KEY_DIAGNOSTICS_RETENTION_DAYS,
            diagnosticsRetentionDays.toString(),
            updatedAt,
        ),
        entity(
            KEY_POLICY_AUDIT_RETENTION_DAYS,
            policyAuditRetentionDays.toString(),
            updatedAt,
        ),
        entity(KEY_ROOT_MODE_ENABLED, rootModeEnabled.toString(), updatedAt),
    )

    private fun entity(key: String, value: String, updatedAt: Long) = AppSettingsEntity(
        settingKey = key,
        settingValue = value,
        updatedAt = updatedAt,
    )

    private fun Map<String, String?>.boolean(key: String, fallback: Boolean): Boolean =
        this[key]?.toBooleanStrictOrNull() ?: fallback

    private fun Map<String, String?>.int(key: String, fallback: Int): Int =
        this[key]?.toIntOrNull() ?: fallback

    private companion object {
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_MONITORING_INTERVAL_SECONDS = "monitoring_interval_seconds"
        const val KEY_SCAN_HISTORY_RETENTION_DAYS = "scan_history_retention_days"
        const val KEY_OBSERVATION_RETENTION_DAYS = "device_observation_retention_days"
        const val KEY_TRAFFIC_RETENTION_DAYS = "traffic_retention_days"
        const val KEY_DIAGNOSTICS_RETENTION_DAYS = "diagnostics_retention_days"
        const val KEY_POLICY_AUDIT_RETENTION_DAYS = "policy_audit_retention_days"
        const val KEY_ROOT_MODE_ENABLED = "root_mode_enabled"
    }
}
