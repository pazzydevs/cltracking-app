package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.CallEventRepository
import com.example.data.CrmClient
import com.example.data.SettingsManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class CallTrackApplication : Application() {

    lateinit var settingsManager: SettingsManager
    lateinit var callEventRepository: CallEventRepository

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        
        val database = AppDatabase.getDatabase(this)
        val crmClient = CrmClient()
        
        callEventRepository = CallEventRepository(
            callEventDao = database.callEventDao(),
            settingsManager = settingsManager,
            crmClient = crmClient
        )

        // Run local cleanup function to merge duplicate rows once on startup
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                callEventRepository.cleanupOldDuplicates()
            } catch (e: Exception) {
                android.util.Log.e("CallTrackApplication", "Failed to run duplicate cleanup on startup: ${e.message}", e)
            }
        }
    }
}
