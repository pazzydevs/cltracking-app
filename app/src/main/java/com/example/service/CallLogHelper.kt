package com.example.service

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.util.Log
import com.example.data.CallEvent
import java.io.File

object CallLogHelper {

    private const val tag = "CallLogHelper"

    data class CallInfo(
        val number: String,
        val contactName: String,
        val type: String, // INCOMING, ANSWERED, MISSED, OUTGOING
        val duration: Long,
        val timestamp: Long,
        val rawType: Int = 0
    )

    fun getLatestCallLog(context: Context): CallInfo? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )

            if (cursor != null && cursor.moveToFirst()) {
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

                val number = if (numberIdx != -1) cursor.getString(numberIdx) ?: "" else ""
                val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                val typeCode = if (typeIdx != -1) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                val date = if (dateIdx != -1) cursor.getLong(dateIdx) else System.currentTimeMillis()
                val duration = if (durationIdx != -1) cursor.getLong(durationIdx) else 0L

                // Map Android CallLog types to unified event statuses
                val mappedStatus = when (typeCode) {
                    CallLog.Calls.INCOMING_TYPE -> {
                        // In CallLog, if a call is incoming and we answered it, duration > 0.
                        if (duration > 0L) "ANSWERED" else "INCOMING"
                    }
                    CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                    CallLog.Calls.MISSED_TYPE -> "MISSED"
                    CallLog.Calls.REJECTED_TYPE -> "MISSED"
                    else -> "ENDED"
                }

                return CallInfo(
                    number = number,
                    contactName = name,
                    type = mappedStatus,
                    duration = duration,
                    timestamp = date,
                    rawType = typeCode
                )
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Permission to read Call Log is not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Error reading latest call log: ${e.message}", e)
        } finally {
            cursor?.close()
        }
        return null
    }
}
