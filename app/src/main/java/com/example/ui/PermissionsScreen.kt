package com.example.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun PermissionsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Trigger state check updates
    var hasPhoneState by remember { mutableStateOf(false) }
    var hasCallLog by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasNotificationAccess by remember { mutableStateOf(false) }

    fun checkAllPermissions() {
        hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        hasCallLog = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        // Check if platform push is fully supported
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Check if Notification Listener is enabled (for WhatsApp notifications parsing)
        val enabledPackages = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        hasNotificationAccess = enabledPackages != null && enabledPackages.contains(context.packageName)
    }

    // Check once at launch and on any resume
    LaunchedEffect(Unit) {
        checkAllPermissions()
    }

    // Launchers for system permissions
    val phonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPhoneState = granted
    }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCallLog = granted
    }

    val pushNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Device Permissions Onboarding",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "To monitor call records and WhatsApp activities continuously and synchronize them with your CRM, this backup broker requires specific permissions. Please grant each of the items below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 1. Phone State Permission Card
        PermissionItemCard(
            title = "Read Phone State",
            description = "Needed to detect real-time incoming and outgoing phone calls and trigger cellular status handlers.",
            isGranted = hasPhoneState,
            onRequest = {
                phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
        )

        // 2. Call Log Permission Card
        PermissionItemCard(
            title = "Read Call Logs",
            description = "Crucial to resolve precise call duration, caller contact display names, and exact miss/answer results.",
            isGranted = hasCallLog,
            onRequest = {
                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
        )

        // 3. Notification Interceptor Access (for WhatsApp monitoring)
        PermissionItemCard(
            title = "Notification Interceptor Access",
            description = "Mandatory for WhatsApp call monitoring. Enables parsing of caller alerts to log VoIP activities.",
            isGranted = hasNotificationAccess,
            onRequest = {
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    context.startActivity(intent)
                }
            }
        )

        // 4. Push notifications permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionItemCard(
                title = "Push Notifications",
                description = "Required to display the steady Foreground Service broker banner and dispatch sync alerts.",
                isGranted = hasNotificationPermission,
                onRequest = {
                    pushNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic status check footer
        val allGranted = hasPhoneState && hasCallLog && hasNotificationPermission && hasNotificationAccess
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (allGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (allGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (allGranted) "All Systems Configured!" else "Setup Incomplete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (allGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (allGranted) 
                            "CallTrack is fully authorized and ready to record events to the CRM." 
                            else 
                            "Please grant missing permissions above. Some modules remain deactivated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allGranted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        Button(
            onClick = { checkAllPermissions() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Re-verify Statuses")
        }
    }
}

@Composable
fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isGranted) MaterialTheme.colorScheme.primary else Color(0xFFFF9800),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isGranted) MaterialTheme.colorScheme.primary else Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.widthIn(min = 100.dp)
            ) {
                Text(
                    text = if (isGranted) "Granted" else "Grant",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
