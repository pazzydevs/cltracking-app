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

    // Helper to identify generic contact names
    fun isGenericContact(value: String?): Boolean {
        if (value == null) return true
        val v = value.trim().lowercase()
        val genericPhrases = listOf(
            "incoming voice call",
            "ongoing voice call",
            "voice call",
            "video call",
            "incoming video call",
            "ongoing video call",
            "whatsapp",
            "whatsapp business",
            "calling...",
            "calling",
            "missed voice call",
            "missed video call"
        )
        return v.isEmpty() || genericPhrases.contains(v) || v.contains("voice call") || v.contains("video call") || v.contains("missed voice") || v.contains("missed video")
    }

    // Normalizations
    fun normalizePhone(phone: String?): String {
        if (phone == null) return ""
        return phone.trim().lowercase().replace(Regex("[\\s\\-\\(\\)\\+\\[\\]]"), "")
    }

    fun normalizeContact(contact: String?): String {
        if (contact == null) return ""
        val trimmed = contact.trim()
        if (isGenericContact(trimmed)) {
            return ""
        }
        return trimmed.replace(Regex("\\s+"), " ")
    }

    // Stable ID generation using Name UUID
    fun generateStableEventId(source: String, phoneNumber: String, contactName: String): String {
        val cleanSource = source.lowercase()
        if (cleanSource == "cellular" || cleanSource == "phone") {
            val normPhone = normalizePhone(phoneNumber)
            val key = if (normPhone.isNotEmpty()) {
                "cellular|$normPhone"
            } else {
                val normContact = normalizeContact(contactName).lowercase()
                "cellular|$normContact"
            }
            return java.util.UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
        } else {
            // WhatsApp or WhatsApp Business
            val normContact = normalizeContact(contactName).lowercase()
            val normPhone = normalizePhone(phoneNumber)
            // If contact is present, use contact, else phone
            val contactOrPhone = if (normContact.isNotEmpty()) normContact else normPhone
            val key = "$cleanSource|$contactOrPhone"
            return java.util.UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
        }
    }

    suspend fun findRecentOpenWhatsAppEvent(source: String, fromTime: Long): CallEvent? = withContext(Dispatchers.IO) {
        return@withContext callEventDao.findRecentOpenWhatsAppEvent(source, fromTime)
    }

    // Local cleanup function to merge duplicate rows
    suspend fun cleanupOldDuplicates() = withContext(Dispatchers.IO) {
        val allEventsList = callEventDao.getAllEventsDirect()
        if (allEventsList.isEmpty()) return@withContext

        // Group events by stable eventId
        val grouped = allEventsList.groupBy { event ->
            generateStableEventId(event.source, event.phoneNumber, event.contactName)
        }

        for ((stableId, events) in grouped) {
            if (events.size > 1) {
                // Delete duplicates and keep the most complete one
                val sortedEvents = events.sortedWith(compareByDescending<CallEvent> {
                    when (it.status.lowercase()) {
                        "ended" -> 3
                        "missed", "declined" -> 2
                        "active" -> 1
                        else -> 0
                    }
                }.thenByDescending { it.durationSeconds }
                 .thenByDescending { it.timestamp })

                val bestEvent = sortedEvents.first()

                for (other in events) {
                    if (other.eventId != bestEvent.eventId) {
                        callEventDao.deleteEventById(other.eventId)
                        Log.d(tag, "Cleaned up duplicate eventId: ${other.eventId} for: ${bestEvent.contactName}")
                    }
                }

                if (bestEvent.eventId != stableId) {
                    val updatedEvent = bestEvent.copy(eventId = stableId)
                    callEventDao.insertEvent(updatedEvent)
                    callEventDao.deleteEventById(bestEvent.eventId)
                    Log.d(tag, "Migrated eventId from ${bestEvent.eventId} to stableId $stableId")
                }
            } else {
                val event = events.first()
                if (event.eventId != stableId) {
                    val updatedEvent = event.copy(eventId = stableId)
                    callEventDao.insertEvent(updatedEvent)
                    callEventDao.deleteEventById(event.eventId)
                    Log.d(tag, "Migrated single eventId ${event.eventId} to stableId $stableId")
                }
            }
        }
    }

    // Save and attempt to synchronize event in real time (one row per contact/source!)
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
        notes: String? = null,
        clientEventIdOverride: String? = null,
        callGroupKey: String? = null
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

        val eventId = clientEventIdOverride ?: generateStableEventId(mappedSource, phoneNumber, contactName)

        val existingEvent = callEventDao.getEventById(eventId)

        // Normalize inputs nicely but preserve best contact/phone names
        var finalContactName = contactName
        var finalPhoneNumber = phoneNumber

        if (isGenericContact(finalContactName) || finalContactName.isEmpty()) {
            if (existingEvent != null && !isGenericContact(existingEvent.contactName)) {
                finalContactName = existingEvent.contactName
            }
        }
        if (finalPhoneNumber.isEmpty() || finalPhoneNumber.startsWith("WhatsApp", ignoreCase = true)) {
            if (existingEvent != null && existingEvent.phoneNumber.isNotEmpty() && !existingEvent.phoneNumber.startsWith("WhatsApp", ignoreCase = true)) {
                finalPhoneNumber = existingEvent.phoneNumber
            }
        }

        val earliestStartedAt = existingEvent?.startedAt ?: startedAt ?: formatIso8601(timestamp)
        val latestCapturedAt = capturedAt ?: formatIso8601(timestamp)

        val finalDurationSeconds = if ((durationSeconds ?: duration) > 0) (durationSeconds ?: duration) else (existingEvent?.durationSeconds ?: 0L)
        val finalDuration = if (duration > 0) duration else (existingEvent?.duration ?: 0L)

        val finalEndedAt = endedAt ?: when {
            finalDurationSeconds > 0 -> formatIso8601(timestamp + (finalDurationSeconds * 1000))
            finalStatus in listOf("ended", "missed", "declined") -> formatIso8601(timestamp)
            else -> existingEvent?.endedAt
        }

        // Avoid multiple CRM POSTs for the same unchanged state
        if (existingEvent != null &&
            existingEvent.status == finalStatus &&
            existingEvent.direction == finalDirection &&
            existingEvent.durationSeconds == finalDurationSeconds &&
            existingEvent.contactName == finalContactName &&
            existingEvent.phoneNumber == finalPhoneNumber
        ) {
            Log.d(tag, "Skip update because state has not changed for: $eventId")
            return@withContext Pair(existingEvent, existingEvent.isSynced)
        }

        val event = CallEvent(
            eventId = eventId,
            deviceId = settings.deviceId,
            agentName = settings.agentName,
            source = mappedSource,
            direction = finalDirection,
            status = finalStatus,
            contactName = finalContactName,
            phoneNumber = finalPhoneNumber,
            appPackage = appPackage ?: if (mappedSource.contains("whatsapp")) appPackage ?: "com.whatsapp" else null,
            startedAt = earliestStartedAt,
            endedAt = finalEndedAt,
            durationSeconds = finalDurationSeconds,
            capturedAt = latestCapturedAt,
            notificationTitle = notificationTitle ?: existingEvent?.notificationTitle,
            notificationText = notificationText ?: existingEvent?.notificationText,
            notes = notes ?: existingEvent?.notes ?: if (mappedSource.contains("whatsapp")) "WhatsApp Call Intercepted" else "Cellular Call Captured",
            timestamp = existingEvent?.timestamp ?: timestamp,
            duration = finalDuration,
            isSynced = false // Mark unsynced again so it gets POSTed!
        )

        // 1. Store in local Room DB (updates row if eventId matches)
        callEventDao.insertEvent(event)
        Log.d(tag, "Saved event locally in Room: $eventId (Number: $finalPhoneNumber, Source: $mappedSource, Direction: $finalDirection, Status: $finalStatus)")

        // 2. If settings allow, attempt dynamic CRM sending
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
                Log.w(tag, "Failed to sync event ${event.eventId} during bulk resync.")
            }
        }
        return@withContext successfulSyncs
    }

    suspend fun hasEventProximity(source: String, timestamp: Long): Boolean = withContext(Dispatchers.IO) {
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

    suspend fun testConnection(baseUrl: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext crmClient.testConnection(baseUrl, apiKey)
    }
    
    suspend fun clearAllEvents() = withContext(Dispatchers.IO) {
        callEventDao.clear()
    }
}
