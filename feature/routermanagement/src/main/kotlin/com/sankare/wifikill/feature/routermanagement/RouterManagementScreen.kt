package com.sankare.wifikill.feature.routermanagement

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sankare.wifikill.core.designsystem.component.EmptyState
import com.sankare.wifikill.core.domain.model.Router
import com.sankare.wifikill.core.domain.model.RouterConnectionStatus
import com.sankare.wifikill.core.domain.model.RouterProtocol

@Composable
fun RouterManagementScreen(
    onBack: () -> Unit,
    onAddRouter: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouterManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                text = "Routers",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onAddRouter) {
                Text("Add")
            }
        }

        Text(
            text = state.network?.let { it.ssid ?: it.interfaceName } ?: "No authorized network",
            style = MaterialTheme.typography.bodyMedium,
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
            state.isLoading -> Text("Loading routers…")
            state.routers.isEmpty() -> EmptyState(
                title = "No routers yet",
                message = "Add a router you are authorized to administer.",
                modifier = Modifier.fillMaxWidth(),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.routers, key = { it.id }) { router ->
                    RouterCard(
                        router = router,
                        busy = state.busyRouterId == router.id,
                        onConnect = { viewModel.connect(router.id) },
                        onDisconnect = { viewModel.disconnect(router.id) },
                        onForget = { viewModel.forget(router.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RouterCard(
    router: Router,
    busy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = router.name, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = statusLabel(router.connectionStatus),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (router.connectionStatus == RouterConnectionStatus.CONNECTED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = "${router.host}:${router.port} • ${router.protocol.label} • ${router.adapterType.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            router.manufacturer?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = if (router.capabilities.isEmpty()) {
                    "No verified capabilities"
                } else {
                    "Verified: " + router.capabilities.joinToString { it.name }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) {
                    Text("Working…", style = MaterialTheme.typography.bodySmall)
                } else if (router.connectionStatus == RouterConnectionStatus.CONNECTED) {
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                } else {
                    Button(onClick = onConnect) { Text("Connect") }
                }
                OutlinedButton(onClick = onForget) { Text("Forget") }
            }
        }
    }
}

@Composable
fun AddRouterScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddRouterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

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
                text = "Add router",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Text(
            text = state.network?.let { it.ssid ?: it.interfaceName } ?: "No authorized network",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.form.name,
            onValueChange = viewModel::updateName,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.form.host,
            onValueChange = viewModel::updateHost,
            label = { Text("Host or IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.form.port,
            onValueChange = viewModel::updatePort,
            label = { Text("Port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtocolOption(
                selected = state.form.protocol == RouterProtocol.HTTPS,
                label = "HTTPS",
                onClick = { viewModel.updateProtocol(RouterProtocol.HTTPS) },
            )
            ProtocolOption(
                selected = state.form.protocol == RouterProtocol.HTTP,
                label = "HTTP",
                onClick = { viewModel.updateProtocol(RouterProtocol.HTTP) },
            )
        }
        OutlinedTextField(
            value = state.form.username,
            onValueChange = viewModel::updateUsername,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.form.password,
            onValueChange = viewModel::updatePassword,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSaving) "Saving…" else "Save and connect")
        }
    }
}

@Composable
private fun ProtocolOption(selected: Boolean, label: String, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

private fun statusLabel(status: RouterConnectionStatus): String = when (status) {
    RouterConnectionStatus.UNKNOWN -> "Unknown"
    RouterConnectionStatus.CONNECTING -> "Connecting"
    RouterConnectionStatus.CONNECTED -> "Connected"
    RouterConnectionStatus.AUTHENTICATION_FAILED -> "Authentication failed"
    RouterConnectionStatus.UNREACHABLE -> "Unreachable"
    RouterConnectionStatus.DISCONNECTED -> "Disconnected"
}

private val RouterProtocol.label: String
    get() = when (this) {
        RouterProtocol.HTTP -> "HTTP"
        RouterProtocol.HTTPS -> "HTTPS"
        RouterProtocol.ROUTER_API -> "Router API"
        RouterProtocol.UNKNOWN -> "Unknown"
    }
