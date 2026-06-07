package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CrmClient {

    private val tag = "CrmClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Helper to resolve endpoints intelligently
    private fun resolveEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        return when {
            trimmed.endsWith("/api/call-events") -> trimmed
            trimmed.endsWith("/api/call-events/") -> trimmed.removeSuffix("/")
            trimmed.endsWith("/call-events") -> trimmed
            trimmed.endsWith("/call-events/") -> trimmed.removeSuffix("/")
            trimmed.endsWith("/api") -> "$trimmed/call-events"
            trimmed.endsWith("/api/") -> "${trimmed}call-events"
            trimmed.endsWith("/") -> "${trimmed}api/call-events"
            else -> "$trimmed/api/call-events"
        }
    }

    // JSON format requested for POST to /api/call-events
    data class CrmPayload(
        val clientEventId: String,
        val deviceId: String,
        val agentName: String,
        val source: String,
        val direction: String,
        val status: String,
        val contactName: String,
        val phoneNumber: String,
        val appPackage: String?,
        val startedAt: String?,
        val endedAt: String?,
        val durationSeconds: Long,
        val capturedAt: String,
        val notificationTitle: String?,
        val notificationText: String?,
        val notes: String?
    )

    suspend fun sendEvent(settings: UserSettings, event: CallEvent): Boolean {
        return try {
            val endpointUrl = resolveEndpoint(settings.crmBaseUrl)
            Log.d(tag, "Sending call event to CRM endpoint: $endpointUrl (Source: ${event.source}, Status: ${event.status})")

            val payload = CrmPayload(
                clientEventId = event.eventId,
                deviceId = settings.deviceId,
                agentName = settings.agentName,
                source = event.source,
                direction = event.direction,
                status = event.status,
                contactName = event.contactName.ifEmpty { "Unknown" },
                phoneNumber = event.phoneNumber,
                appPackage = event.appPackage,
                startedAt = event.startedAt,
                endedAt = event.endedAt,
                durationSeconds = event.durationSeconds,
                capturedAt = event.capturedAt,
                notificationTitle = event.notificationTitle,
                notificationText = event.notificationText,
                notes = event.notes ?: "Call event saved"
            )

            val jsonAdapter = moshi.adapter(CrmPayload::class.java)
            val jsonString = jsonAdapter.toJson(payload)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonString.toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .post(requestBody)

            if (settings.crmApiKey.isNotEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer ${settings.crmApiKey}")
            }

            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val isSuccessful = response.isSuccessful
                Log.d(tag, "CRM response code: $code, successful: $isSuccessful")
                isSuccessful
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to send call event to CRM: ${e.message}", e)
            false
        }
    }

    suspend fun testConnection(baseUrl: String, apiKey: String): Boolean {
        return try {
            val endpointUrl = resolveEndpoint(baseUrl)
            Log.d(tag, "Testing connection to: $endpointUrl")

            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .get()

            if (apiKey.isNotEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                Log.d(tag, "Test connection response code: ${response.code}")
                response.code == 200
            }
        } catch (e: Exception) {
            Log.e(tag, "Test connection failed: ${e.message}")
            false
        }
    }
}
