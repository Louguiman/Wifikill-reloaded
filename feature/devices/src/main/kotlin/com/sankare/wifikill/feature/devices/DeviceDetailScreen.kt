package com.sankare.wifikill.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankare.wifikill.core.designsystem.component.EmptyState
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import java.time.Instant

@Composable
fun DeviceDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    DeviceDetailContent(
        state = state,
        errorMessage = errorMessage,
        onBack = onBack,
        onDismissError = viewModel::dismissError,
        onRename = viewModel::rename,
        onSetTrusted = viewModel::setTrusted,
        modifier = modifier,
    )
}

@Composable
private fun DeviceDetailContent(
    state: DeviceDetailUiState,
    errorMessage: String?,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
    onRename: (String?) -> Unit,
    onSetTrusted: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

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
                text = "Device details",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        val device = state.device
        when {
            device == null && state.isLoading -> Text("Loading device…")

            device == null -> EmptyState(
                title = "Device not found",
                message = "This device is no longer in the local database.",
                modifier = Modifier.fillMaxWidth(),
            )

            else -> {
                DeviceHeader(device = device)

                errorMessage?.let { message ->
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Trusted device",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Trust is a local label. It does not change router policy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = device.isTrusted,
                        onCheckedChange = onSetTrusted,
                    )
                }

                OutlinedButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Rename device")
                }

                Text(
                    text = "Observation history (${state.history.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )

                if (state.history.isEmpty()) {
                    EmptyState(
                        title = "No observations",
                        message = "Device history appears after a successful scan.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.history, key = { it.id }) { observation ->
                            ObservationRow(observation)
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            initialName = state.device?.customName.orEmpty(),
            onDismiss = { showRenameDialog = false },
            onConfirm = { name ->
                showRenameDialog = false
                onRename(name)
            },
        )
    }
}

@Composable
private fun DeviceHeader(device: Device) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = device.customName ?: device.hostname ?: "Unknown device",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "${device.deviceType.name} • confidence ${device.identityConfidence.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            device.hostname?.let {
                Text("Hostname: $it", style = MaterialTheme.typography.bodySmall)
            }
            device.manufacturer?.let {
                Text("Manufacturer: $it", style = MaterialTheme.typography.bodySmall)
            }
            device.notes?.let {
                Text("Notes: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ObservationRow(observation: DeviceObservation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = observation.ipAddress ?: "IP unknown",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "MAC ${observation.macAddress ?: "unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${observation.source.name} • ${observation.reachability.name} • " +
                    Instant.ofEpochMilli(observation.observedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename device") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Display name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim().takeIf { it.isNotEmpty() }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
