package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

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
        timestamp: Long = System.currentTimeMillis()
    ): Pair<CallEvent, Boolean> = withContext(Dispatchers.IO) {
        val settings = settingsManager.settingsFlow.first()
        val eventId = java.util.UUID.randomUUID().toString()

        val event = CallEvent(
            eventId = eventId,
            deviceId = settings.deviceId,
            agentName = settings.agentName,
            source = source,
            contactName = contactName,
            phoneNumber = phoneNumber,
            status = status,
            timestamp = timestamp,
            duration = duration,
            isSynced = false
        )

        // 1. Store in local Room DB first (offline-first design!)
        callEventDao.insertEvent(event)
        Log.d(tag, "Saved event locally in Room: $eventId (Number: $phoneNumber, Status: $status)")

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
        val count = callEventDao.hasEventProximity(source, timestamp - 3000, timestamp + 3000)
        return@withContext count > 0
    }

    suspend fun testConnection(baseUrl: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext crmClient.testConnection(baseUrl, apiKey)
    }
    
    suspend fun clearAllEvents() = withContext(Dispatchers.IO) {
        callEventDao.clear()
    }
}
