package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CallEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: CallTrackViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val settings by viewModel.settings.collectAsState()
    val events by viewModel.recentEvents.collectAsState()
    val pendingCount by viewModel.pendingSyncCount.collectAsState()
    val eventsToday by viewModel.eventsTodayCount.collectAsState()
    val lastSync by viewModel.lastSyncTime.collectAsState()
    val lastEventRecord by viewModel.lastEvent.collectAsState()

    val isMonitoring = settings.monitoringEnabled

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. ACTIVE FOREGROUND STATUS CARD
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isMonitoring) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                    text = "Monitoring Status",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isMonitoring) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Glowing Pulse Indicator
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                )
                                Text(
                                    text = if (isMonitoring) "Active" else "Inactive",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMonitoring) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    if (isMonitoring) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Divider(color = (if (isMonitoring) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.08f))

                    // Dynamic Toggle Button
                    Button(
                        onClick = { viewModel.toggleMonitoring(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (isMonitoring) "Stop Monitoring" else "Start Monitoring",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 2. LIVE CRM COUPLING CARD
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "CRM SYNC HEALTH",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Agent In Charge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(settings.agentName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Target CRM Connection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = settings.crmBaseUrl.take(28) + if (settings.crmBaseUrl.length > 28) "..." else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Unique Device ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(settings.deviceId))
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = settings.deviceId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("(Copy)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Last Synced Activity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(lastSync, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 3. STATISTICAL DASHBOARD CELLS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Events Today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = eventsToday.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Queue Cache",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            if (pendingCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync Queue",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { viewModel.forceSyncNow() }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = pendingCount.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (pendingCount == 0) {
                                Text(
                                    text = "SYNCED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // LAST RECORD CARD MINI
        if (lastEventRecord != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "LATEST CAPTURE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${lastEventRecord!!.source} Call with ${lastEventRecord!!.contactName.ifEmpty { lastEventRecord!!.phoneNumber }} | Status: ${lastEventRecord!!.status}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 4. ACTIVITY HEADER Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent CRM Feed Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (pendingCount > 0) {
                    TextButton(onClick = { viewModel.forceSyncNow() }) {
                        Text("Force Sync Cache")
                    }
                }
            }
        }

        // 5. ACTIVITY DYNAMIC REVIEWS
        if (events.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No recorded call events yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Toggle monitoring, confirm permissions, and call details will sync here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        } else {
            items(events) { event ->
                CallHistoryItem(event = event)
            }
        }
    }
}

@Composable
fun CallHistoryItem(event: CallEvent) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss yyyy-MM-dd", Locale.getDefault()) }
    val timeString = remember(event.timestamp) { formatter.format(Date(event.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Source Badging
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (event.source == "WHATSAPP") Color(0xFFE8F5E9) else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (event.source == "WHATSAPP") {
                        // Custom WA symbol fallback in text
                        Text(
                            text = "WA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Cellular Voice",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Core call specs
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.contactName.ifEmpty { event.phoneNumber },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (event.contactName.isNotEmpty()) event.phoneNumber else "Volatile Log",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Sync alignment and call status indicators
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (event.status) {
                                "ANSWERED" -> Color(0xFFE8F5E9)
                                "INCOMING" -> Color(0xFFE1F5FE)
                                "OUTGOING" -> Color(0xFFFFF3E0)
                                "MISSED" -> Color(0xFFFFEBEE)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = event.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (event.status) {
                            "ANSWERED" -> Color(0xFF2E7D32)
                            "INCOMING" -> Color(0xFF0288D1)
                            "OUTGOING" -> Color(0xFFF57C00)
                            "MISSED" -> Color(0xFFC62828)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Cache vs cloud state
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (event.isSynced) Color(0xFF4CAF50) else Color(0xFFFF9800))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (event.isSynced) "Synced" else "Cached",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (event.isSynced) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (event.duration > 0L) {
                    Text(
                        text = "Duration: ${event.duration}s",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
