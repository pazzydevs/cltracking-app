package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.CallTrackApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PhoneStateReceiver : BroadcastReceiver() {

    private val tag = "PhoneStateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        Log.d(tag, "Received phone state changed broadcast: $state")

        val app = context.applicationContext as? CallTrackApplication ?: return
        val repository = app.callEventRepository
        val settingsManager = app.settingsManager

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val settings = settingsManager.settingsFlow.first()
                if (!settings.monitoringEnabled) {
                    Log.d(tag, "CallTrack Monitoring is disabled in settings. Ignoring broadcast.")
                    return@launch
                }

                if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                    // IDLE state means the call just finished (or ringing stopped/missed)
                    // Leverage our database-backed proximity query to dynamically detect and capture
                    // the *new* call event as soon as the Android system commits it to the CallLog,
                    // automatically avoiding stale and duplicate records.
                    var callInfo: CallLogHelper.CallInfo? = null
                    var attempts = 0
                    val maxAttempts = 6
                    val delayMs = 1200L
                    var successfullyCapturedNewEvent = false
                    
                    Log.d(tag, "Phone idle detected. Starting dynamic CallLog polling (max $maxAttempts attempts)...")
                    
                    while (attempts < maxAttempts) {
                        callInfo = CallLogHelper.getLatestCallLog(context)
                        if (callInfo != null) {
                            // Check if this latest CallLog record is fresh AND has not been saved yet
                            val alreadyRecorded = repository.hasEventProximity("PHONE", callInfo.timestamp)
                            
                            // In Android, CallLog.Calls.DATE represents the call START time.
                            // The actual call END time is the start time plus the call duration.
                            val callEndTime = callInfo.timestamp + (callInfo.duration * 1000)
                            
                            // Use a generous 10-minute window relative to the call end time, which handles 
                            // both short missed calls and very long active calls perfectly, even with clock drift.
                            val isFresh = Math.abs(System.currentTimeMillis() - callEndTime) < 1000 * 60 * 10
                            
                            if (!alreadyRecorded && isFresh) {
                                // This is indeed the brand new call log entry representing the call that just ended!
                                Log.d(tag, "Matched new unrecorded fresh CallLog entry: Number=${callInfo.number}, Status=${callInfo.type}, Timestamp=${callInfo.timestamp} (Attempt ${attempts + 1})")
                                successfullyCapturedNewEvent = true
                                break
                            } else {
                                Log.d(tag, "Latest CallLog (Timestamp: ${callInfo.timestamp}, Duration: ${callInfo.duration}s) alreadyRecord=$alreadyRecorded, isFresh=$isFresh. Waiting for system write... (Attempt ${attempts + 1})")
                            }
                        } else {
                            Log.d(tag, "CallLog query returned null. Waiting... (Attempt ${attempts + 1})")
                        }
                        attempts++
                        delay(delayMs)
                    }
                    
                    if (successfullyCapturedNewEvent && callInfo != null) {
                        val rawType = callInfo.rawType
                        val duration = callInfo.duration
                        
                        val (direction, crmStatus) = when (rawType) {
                            1 -> { // INCOMING_TYPE
                                if (duration > 0L) {
                                    Pair("incoming", "ended")
                                } else {
                                    Pair("incoming", "missed")
                                }
                            }
                            2 -> Pair("outgoing", "ended") // OUTGOING_TYPE
                            3 -> Pair("missed", "missed")  // MISSED_TYPE
                            5 -> Pair("missed", "declined") // REJECTED_TYPE
                            else -> {
                                when (callInfo.type) {
                                    "ANSWERED", "ENDED" -> Pair("incoming", "ended")
                                    "OUTGOING" -> Pair("outgoing", "ended")
                                    "MISSED" -> Pair("missed", "missed")
                                    else -> Pair("unknown", "unknown")
                                }
                            }
                        }

                        Log.d(tag, "Recording validated fresh event: Source=cellular, Status=$crmStatus, Direction=$direction, RawType=$rawType")
                        repository.recordEvent(
                            source = "cellular",
                            status = crmStatus,
                            phoneNumber = callInfo.number,
                            contactName = callInfo.contactName,
                            duration = duration,
                            timestamp = callInfo.timestamp,
                            direction = direction,
                            durationSeconds = duration
                        )
                    } else {
                        Log.w(tag, "No new unrecorded call details came through CallLog query after retries (the latest calls are already processed or permissions are missing).")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error processing phone state idle state: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
