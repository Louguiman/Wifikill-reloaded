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
import androidx.compose.ui.res.stringResource
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
    onOpenMonitoring: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
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
        onOpenMonitoring = onOpenMonitoring,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenSettings = onOpenSettings,
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
    onOpenMonitoring: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.dashboard_subtitle),
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
            Text(
                if (state.deviceCount == 0) {
                    stringResource(R.string.dashboard_discover)
                } else {
                    stringResource(R.string.dashboard_scan_again)
                },
            )
        }

        OutlinedButton(
            onClick = onOpenDevices,
            enabled = state.deviceCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.dashboard_view_devices, state.deviceCount))
        }

        OutlinedButton(
            onClick = onOpenRouters,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.dashboard_manage_routers))
        }

        OutlinedButton(
            onClick = onOpenAccess,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.dashboard_access_policies))
        }

        OutlinedButton(
            onClick = onOpenMonitoring,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.dashboard_monitoring))
        }

        OutlinedButton(
            onClick = onOpenDiagnostics,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.dashboard_diagnostics))
        }

        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.dashboard_settings))
        }

        if (!state.isLoading && state.network == null) {
            EmptyState(
                title = stringResource(R.string.dashboard_empty_title),
                message = stringResource(R.string.dashboard_empty_message),
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
                text = stringResource(R.string.dashboard_current_network),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = network.displayName(),
                style = MaterialTheme.typography.titleLarge,
            )
            if (network != null) {
                Text(
                    text = stringResource(
                    R.string.dashboard_network_summary,
                    network.networkType.label(),
                    network.subnet ?: stringResource(R.string.dashboard_subnet_unknown),
                ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.dashboard_gateway,
                        network.gatewayIp ?: stringResource(R.string.dashboard_unknown),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_device_count, deviceCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (network.isMetered) {
                    Text(
                        text = stringResource(R.string.dashboard_metered),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.dashboard_not_connected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NetworkContext?.displayName(): String {
    val context = this ?: return stringResource(R.string.dashboard_no_network)
    return context.ssid?.takeIf { it.isNotBlank() }
        ?: context.interfaceName?.takeIf { it.isNotBlank() }
        ?: context.gatewayIp?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.dashboard_network_at, it) }
        ?: stringResource(R.string.dashboard_unknown_network)
}

@Composable
private fun NetworkType.label(): String = stringResource(
    when (this) {
        NetworkType.WIFI -> R.string.network_type_wifi
        NetworkType.ETHERNET -> R.string.network_type_ethernet
        NetworkType.CELLULAR -> R.string.network_type_cellular
        NetworkType.OTHER -> R.string.network_type_other
        NetworkType.UNKNOWN -> R.string.network_type_unknown
    },
)

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
            onOpenMonitoring = {},
            onOpenDiagnostics = {},
            onOpenSettings = {},
        )
    }
}
