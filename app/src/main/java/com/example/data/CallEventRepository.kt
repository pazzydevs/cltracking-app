package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CallEventRepository(
    private val callEventDao: CallEventDao,
    private val settingsManager: SettingsManager,
    private val crmClient: CrmClient
) {

    private val tag = "CallEventRepository"

    val allEvents: Flow<List<CallEvent>> = callEventDao.getAllEvents()

    fun getEventsTodayCount(): Flow<Int> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return callEventDao.countEventsToday(calendar.timeInMillis)
    }

    val pendingSyncCount: Flow<Int> = callEventDao.countPendingSync()

    val lastEvent: Flow<CallEvent?> = callEventDao.getLastEvent()

    // Save and attempt to synchronize event in real time
    suspend fun recordEvent(
        source: String,
        status: String,
        phoneNumber: String,
        contactName: String,
        duration: Long,
        timestamp: Long = System.currentTimeMillis(),
        
        // Optional parameters to override defaults
        direction: String? = null,
        appPackage: String? = null,
        startedAt: String? = null,
        endedAt: String? = null,
        durationSeconds: Long? = null,
        capturedAt: String? = null,
        notificationTitle: String? = null,
        notificationText: String? = null,
        notes: String? = null
    ): Pair<CallEvent, Boolean> = withContext(Dispatchers.IO) {
        val settings = settingsManager.settingsFlow.first()

        // Distinguish cellular, whatsapp, and whatsapp business
        val mappedSource = when {
            source.equals("phone", ignoreCase = true) || source.equals("cellular", ignoreCase = true) || source.equals("PHONE", ignoreCase = true) -> "cellular"
            source.equals("whatsapp_business", ignoreCase = true) || appPackage == "com.whatsapp.w4b" -> "whatsapp_business"
            source.equals("whatsapp", ignoreCase = true) || appPackage == "com.whatsapp" || source.equals("WHATSAPP", ignoreCase = true) -> "whatsapp"
            else -> source.lowercase()
        }

        // Map status and direction if they are not passed
        val finalDirection = direction ?: when (status.uppercase()) {
            "INCOMING", "RINGING", "ACTIVE" -> "incoming"
            "ANSWERED", "ENDED" -> "incoming"
            "OUTGOING" -> "outgoing"
            "MISSED" -> "missed"
            "DECLINED", "REJECTED" -> "missed"
            else -> "unknown"
        }

        // Mapping to requested statuses: ringing | active | ended | missed | declined | captured | unknown
        val finalStatus = when (status.uppercase()) {
            "INCOMING", "RINGING" -> "ringing"
            "ACTIVE" -> "active"
            "ANSWERED", "ENDED" -> "ended"
            "OUTGOING" -> "ended"
            "MISSED" -> "missed"
            "DECLINED", "REJECTED" -> "declined"
            "CAPTURED" -> "captured"
            else -> status.lowercase()
        }

        val finalDurationSeconds = durationSeconds ?: duration

        // format timestamps to ISO-8601
        val finalCapturedAt = capturedAt ?: formatIso8601(timestamp)
        val finalStartedAt = startedAt ?: formatIso8601(timestamp)
        val finalEndedAt = endedAt ?: if (finalDurationSeconds > 0) {
            formatIso8601(timestamp + (finalDurationSeconds * 1000))
        } else {
            null
        }

        // Build clientEventId deterministically to prevent duplicate syncs
        val eventId = generateClientEventId(mappedSource, phoneNumber, timestamp, finalDurationSeconds, finalDirection)

        val event = CallEvent(
            eventId = eventId,
            deviceId = settings.deviceId,
            agentName = settings.agentName,
            source = mappedSource,
            direction = finalDirection,
            status = finalStatus,
            contactName = contactName,
            phoneNumber = phoneNumber,
            appPackage = appPackage ?: if (mappedSource.contains("whatsapp")) appPackage ?: "com.whatsapp" else null,
            startedAt = finalStartedAt,
            endedAt = finalEndedAt,
            durationSeconds = finalDurationSeconds,
            capturedAt = finalCapturedAt,
            notificationTitle = notificationTitle,
            notificationText = notificationText,
            notes = notes ?: if (mappedSource.contains("whatsapp")) "WhatsApp Call Intercepted" else "Cellular Call Captured",
            timestamp = timestamp,
            duration = duration,
            isSynced = false
        )

        // 1. Store in local Room DB first (offline-first design!)
        callEventDao.insertEvent(event)
        Log.d(tag, "Saved event locally in Room: $eventId (Number: $phoneNumber, Source: $mappedSource, Direction: $finalDirection, Status: $finalStatus)")

        // 2. If settings allow/monitoring is enabled, attempt immediate CRM send
        var syncSuccess = false
        if (settings.monitoringEnabled) {
            syncSuccess = crmClient.sendEvent(settings, event)
            if (syncSuccess) {
                callEventDao.markEventAsSynced(eventId)
                Log.d(tag, "Successfully uploaded event in real-time to CRM: $eventId")
            } else {
                Log.w(tag, "Real-time sync failed. Kept in Room for retry: $eventId")
            }
        } else {
            Log.d(tag, "Monitoring disabled, skipped real-time upload.")
        }

        return@withContext Pair(event.copy(isSynced = syncSuccess), syncSuccess)
    }

    fun formatIso8601(timestamp: Long): String {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        df.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return df.format(java.util.Date(timestamp))
    }

    private fun generateClientEventId(source: String, phoneNumber: String, timestamp: Long, duration: Long, direction: String): String {
        val raw = "$source|$phoneNumber|$timestamp|$duration|$direction"
        return java.util.UUID.nameUUIDFromBytes(raw.toByteArray(Charsets.UTF_8)).toString()
    }

    // Process sync of all unsynced logs inside local database
    suspend fun syncUnsyncedEvents(): Int = withContext(Dispatchers.IO) {
        val settings = settingsManager.settingsFlow.first()
        val unsyncedList = callEventDao.getUnsyncedEvents()
        Log.d(tag, "Running sync. Found ${unsyncedList.size} unsynced items.")

        if (unsyncedList.isEmpty()) return@withContext 0

        var successfulSyncs = 0
        for (event in unsyncedList) {
            val success = crmClient.sendEvent(settings, event)
            if (success) {
                callEventDao.markEventAsSynced(event.eventId)
                successfulSyncs++
            } else {
                // Stop or continue? We continue to try others, but typically connection issue affects all
                Log.w(tag, "Failed to sync event ${event.eventId} during bulk resync.")
            }
        }
        return@withContext successfulSyncs
    }

    suspend fun hasEventProximity(source: String, timestamp: Long): Boolean = withContext(Dispatchers.IO) {
        // Proximity window of +/- 3 seconds to handle hardware clock skews and rounding differences safely.
        val normalizedSource = when {
            source.equals("phone", ignoreCase = true) || source.equals("cellular", ignoreCase = true) || source.equals("PHONE", ignoreCase = true) -> "cellular"
            source.equals("whatsapp_business", ignoreCase = true) -> "whatsapp_business"
            source.equals("whatsapp", ignoreCase = true) || source.equals("WHATSAPP", ignoreCase = true) -> "whatsapp"
            else -> source.lowercase()
        }
        val count = callEventDao.hasEventProximity(normalizedSource, timestamp - 3000, timestamp + 3000)
        val oldStyleSource = if (normalizedSource == "cellular") "PHONE" else normalizedSource.uppercase()
        val oldCount = callEventDao.hasEventProximity(oldStyleSource, timestamp - 3000, timestamp + 3000)
        return@withContext (count > 0 || oldCount > 0)
    }

    suspend fun findRecentEvent(source: String, contactName: String, timestamp: Long): CallEvent? = withContext(Dispatchers.IO) {
        // Find is within 60 seconds (60000 ms)
        val minTime = timestamp - 60000
        val maxTime = timestamp + 60000
        return@withContext callEventDao.findRecentEvent(source, contactName, minTime, maxTime)
    }

    suspend fun testConnection(baseUrl: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext crmClient.testConnection(baseUrl, apiKey)
    }
    
    suspend fun clearAllEvents() = withContext(Dispatchers.IO) {
        callEventDao.clear()
    }
}
