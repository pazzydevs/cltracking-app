package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.CallTrackApplication
import com.example.data.CallEvent
import com.example.data.CallEventRepository
import com.example.data.SettingsManager
import com.example.data.UserSettings
import com.example.service.CallMonitoringService
import com.example.service.CrmSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class AppTab {
    Dashboard, Settings, Permissions
}

enum class ConnectionState {
    IDLE, TESTING, SUCCESS, ERROR
}

class CallTrackViewModel(
    private val repository: CallEventRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val tag = "CallTrackViewModel"

    // Dynamic Navigation state
    private val _currentTab = MutableStateFlow(AppTab.Dashboard)
    val currentTab = _currentTab.asStateFlow()

    // Connection test state
    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow("Never")
    val lastSyncTime = _lastSyncTime.asStateFlow()

    // Exposing reactive Room Streams
    val settings: StateFlow<UserSettings> = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings("", "", "", "", "", 15, false)
        )

    val recentEvents: StateFlow<List<CallEvent>> = repository.allEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val eventsTodayCount: StateFlow<Int> = repository.getEventsTodayCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val lastEvent: StateFlow<CallEvent?> = repository.lastEvent
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    // Toggle call monitoring active status
    fun toggleMonitoring(context: Context) {
        viewModelScope.launch {
            val currentSettings = settings.value
            val nextState = !currentSettings.monitoringEnabled
            
            // 1. Save state in Datastore
            settingsManager.setMonitoringEnabled(nextState)
            Log.d(tag, "Monitoring state toggled to: $nextState")

            // 2. Control Foreground Service & WorkManager Periodic Task
            updateBackgroundOperations(context, nextState, currentSettings.syncIntervalMins)
        }
    }

    private fun updateBackgroundOperations(context: Context, enabled: Boolean, intervalMins: Int) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            // Start continuous logging Foreground Service
            CallMonitoringService.startService(context)

            // Setup and trigger periodic WorkManager resync
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncInterval = intervalMins.coerceAtLeast(15) // WorkManager limits to 15m minimum
            val workRequest = PeriodicWorkRequestBuilder<CrmSyncWorker>(
                syncInterval.toLong(), TimeUnit.MINUTES
            )
            .setConstraints(constraints)
            .build()

            workManager.enqueueUniquePeriodicWork(
                "crm_sync_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d(tag, "Scheduled CRM backup WorkManager: Interval = ${syncInterval}m")
        } else {
            // Stop continuous logging Foreground Service
            CallMonitoringService.stopService(context)

            // Cancel background sync task
            workManager.cancelUniqueWork("crm_sync_work")
            Log.d(tag, "Stopped background monitoring service and canceled WorkManager sync task.")
        }
    }

    // Test endpoint connection dynamically
    fun testConnection(
        context: Context,
        crmBaseUrl: String,
        crmApiKey: String,
        deviceName: String,
        agentName: String,
        syncIntervalMins: Int
    ) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.TESTING
            val success = repository.testConnection(crmBaseUrl, crmApiKey)
            if (success) {
                _connectionState.value = ConnectionState.SUCCESS
                updateLastSyncTime()
                
                // Automatically save settings on success
                settingsManager.saveSettings(
                    crmBaseUrl = crmBaseUrl,
                    crmApiKey = crmApiKey,
                    deviceName = deviceName,
                    agentName = agentName,
                    syncIntervalMins = syncIntervalMins
                )
                val currentSettings = settings.value
                if (currentSettings.monitoringEnabled) {
                    updateBackgroundOperations(context, true, syncIntervalMins)
                }
            } else {
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    // Save customized options
    fun saveSettings(
        context: Context,
        crmBaseUrl: String,
        crmApiKey: String,
        deviceName: String,
        agentName: String,
        syncIntervalMins: Int
    ) {
        viewModelScope.launch {
            settingsManager.saveSettings(
                crmBaseUrl = crmBaseUrl,
                crmApiKey = crmApiKey,
                deviceName = deviceName,
                agentName = agentName,
                syncIntervalMins = syncIntervalMins
            )
            val currentSettings = settings.value
            // If monitoring is active, trigger update of WorkManager scheduling with new sync interval
            if (currentSettings.monitoringEnabled) {
                updateBackgroundOperations(context, true, syncIntervalMins)
            }
        }
    }

    // Trigger manual force sync now
    fun forceSyncNow() {
        viewModelScope.launch {
            val synced = repository.syncUnsyncedEvents()
            if (synced > 0) {
                updateLastSyncTime()
            }
        }
    }
    
    fun clearDatabase() {
        viewModelScope.launch {
            repository.clearAllEvents()
        }
    }

    private fun updateLastSyncTime() {
        val sdf = SimpleDateFormat("HH:mm:ss (MMM dd)", Locale.getDefault())
        _lastSyncTime.value = sdf.format(Date())
    }

    init {
        // Ensure standard unique device id is set upon startup
        viewModelScope.launch {
            settingsManager.ensureDeviceIdGenerated()
        }
    }

    class Factory(
        private val repository: CallEventRepository,
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CallTrackViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CallTrackViewModel(repository, settingsManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
