package com.sankare.wifikill.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankare.wifikill.core.designsystem.component.EmptyState
import com.sankare.wifikill.core.designsystem.theme.WiFiKillTheme
import com.sankare.wifikill.core.domain.model.NetworkContext
import com.sankare.wifikill.core.domain.model.NetworkType

@Composable
fun DashboardScreen(
    onOpenDiscovery: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenRouters: () -> Unit,
    onOpenAccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onOpenDiscovery = onOpenDiscovery,
        onOpenDevices = onOpenDevices,
        onOpenRouters = onOpenRouters,
        onOpenAccess = onOpenAccess,
        modifier = modifier,
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onOpenDiscovery: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenRouters: () -> Unit,
    onOpenAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "WiFiKill Reloaded",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Network visibility. Device intelligence. Access management.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NetworkCard(
            network = state.network,
            deviceCount = state.deviceCount,
            modifier = Modifier.padding(top = 16.dp),
        )

        Button(
            onClick = onOpenDiscovery,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.deviceCount == 0) "Run network discovery" else "Scan again")
        }

        OutlinedButton(
            onClick = onOpenDevices,
            enabled = state.deviceCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("View devices (${state.deviceCount})")
        }

        OutlinedButton(
            onClick = onOpenRouters,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Manage routers")
        }

        OutlinedButton(
            onClick = onOpenAccess,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Access policies")
        }

        if (!state.isLoading && state.network == null) {
            EmptyState(
                title = "No authorized network detected",
                message = "Connect to the Wi-Fi or Ethernet network you administer, then scan.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NetworkCard(
    network: NetworkContext?,
    deviceCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Current network",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = network.displayName(),
                style = MaterialTheme.typography.titleLarge,
            )
            if (network != null) {
                Text(
                    text = "${network.networkType.label()} • ${network.subnet ?: "subnet unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Gateway ${network.gatewayIp ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Discovered devices: $deviceCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (network.isMetered) {
                    Text(
                        text = "Metered connection",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else {
                Text(
                    text = "Not connected to an administrable network",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun NetworkContext?.displayName(): String {
    val context = this ?: return "No network"
    return context.ssid?.takeIf { it.isNotBlank() }
        ?: context.interfaceName?.takeIf { it.isNotBlank() }
        ?: context.gatewayIp?.takeIf { it.isNotBlank() }?.let { "Network at $it" }
        ?: "Unknown network"
}

private fun NetworkType.label(): String = when (this) {
    NetworkType.WIFI -> "Wi-Fi"
    NetworkType.ETHERNET -> "Ethernet"
    NetworkType.CELLULAR -> "Cellular"
    NetworkType.OTHER -> "Other"
    NetworkType.UNKNOWN -> "Unknown"
}

@Preview
@Composable
private fun DashboardScreenPreview() {
    WiFiKillTheme(darkTheme = true) {
        DashboardContent(
            state = DashboardUiState(
                network = NetworkContext(
                    networkId = "preview",
                    ssid = "HomeLab",
                    bssid = null,
                    gatewayIp = "192.168.1.1",
                    localIp = "192.168.1.42",
                    subnet = "192.168.1.0/24",
                    interfaceName = "wlan0",
                    networkType = NetworkType.WIFI,
                    isMetered = false,
                    isValidated = true,
                ),
                deviceCount = 7,
                isLoading = false,
            ),
            onOpenDiscovery = {},
            onOpenDevices = {},
            onOpenRouters = {},
            onOpenAccess = {},
        )
    }
}
