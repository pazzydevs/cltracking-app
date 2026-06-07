package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.CallEventRepository
import com.example.data.CrmClient
import com.example.data.SettingsManager

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
    }
}
