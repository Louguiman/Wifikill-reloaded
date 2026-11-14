package com.sankare.wifikill.feature.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankare.wifikill.core.designsystem.component.EmptyState
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.NetworkContext

@Composable
fun DiscoveryScreen(
    onBack: () -> Unit,
    onOpenDevice: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiscoveryContent(
        state = state,
        onBack = onBack,
        onStartScan = viewModel::startScan,
        onCancelScan = viewModel::cancelScan,
        onDismissError = viewModel::dismissError,
        onOpenDevice = onOpenDevice,
        modifier = modifier,
    )
}

@Composable
private fun DiscoveryContent(
    state: DiscoveryUiState,
    onBack: () -> Unit,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onDismissError: () -> Unit,
    onOpenDevice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
            Text(
                text = "Network discovery",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        NetworkSummary(state.network)

        if (state.isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.errorMessage?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss")
                    }
                }
            }
        }

        if (state.isScanning) {
            OutlinedButton(onClick = onCancelScan, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel scan")
            }
        } else {
            Button(
                onClick = onStartScan,
                enabled = state.network?.networkId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scan network")
            }
        }

        Text(
            text = "Devices (${state.devices.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (state.devices.isEmpty()) {
            EmptyState(
                title = if (state.isScanning) "Discovering devices" else "No devices found yet",
                message = if (state.isScanning) {
                    "Probing the local subnet. Results appear as hosts respond."
                } else {
                    "Run a scan to discover devices on your authorized network."
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.devices, key = { it.id }) { device ->
                    DeviceRow(device = device, onClick = { onOpenDevice(device.id) })
                }
            }
        }
    }
}

@Composable
private fun NetworkSummary(network: NetworkContext?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = network?.ssid ?: network?.interfaceName ?: "No network",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Subnet ${network?.subnet ?: "unknown"} • Gateway ${network?.gatewayIp ?: "unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceRow(device: Device, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = device.displayName(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "${device.deviceType.name} • confidence ${device.identityConfidence.name}" +
                    if (device.isTrusted) " • trusted" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Device.displayName(): String =
    customName ?: hostname ?: manufacturer ?: "Unknown device"
