package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_events")
data class CallEvent(
    @PrimaryKey val eventId: String,
    val deviceId: String,
    val agentName: String,
    val source: String,           // PHONE, WHATSAPP
    val contactName: String,
    val phoneNumber: String,
    val status: String,           // INCOMING, ANSWERED, MISSED, OUTGOING, ENDED
    val timestamp: Long,
    val duration: Long,           // Duration in seconds (0 if not available)
    val isSynced: Boolean = false
)
