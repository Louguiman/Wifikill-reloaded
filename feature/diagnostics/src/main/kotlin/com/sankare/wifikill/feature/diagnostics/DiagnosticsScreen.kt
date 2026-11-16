package com.sankare.wifikill.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankare.wifikill.core.designsystem.component.EmptyState
import com.sankare.wifikill.core.domain.model.DiagnosticResult
import com.sankare.wifikill.core.domain.model.DiagnosticStatus
import com.sankare.wifikill.core.domain.model.DiagnosticType
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.RootOperation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingRootOperation by remember { mutableStateOf<RootOperation?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.diagnostics_back))
            }
            Text(
                text = stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = viewModel::runAllDiagnostics,
                enabled = !state.isBusy && state.network?.networkId != null,
            ) {
                Text(
                    if (state.isRunningAll) {
                        stringResource(R.string.diagnostics_running)
                    } else {
                        stringResource(R.string.diagnostics_run_all)
                    },
                )
            }
        }

        Text(
            text = state.network.displayName(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.diagnostics_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.isLoading -> Text(stringResource(R.string.diagnostics_loading))
            state.network?.networkId == null -> EmptyState(
                title = stringResource(R.string.diagnostics_no_network),
                message = stringResource(R.string.diagnostics_no_network_message),
                modifier = Modifier.fillMaxWidth(),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.progress?.let { progress ->
                    item {
                        Text(
                            text = progress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                state.error?.let { error ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::clearError) {
                                Text(stringResource(R.string.diagnostics_dismiss))
                            }
                        }
                    }
                }
                state.latestResult?.let { result ->
                    item {
                        Text(
                            text = stringResource(R.string.diagnostics_latest_result),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        ResultCard(result = result, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                items(DIAGNOSTIC_TYPES, key = { it.name }) { type ->
                    DiagnosticActionCard(
                        type = type,
                        busy = state.isBusy,
                        running = state.runningType == type,
                        onRun = { viewModel.runDiagnostic(type) },
                    )
                }

                if (state.rootModeEnabled) {
                    item {
                        RootAdvancedDiagnosticsCard(
                            state = state,
                            onRequestOperation = { pendingRootOperation = it },
                            onDismissResult = viewModel::clearRootResult,
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.diagnostics_history),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.history.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.diagnostics_no_results),
                            message = stringResource(
                                R.string.diagnostics_no_results_message,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    items(state.history, key = { it.id }) { result ->
                        ResultCard(result = result)
                    }
                }
            }
        }
    }

    pendingRootOperation?.let { operation ->
        AlertDialog(
            onDismissRequest = { pendingRootOperation = null },
            title = { Text(stringResource(R.string.diagnostics_root_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.diagnostics_root_confirm_message,
                        operation.label(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRootOperation = null
                        viewModel.executeRootOperation(operation)
                    },
                ) {
                    Text(stringResource(R.string.diagnostics_root_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRootOperation = null }) {
                    Text(stringResource(R.string.diagnostics_root_cancel))
                }
            },
        )
    }
}

@Composable
private fun DiagnosticActionCard(
    type: DiagnosticType,
    busy: Boolean,
    running: Boolean,
    onRun: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = type.localizedLabel(),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = type.localizedDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onRun,
                enabled = !busy,
            ) {
                Text(
                    if (running) {
                        stringResource(R.string.diagnostics_running)
                    } else {
                        stringResource(R.string.diagnostics_run)
                    },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticType.localizedLabel(): String = stringResource(
    when (this) {
        DiagnosticType.GATEWAY_REACHABILITY -> R.string.diagnostics_type_gateway
        DiagnosticType.DNS_RESOLUTION -> R.string.diagnostics_type_dns
        DiagnosticType.INTERNET_CONNECTIVITY -> R.string.diagnostics_type_internet
        DiagnosticType.LATENCY -> R.string.diagnostics_type_latency
        DiagnosticType.PACKET_LOSS -> R.string.diagnostics_type_packet_loss
        DiagnosticType.NETWORK_CONFIGURATION ->
            R.string.diagnostics_type_network_configuration
    },
)

@Composable
private fun DiagnosticType.localizedDescription(): String = stringResource(
    when (this) {
        DiagnosticType.GATEWAY_REACHABILITY -> R.string.diagnostics_desc_gateway
        DiagnosticType.DNS_RESOLUTION -> R.string.diagnostics_desc_dns
        DiagnosticType.INTERNET_CONNECTIVITY -> R.string.diagnostics_desc_internet
        DiagnosticType.LATENCY -> R.string.diagnostics_desc_latency
        DiagnosticType.PACKET_LOSS -> R.string.diagnostics_desc_packet_loss
        DiagnosticType.NETWORK_CONFIGURATION ->
            R.string.diagnostics_desc_network_configuration
    },
)

@Composable
private fun RootOperation.label(): String = stringResource(
    when (this) {
        RootOperation.INSPECT_LOCAL_INTERFACE -> R.string.diagnostics_root_inspect_local
        RootOperation.READ_NETWORK_CONFIGURATION -> R.string.diagnostics_root_read_network
    },
)

@Composable
private fun RootAdvancedDiagnosticsCard(
    state: DiagnosticsUiState,
    onRequestOperation: (RootOperation) -> Unit,
    onDismissResult: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_root_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.diagnostics_root_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = when (state.rootAvailable) {
                    null -> stringResource(R.string.diagnostics_root_checking)
                    true -> stringResource(R.string.diagnostics_root_available)
                    false -> stringResource(R.string.diagnostics_root_unavailable)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onRequestOperation(RootOperation.INSPECT_LOCAL_INTERFACE) },
                    enabled = state.rootAvailable == true && !state.rootBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.diagnostics_root_inspect_interfaces))
                }
                OutlinedButton(
                    onClick = { onRequestOperation(RootOperation.READ_NETWORK_CONFIGURATION) },
                    enabled = state.rootAvailable == true && !state.rootBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.diagnostics_root_read_configuration))
                }
            }
            state.rootError?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissResult) {
                        Text(stringResource(R.string.diagnostics_root_dismiss))
                    }
                }
            }
            state.rootResult?.let { result ->
                Text(
                    text = result.output ?: stringResource(R.string.diagnostics_root_no_output),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: DiagnosticResult, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.type.localizedLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = result.status.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(result.status),
                )
            }
            result.target?.let { target ->
                Text(
                    text = stringResource(R.string.diagnostics_target, target),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            result.latencyMs?.let { latency ->
                Text(
                    text = stringResource(R.string.diagnostics_latency, latency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            result.resultMessage?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = formatTimestamp(result.executedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusColor(status: DiagnosticStatus) = when (status) {
    DiagnosticStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    DiagnosticStatus.WARNING -> MaterialTheme.colorScheme.tertiary
    DiagnosticStatus.FAILED -> MaterialTheme.colorScheme.error
    DiagnosticStatus.UNAVAILABLE,
    DiagnosticStatus.RUNNING,
    -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun DiagnosticStatus.label(): String = stringResource(
    when (this) {
        DiagnosticStatus.RUNNING -> R.string.diagnostics_status_running
        DiagnosticStatus.SUCCESS -> R.string.diagnostics_status_success
        DiagnosticStatus.WARNING -> R.string.diagnostics_status_warning
        DiagnosticStatus.FAILED -> R.string.diagnostics_status_failed
        DiagnosticStatus.UNAVAILABLE -> R.string.diagnostics_status_unavailable
    },
)

@Composable
private fun NetworkContext?.displayName(): String {
    val context = this ?: return stringResource(R.string.diagnostics_no_network_name)
    return context.ssid?.takeIf { it.isNotBlank() }
        ?: context.interfaceName?.takeIf { it.isNotBlank() }
        ?: context.gatewayIp?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.diagnostics_network_at, it) }
        ?: stringResource(R.string.diagnostics_unknown_network)
}

private fun formatTimestamp(epochMillis: Long): String =
    TIMESTAMP_FORMATTER.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )

private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm:ss", Locale.getDefault())
