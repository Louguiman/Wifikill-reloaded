package com.sankare.wifikill.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankare.wifikill.core.domain.model.AppSettings
import com.sankare.wifikill.core.domain.model.RetentionKind

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    SettingsContent(
        state = state,
        onBack = onBack,
        onSetInterval = viewModel::setMonitoringInterval,
        onSetRetention = viewModel::setRetention,
        onSetRootMode = viewModel::setRootModeEnabled,
        onDeleteLocalData = { showDeleteConfirmation = true },
        onDismissFeedback = viewModel::clearFeedback,
        modifier = modifier,
    )

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteLocalData()
                    },
                ) {
                    Text(stringResource(R.string.settings_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetRetention: (RetentionKind, Int) -> Unit,
    onSetRootMode: (Boolean) -> Unit,
    onDeleteLocalData: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.settings_back))
                }
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        if (state.isLoading) {
            item { Text(stringResource(R.string.settings_loading)) }
            return@LazyColumn
        }

        state.message?.let { message ->
            item {
                FeedbackCard(
                    text = feedbackText(message),
                    isError = false,
                    onDismiss = onDismissFeedback,
                )
            }
        }
        state.error?.let { error ->
            item {
                FeedbackCard(
                    text = feedbackText(error),
                    isError = true,
                    onDismiss = onDismissFeedback,
                )
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_root_title)) {
                Text(
                    text = stringResource(R.string.settings_root_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (state.rootAvailable) {
                                null -> stringResource(R.string.settings_root_checking)
                                true -> stringResource(R.string.settings_root_available)
                                false -> stringResource(R.string.settings_root_unavailable)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = state.settings.rootModeEnabled,
                        onCheckedChange = onSetRootMode,
                        enabled = state.rootAvailable == true || state.settings.rootModeEnabled,
                    )
                }
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_monitoring_title)) {
                Text(
                    text = stringResource(R.string.settings_monitoring_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IntervalControl(
                    value = state.settings.monitoringRefreshIntervalSeconds,
                    onChange = onSetInterval,
                )
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_retention_title)) {
                Text(
                    text = stringResource(R.string.settings_retention_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RetentionRow(
                    label = stringResource(R.string.settings_retention_scan),
                    days = state.settings.scanHistoryRetentionDays,
                    onSelect = { onSetRetention(RetentionKind.SCAN_HISTORY, it) },
                )
                RetentionRow(
                    label = stringResource(R.string.settings_retention_observations),
                    days = state.settings.deviceObservationRetentionDays,
                    onSelect = { onSetRetention(RetentionKind.DEVICE_OBSERVATIONS, it) },
                )
                RetentionRow(
                    label = stringResource(R.string.settings_retention_traffic),
                    days = state.settings.trafficRetentionDays,
                    onSelect = { onSetRetention(RetentionKind.TRAFFIC, it) },
                )
                RetentionRow(
                    label = stringResource(R.string.settings_retention_diagnostics),
                    days = state.settings.diagnosticsRetentionDays,
                    onSelect = { onSetRetention(RetentionKind.DIAGNOSTICS, it) },
                )
                RetentionRow(
                    label = stringResource(R.string.settings_retention_policy),
                    days = state.settings.policyAuditRetentionDays,
                    onSelect = { onSetRetention(RetentionKind.POLICY_AUDIT, it) },
                )
            }
        }

        item {
            SettingsCard(title = stringResource(R.string.settings_delete_title)) {
                Text(
                    text = stringResource(R.string.settings_delete_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onDeleteLocalData,
                    enabled = !state.isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_delete_action))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun IntervalControl(value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_monitoring_interval),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = { onChange(value - 1) },
            enabled = value > AppSettings.MIN_MONITORING_INTERVAL_SECONDS,
        ) {
            Text("−")
        }
        Text(
            text = stringResource(R.string.settings_seconds, value),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        OutlinedButton(
            onClick = { onChange(value + 1) },
            enabled = value < AppSettings.MAX_MONITORING_INTERVAL_SECONDS,
        ) {
            Text("+")
        }
    }
}

@Composable
private fun RetentionRow(
    label: String,
    days: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.settings_days, days),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.settings_change))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                RETENTION_PRESETS.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_days, preset)) },
                        onClick = {
                            expanded = false
                            onSelect(preset)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(
    text: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_dismiss))
            }
        }
    }
}

@Composable
private fun feedbackText(code: String): String = when (code) {
    "LOCAL_DATA_DELETED" -> stringResource(R.string.settings_local_data_deleted)
    "ROOT_UNAVAILABLE" -> stringResource(R.string.settings_root_unavailable_error)
    else -> code
}

private val RETENTION_PRESETS = listOf(7, 14, 30, 60, 90, 180, 365)
