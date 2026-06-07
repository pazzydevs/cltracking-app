package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.CallTrackApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    private val tag = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.d(tag, "System boot completed or package replaced. Checking monitoring start state...")

        val app = context.applicationContext as? CallTrackApplication ?: return
        val settingsManager = app.settingsManager

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsManager.settingsFlow.first()
                if (settings.monitoringEnabled) {
                    Log.d(tag, "Monitoring was enabled. Auto-starting CallMonitoringService in background...")
                    CallMonitoringService.startService(context)
                } else {
                    Log.d(tag, "Monitoring was disabled. No auto-start required.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to check boot settings: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
