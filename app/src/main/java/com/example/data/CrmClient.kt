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
        val deviceId: String,
        val agentName: String,
        val source: String,
        val contactName: String,
        val phoneNumber: String,
        val status: String,
        val timestamp: Long,
        val duration: Long
    )

    suspend fun sendEvent(settings: UserSettings, event: CallEvent): Boolean {
        return try {
            val endpointUrl = resolveEndpoint(settings.crmBaseUrl)
            Log.d(tag, "Sending call event to CRM endpoint: $endpointUrl (Source: ${event.source}, Status: ${event.status})")

            val payload = CrmPayload(
                deviceId = settings.deviceId,
                agentName = settings.agentName,
                source = event.source,
                contactName = event.contactName.ifEmpty { "Unknown" },
                phoneNumber = event.phoneNumber,
                status = event.status,
                timestamp = event.timestamp,
                duration = event.duration
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
            // Test connection will attempt a GET request or a POST with mock body to api endpoint
            // To make it lightweight, let's resolve endpoint and try to see if endpoint exists,
            // or just make a ping to base URL to see if it resolves and returns a response.
            val endpointUrl = resolveEndpoint(baseUrl)
            Log.d(tag, "Testing connection to: $endpointUrl")

            // Let's perform a lightweight HEAD or GET on the resolved URL
            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .get()

            if (apiKey.isNotEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                // Even a 404 or 405 means the server exists and is contactable.
                // 200, 4xx, 5xx are all technically "contacted" versus network timeout/host unreachable which throw exceptions.
                Log.d(tag, "Test connection response code: ${response.code}")
                // If we get any HTTP direct response, the base URL is validated and endpoint contactable.
                true
            }
        } catch (e: Exception) {
            Log.e(tag, "Test connection failed: ${e.message}")
            false
        }
    }
}
