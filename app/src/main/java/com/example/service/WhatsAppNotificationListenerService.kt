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
    private val processedKeys = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") {
            return
        }

        // Clean up old processed keys (older than 10 minutes)
        val now = System.currentTimeMillis()
        processedKeys.entries.removeIf { now - it.value > 600000 }

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

                // Active or ongoing calls
                val isCallingText = text.equals("voice call", ignoreCase = true) ||
                        text.equals("video call", ignoreCase = true) ||
                        text.contains("ongoing call", ignoreCase = true) ||
                        text.contains("call in progress", ignoreCase = true)

                val isMissed = isMissedText || isMissedTitle
                val isIncoming = isCallCategory || isIncomingText || isIncomingTitle
                val isActive = isCallingText

                if (!isMissed && !isIncoming && !isActive) {
                    return@launch
                }

                val statusLabel = when {
                    isMissed -> "missed"
                    isIncoming -> "ringing"
                    else -> "active"
                }

                val directionLabel = when {
                    isMissed -> "missed"
                    else -> "incoming"
                }

                var contactName = ""
                if (!repository.isGenericContact(title) && title.trim().isNotEmpty()) {
                    contactName = title.trim()
                } else if (!repository.isGenericContact(text) && text.trim().isNotEmpty()) {
                    contactName = text.trim()
                }

                if (contactName.isEmpty()) {
                    val twoMinutesAgo = sbn.postTime - (2 * 60 * 1000L)
                    val recentOpen = repository.findRecentOpenWhatsAppEvent(crmSource, twoMinutesAgo)
                    if (recentOpen != null) {
                        contactName = recentOpen.contactName
                        Log.d(tag, "Contact empty but found recent open WhatsApp event for $crmSource: ${recentOpen.contactName}")
                    } else {
                        Log.d(tag, "Contact empty and no recent open WhatsApp event found for $crmSource. Skipping notification.")
                        return@launch
                    }
                }

                // 1. In-memory Duplicate Protection
                val memoryKey = "${sbn.key}_${contactName}_${pkg}_${statusLabel}"
                val lastProcessedTime = processedKeys[memoryKey]
                if (lastProcessedTime != null && (now - lastProcessedTime) < 30000) {
                    Log.d(tag, "In-memory duplicate protection triggered for $memoryKey. Skipping execution.")
                    return@launch
                }
                processedKeys[memoryKey] = now

                Log.d(tag, "Recording WHATSAPP call: '$contactName' as $statusLabel ($directionLabel) from $pkg")
                repository.recordEvent(
                    source = crmSource,
                    status = statusLabel,
                    phoneNumber = "WhatsApp: $contactName",
                    contactName = contactName,
                    duration = 0L,
                    timestamp = sbn.postTime,
                    direction = directionLabel,
                    appPackage = pkg,
                    capturedAt = repository.formatIso8601(sbn.postTime),
                    notificationTitle = title,
                    notificationText = text
                )

            } catch (e: Exception) {
                Log.e(tag, "Exception parsing WhatsApp Notification: ${e.message}", e)
            }
        }
    }
}
