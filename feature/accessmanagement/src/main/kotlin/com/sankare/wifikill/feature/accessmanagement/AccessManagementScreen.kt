package com.sankare.wifikill.feature.accessmanagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.sankare.wifikill.core.domain.model.AccessPolicy
import com.sankare.wifikill.core.domain.model.AccessPolicyAction
import com.sankare.wifikill.core.domain.model.AccessPolicyStatus
import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.Router

@Composable
fun AccessManagementScreen(
    onBack: () -> Unit,
    onOpenRouters: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccessManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val devicesById = state.devices.associateBy { it.id }

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
                text = "Access",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenRouters) {
                Text("Routers")
            }
        }

        Text(
            text = "Policy changes apply through the router's authenticated API and are " +
                "confirmed by reading the router state back.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        when {
            state.isLoading -> Text("Loading access state…")
            state.routers.isEmpty() -> EmptyState(
                title = "No authorized router",
                message = "Add a router you administer before changing access policies.",
                modifier = Modifier.fillMaxWidth(),
            )
            else -> {
                RouterSelection(
                    routers = state.routers,
                    selectedRouterId = state.selectedRouterId,
                    onSelect = viewModel::selectRouter,
                )
                DeviceSelection(
                    devices = state.devices,
                    selectedDeviceId = state.selectedDeviceId,
                    onSelect = viewModel::selectDevice,
                )
                PolicyComposer(
                    action = state.pendingAction,
                    reason = state.reason,
                    enabled = !state.isApplying && state.selectedDeviceId != null,
                    isApplying = state.isApplying,
                    onAction = viewModel::setAction,
                    onReason = viewModel::updateReason,
                    onApply = viewModel::apply,
                )
                Text(
                    text = "Policies on this router",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (state.policies.isEmpty()) {
                    EmptyState(
                        title = "No policies",
                        message = "Access policies appear here once applied.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.policies, key = { it.id }) { policy ->
                            PolicyCard(
                                policy = policy,
                                device = devicesById[policy.deviceId],
                                busy = state.busyPolicyId == policy.id,
                                onRemove = { viewModel.remove(policy.id) },
                                onVerify = { viewModel.verify(policy.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouterSelection(
    routers: List<Router>,
    selectedRouterId: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Router",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(routers, key = { it.id }) { router ->
                FilterChip(
                    selected = router.id == selectedRouterId,
                    onClick = { onSelect(router.id) },
                    label = { Text(router.name) },
                )
            }
        }
    }
}

@Composable
private fun DeviceSelection(
    devices: List<Device>,
    selectedDeviceId: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Device",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (devices.isEmpty()) {
            Text(
                text = "No discovered devices on this network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.id }) { device ->
                    FilterChip(
                        selected = device.id == selectedDeviceId,
                        onClick = { onSelect(device.id) },
                        label = { Text(device.displayName(), maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyComposer(
    action: AccessPolicyAction,
    reason: String,
    enabled: Boolean,
    isApplying: Boolean,
    onAction: (AccessPolicyAction) -> Unit,
    onReason: (String) -> Unit,
    onApply: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = action == AccessPolicyAction.BLOCK_INTERNET,
                    onClick = { onAction(AccessPolicyAction.BLOCK_INTERNET) },
                    label = { Text("Block internet") },
                )
                FilterChip(
                    selected = action == AccessPolicyAction.ALLOW_INTERNET,
                    onClick = { onAction(AccessPolicyAction.ALLOW_INTERNET) },
                    label = { Text("Allow internet") },
                )
            }
            OutlinedTextField(
                value = reason,
                onValueChange = onReason,
                label = { Text("Reason (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onApply,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        isApplying -> "Verifying…"
                        action == AccessPolicyAction.BLOCK_INTERNET -> "Block and verify"
                        else -> "Allow and verify"
                    },
                )
            }
        }
    }
}

@Composable
private fun PolicyCard(
    policy: AccessPolicy,
    device: Device?,
    busy: Boolean,
    onRemove: () -> Unit,
    onVerify: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
                    text = device?.displayName() ?: "Unknown device",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = policy.status.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(policy.status),
                )
            }
            Text(
                text = "Action: ${policy.action.label()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            policy.reason?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            if (policy.status == AccessPolicyStatus.ACTIVE ||
                policy.status == AccessPolicyStatus.UNKNOWN ||
                policy.status == AccessPolicyStatus.FAILED
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) {
                        Text("Working…", style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedButton(onClick = onVerify) { Text("Verify") }
                        OutlinedButton(onClick = onRemove) { Text("Remove") }
                    }
                }
            }
        }
    }
}

private fun Device.displayName(): String = customName?.takeIf { it.isNotBlank() }
    ?: hostname?.takeIf { it.isNotBlank() }
    ?: "Device ${id.take(8)}"

private fun AccessPolicyAction.label(): String = when (this) {
    AccessPolicyAction.BLOCK_INTERNET -> "Block internet"
    AccessPolicyAction.ALLOW_INTERNET -> "Allow internet"
}

private fun AccessPolicyStatus.label(): String = when (this) {
    AccessPolicyStatus.PENDING -> "Pending"
    AccessPolicyStatus.APPLYING -> "Applying"
    AccessPolicyStatus.ACTIVE -> "Active"
    AccessPolicyStatus.REMOVING -> "Removing"
    AccessPolicyStatus.REMOVED -> "Removed"
    AccessPolicyStatus.FAILED -> "Failed"
    AccessPolicyStatus.UNKNOWN -> "Unverified"
}

@Composable
private fun statusColor(status: AccessPolicyStatus) = when (status) {
    AccessPolicyStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    AccessPolicyStatus.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
