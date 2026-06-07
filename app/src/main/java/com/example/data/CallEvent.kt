package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_events")
data class CallEvent(
    @PrimaryKey val eventId: String,
    val deviceId: String,
    val agentName: String,
    val source: String,           // PHONE, WHATSAPP, whatsapp, whatsapp_business, cellular
    val contactName: String,
    val phoneNumber: String,
    val status: String,           // INCOMING, ANSWERED, MISSED, OUTGOING, ENDED, or CRM-specific values
    val timestamp: Long,
    val duration: Long,           // Duration in seconds (0 if not available)
    val isSynced: Boolean = false,
    val direction: String = "unknown",
    val appPackage: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val durationSeconds: Long = 0L,
    val capturedAt: String = "",
    val notificationTitle: String? = null,
    val notificationText: String? = null,
    val notes: String? = null
)
