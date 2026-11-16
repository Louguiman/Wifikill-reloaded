package com.sankare.wifikill.feature.monitoring

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankare.wifikill.core.designsystem.component.EmptyState
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.TrafficSample
import com.sankare.wifikill.core.domain.model.TrafficSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonitoringScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonitoringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.startMonitoring()
        onDispose { viewModel.pauseMonitoring() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.monitoring_back))
            }
            Text(
                text = stringResource(R.string.monitoring_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Text(
            text = state.network.displayName(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.monitoring_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.isLoading -> Text(stringResource(R.string.monitoring_loading))
            state.network?.networkId == null -> EmptyState(
                title = stringResource(R.string.monitoring_no_network),
                message = stringResource(R.string.monitoring_no_network_message),
                modifier = Modifier.fillMaxWidth(),
            )
            else -> {
                LatestSampleCard(
                    sample = state.latestSample,
                    isRefreshing = state.isRefreshing,
                )

                state.unavailableReason?.let { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::clearUnavailable) {
                            Text(stringResource(R.string.monitoring_dismiss))
                        }
                    }
                }
                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            if (state.isMonitoring) {
                                viewModel.pauseMonitoring()
                            } else {
                                viewModel.startMonitoring()
                            }
                        },
                    ) {
                        Text(
                            if (state.isMonitoring) {
                                stringResource(R.string.monitoring_pause)
                            } else {
                                stringResource(R.string.monitoring_start)
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::refreshNow,
                        enabled = !state.isRefreshing,
                    ) {
                        Text(
                            if (state.isRefreshing) {
                                stringResource(R.string.monitoring_refreshing)
                            } else {
                                stringResource(R.string.monitoring_refresh_now)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (state.isMonitoring) {
                            stringResource(
                                R.string.monitoring_live,
                                state.refreshIntervalSeconds,
                            )
                        } else {
                            stringResource(R.string.monitoring_paused)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isMonitoring) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Text(
                    text = stringResource(R.string.monitoring_recent_samples),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (state.samples.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.monitoring_no_samples),
                        message = stringResource(R.string.monitoring_no_samples_message),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.samples.take(MAX_VISIBLE_SAMPLES), key = { it.id }) { sample ->
                            SampleCard(sample = sample)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LatestSampleCard(sample: TrafficSample?, isRefreshing: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.monitoring_latest_sample),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (isRefreshing) {
                    Text(
                        text = stringResource(R.string.monitoring_reading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (sample == null) {
                Text(
                    text = stringResource(R.string.monitoring_no_measurement),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.monitoring_received,
                        formatBytes(sample.bytesReceived),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.monitoring_sent,
                        formatBytes(sample.bytesSent),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.monitoring_source,
                        sample.source.localizedLabel(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.monitoring_sampled,
                        formatTimestamp(sample.sampledAt),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SampleCard(sample: TrafficSample) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimestamp(sample.sampledAt),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = sample.source.localizedLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.monitoring_sample_summary,
                    formatBytes(sample.bytesReceived),
                    formatBytes(sample.bytesSent),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NetworkContext?.displayName(): String {
    val context = this ?: return stringResource(R.string.monitoring_no_network_name)
    return context.ssid?.takeIf { it.isNotBlank() }
        ?: context.interfaceName?.takeIf { it.isNotBlank() }
        ?: context.gatewayIp?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.monitoring_network_at, it) }
        ?: stringResource(R.string.monitoring_unknown_network)
}

@Composable
private fun TrafficSource.localizedLabel(): String = stringResource(
    when (this) {
        TrafficSource.ROUTER_TELEMETRY -> R.string.monitoring_source_router
        TrafficSource.ANDROID_LOCAL -> R.string.monitoring_source_android
        TrafficSource.UNKNOWN -> R.string.monitoring_source_unknown
    },
)

@Composable
private fun formatBytes(bytes: Long?): String {
    if (bytes == null) return stringResource(R.string.monitoring_unavailable)
    if (bytes < UNIT) return "$bytes B"
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= UNIT && unitIndex < UNITS.lastIndex - 1) {
        value /= UNIT
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, UNITS[unitIndex + 1])
}

private fun formatTimestamp(epochMillis: Long): String =
    TIMESTAMP_FORMATTER.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
    )

private const val UNIT = 1024.0
private const val MAX_VISIBLE_SAMPLES = 20
private val UNITS = listOf("B", "KB", "MB", "GB", "TB")
private val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, HH:mm:ss", Locale.getDefault())
