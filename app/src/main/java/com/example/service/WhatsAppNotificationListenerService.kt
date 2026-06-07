package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.CallTrackApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WhatsAppNotificationListenerService : NotificationListenerService() {

    private val tag = "WhatsAppInterceptor"
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") {
            return
        }

        val app = applicationContext as? CallTrackApplication ?: return
        val settingsManager = app.settingsManager
        val repository = app.callEventRepository

        scope.launch {
            try {
                val settings = settingsManager.settingsFlow.first()
                if (!settings.monitoringEnabled) {
                    return@launch
                }

                val notification = sbn.notification ?: return@launch
                val extras = notification.extras ?: return@launch

                val title = extras.getString(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getString(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val category = notification.category ?: ""

                // Log details to help locate patterns
                Log.d(tag, "Intercepted WhatsApp Notification - Pkg: $pkg, Title: '$title', Text: '$text', Category: '$category'")

                // Target source based on WhatsApp vs WhatsApp Business packages
                val crmSource = if (pkg == "com.whatsapp.w4b") "whatsapp_business" else "whatsapp"

                // Detect Missed Call
                val isMissedText = text.contains("missed voice call", ignoreCase = true) ||
                        text.contains("missed video call", ignoreCase = true) ||
                        text.contains("missed call", ignoreCase = true) ||
                        text.contains("missed group call", ignoreCase = true)

                val isMissedTitle = title.contains("missed voice call", ignoreCase = true) ||
                        title.contains("missed video call", ignoreCase = true) ||
                        title.contains("missed call", ignoreCase = true) ||
                        title.contains("missed group call", ignoreCase = true)

                if (isMissedText || isMissedTitle) {
                    val contactName = if (isMissedText) title else text
                    val timestamp = sbn.postTime
                    val alreadyRecorded = repository.hasEventProximity(crmSource, timestamp)
                    if (!alreadyRecorded && contactName.isNotEmpty() && contactName != "WhatsApp" && contactName != "WhatsApp Business") {
                        Log.d(tag, "New missed WHATSAPP call captured: '$contactName' from $pkg")
                        repository.recordEvent(
                            source = crmSource,
                            status = "missed",
                            phoneNumber = "WhatsApp: $contactName",
                            contactName = contactName,
                            duration = 0L,
                            timestamp = timestamp,
                            direction = "missed",
                            appPackage = pkg,
                            notificationTitle = title,
                            notificationText = text
                        )
                    }
                    return@launch
                }

                // Detect Incoming Call
                val isCallCategory = category == Notification.CATEGORY_CALL
                val isIncomingText = text.contains("incoming voice call", ignoreCase = true) ||
                        text.contains("incoming video call", ignoreCase = true) ||
                        text.contains("incoming group call", ignoreCase = true) ||
                        text.contains("calling...", ignoreCase = true) ||
                        text.contains("incoming call", ignoreCase = true)

                val isIncomingTitle = title.contains("incoming voice call", ignoreCase = true) ||
                        title.contains("incoming video call", ignoreCase = true) ||
                        title.contains("incoming call", ignoreCase = true)

                if (isCallCategory || isIncomingText || isIncomingTitle) {
                    val contactName = if (isIncomingText) title else text
                    if (contactName.isNotEmpty() && contactName != "WhatsApp" && contactName != "WhatsApp Business") {
                        val timestamp = sbn.postTime
                        val alreadyRecorded = repository.hasEventProximity(crmSource, timestamp)
                        if (!alreadyRecorded) {
                            Log.d(tag, "New incoming WHATSAPP call captured: '$contactName' from $pkg")
                            repository.recordEvent(
                                source = crmSource,
                                status = "ringing",
                                phoneNumber = "WhatsApp: $contactName",
                                contactName = contactName,
                                duration = 0L,
                                timestamp = timestamp,
                                direction = "incoming",
                                appPackage = pkg,
                                notificationTitle = title,
                                notificationText = text
                            )
                        }
                    }
                    return@launch
                }

                // Active or ongoing calls
                val isCallingText = text.equals("voice call", ignoreCase = true) ||
                        text.equals("video call", ignoreCase = true) ||
                        text.contains("ongoing call", ignoreCase = true) ||
                        text.contains("call in progress", ignoreCase = true)

                if (isCallingText) {
                    val contactName = title
                    if (contactName.isNotEmpty() && contactName != "WhatsApp" && contactName != "WhatsApp Business") {
                        val timestamp = sbn.postTime
                        val alreadyRecorded = repository.hasEventProximity(crmSource, timestamp)
                        if (!alreadyRecorded) {
                            Log.d(tag, "Active ongoing WHATSAPP call captured: '$contactName' from $pkg")
                            repository.recordEvent(
                                source = crmSource,
                                status = "active",
                                phoneNumber = "WhatsApp: $contactName",
                                contactName = contactName,
                                duration = 0L,
                                timestamp = timestamp,
                                direction = "incoming",
                                appPackage = pkg,
                                notificationTitle = title,
                                notificationText = text
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(tag, "Exception parsing WhatsApp Notification: ${e.message}", e)
            }
        }
    }
}
