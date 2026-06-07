package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.UserSettings

@Composable
fun SettingsScreen(
    viewModel: CallTrackViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val currentSettings by viewModel.settings.collectAsState()
    val connState by viewModel.connectionState.collectAsState()

    // Internal form states
    var crmBaseUrl by remember { mutableStateOf("") }
    var crmApiKey by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var agentName by remember { mutableStateOf("") }
    var syncIntervalMins by remember { mutableStateOf(15) }

    var isPasswordVisible by remember { mutableStateOf(false) }

    // Synchronize form states on loaded setting preferences
    LaunchedEffect(currentSettings) {
        if (crmBaseUrl.isEmpty() && currentSettings.crmBaseUrl.isNotEmpty()) {
            crmBaseUrl = currentSettings.crmBaseUrl
        }
        if (crmApiKey.isEmpty() && currentSettings.crmApiKey.isNotEmpty()) {
            crmApiKey = currentSettings.crmApiKey
        }
        if (deviceName.isEmpty() && currentSettings.deviceName.isNotEmpty()) {
            deviceName = currentSettings.deviceName
        }
        if (agentName.isEmpty() && currentSettings.agentName.isNotEmpty()) {
            agentName = currentSettings.agentName
        }
        if (syncIntervalMins == 15 && currentSettings.syncIntervalMins != 15) {
            syncIntervalMins = currentSettings.syncIntervalMins
        }
    }

    // Endpoint URL validator checks
    val isUrlValid = remember(crmBaseUrl) {
        crmBaseUrl.trim().startsWith("http://") || crmBaseUrl.trim().startsWith("https://")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CRM Configuration settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 1. Connection settings Section
        Text(
            text = "INTEGRATION INTEGRALS",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = crmBaseUrl,
            onValueChange = { crmBaseUrl = it },
            label = { Text("CRM API Base URL") },
            placeholder = { Text("https://crm.company.com/api") },
            modifier = Modifier.fillMaxWidth(),
            isError = crmBaseUrl.isNotEmpty() && !isUrlValid,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                if (crmBaseUrl.isNotEmpty() && !isUrlValid) {
                    Icon(Icons.Default.Warning, contentDescription = "Invalid URL Scheme", tint = MaterialTheme.colorScheme.error)
                }
            }
        )
        if (crmBaseUrl.isNotEmpty() && !isUrlValid) {
            Text(
                text = "Base URL must begin with http:// or https://",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedTextField(
            value = crmApiKey,
            onValueChange = { crmApiKey = it },
            label = { Text("API Key / Bearer Token") },
            placeholder = { Text("Enter your authorization token") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Text(text = if (isPasswordVisible) "Hide" else "Show")
                }
            },
            singleLine = true
        )

        // 2. Identity info
        Text(
            text = "AGENT & DEVICE IDENTITY",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device Display Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = agentName,
            onValueChange = { agentName = it },
            label = { Text("Sales Agent Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // 3. Sync Presets
        Text(
            text = "SYNC FREQUENCY (MINUTES)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(15, 30, 60, 120).forEach { mins ->
                FilterChip(
                    selected = syncIntervalMins == mins,
                    onClick = { syncIntervalMins = mins },
                    label = { Text("${mins}m") },
                    leadingIcon = if (syncIntervalMins == mins) {
                        { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
        
        Text(
            text = "* Note: WorkManager updates may take up to 15 minutes due to Android battery-saving guidelines.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Connection test result feedbacks
        if (connState != ConnectionState.IDLE) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (connState) {
                        ConnectionState.TESTING -> MaterialTheme.colorScheme.secondaryContainer
                        ConnectionState.SUCCESS -> Color(0xFFE8F5E9)
                        ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (connState) {
                        ConnectionState.TESTING -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("Testing CRM Endpoint connection...", style = MaterialTheme.typography.bodyMedium)
                        }
                        ConnectionState.SUCCESS -> {
                            Icon(Icons.Default.Done, contentDescription = "Success", tint = Color(0xFF2E7D32))
                            Text("Connection Test Success. Endpoints verified!", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2E7D32))
                        }
                        ConnectionState.ERROR -> {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                            Text("Test connection failed. Verify URL and token credentials.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        else -> {}
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.testConnection(crmBaseUrl, crmApiKey) },
                enabled = isUrlValid && connState != ConnectionState.TESTING,
                modifier = Modifier.weight(1.0f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Test Connection")
            }

            Button(
                onClick = {
                    viewModel.saveSettings(
                        context = context,
                        crmBaseUrl = crmBaseUrl,
                        crmApiKey = crmApiKey,
                        deviceName = deviceName,
                        agentName = agentName,
                        syncIntervalMins = syncIntervalMins
                    )
                },
                enabled = isUrlValid && crmBaseUrl.isNotEmpty() && deviceName.isNotEmpty() && agentName.isNotEmpty(),
                modifier = Modifier.weight(1.4f).height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Settings")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostic Clear Logs Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text("Delete local call logs held in cache. This does not modify database endpoints in the cloud.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { viewModel.clearDatabase() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Local Logs Cache")
                }
            }
        }
    }
}
