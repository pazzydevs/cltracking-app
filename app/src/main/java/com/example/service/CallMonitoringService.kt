package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.CallTrackApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CallMonitoringService : Service() {

    private val tag = "CallMonitoringService"
    private val channelId = "calltrack_monitoring_channel"
    private val notificationId = 101
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var isReceiverRegistered = false
    private val phoneStateReceiver = PhoneStateReceiver()

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        
        fun startService(context: Context) {
            val intent = Intent(context, CallMonitoringService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent) // Triggers onStartCommand to stop
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Foreground Monitoring Service Created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(tag, "onStartCommand triggered with action: $action")

        if (action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        // Default is to start/keep monitoring
        startMonitoringForeground()
        return START_STICKY
    }

    private fun startMonitoringForeground() {
        val notification = createNotification()
        
        // Start foreground with appropriate type for Android 14+ / SDK 34-36 compat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    notificationId, 
                    notification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                Log.d(tag, "Started foreground service with SPECIAL_USE type")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start foreground service with SPECIAL_USE: ${e.message}", e)
                try {
                    startForeground(notificationId, notification)
                } catch (ex: Exception) {
                    Log.e(tag, "Failed fallback standard startForeground: ${ex.message}", ex)
                }
            }
        } else {
            startForeground(notificationId, notification)
        }

        registerPhoneStateReceiver()
    }

    private fun stopMonitoring() {
        Log.d(tag, "Stopping Call Monitoring Foreground Service...")
        unregisterPhoneStateReceiver()
        stopForeground(true)
        stopSelf()
    }

    private fun registerPhoneStateReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(phoneStateReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(phoneStateReceiver, filter)
                }
                isReceiverRegistered = true
                Log.d(tag, "Dynamically registered PhoneStateReceiver successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error registering PhoneStateReceiver dynamically: ${e.message}", e)
            }
        }
    }

    private fun unregisterPhoneStateReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(phoneStateReceiver)
                isReceiverRegistered = false
                Log.d(tag, "Dynamically unregistered PhoneStateReceiver successfully")
            } catch (e: Exception) {
                Log.e(tag, "Error unregistering PhoneStateReceiver: ${e.message}", e)
            }
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, CallMonitoringService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("CallTrack Active Monitoring")
            .setContentText("Running call and WhatsApp background interceptor...")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call) // Default Android phone icon fallback
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Call Connection Relay Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors phone calls and WhatsApp to sync to CRM"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(tag, "CallMonitoringService Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
