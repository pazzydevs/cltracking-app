package com.example.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "calltrack_settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val CRM_BASE_URL = stringPreferencesKey("crm_base_url")
        private val CRM_API_KEY_ENCRYPTED = stringPreferencesKey("crm_api_key_encrypted")
        private val DEVICE_NAME = stringPreferencesKey("device_name")
        private val AGENT_NAME = stringPreferencesKey("agent_name")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val SYNC_INTERVAL_MINS = intPreferencesKey("sync_interval_mins")
        private val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")

        // Hardcoded key fallback / local obfuscation key
        private const val OBFUSCATION_SALT = "CallTrackCRMSecret123"
    }

    // Encrypt stored API key using simple secure obfuscation
    private fun encrypt(value: String): String {
        return try {
            val keyBytes = OBFUSCATION_SALT.toByteArray()
            val valueBytes = value.toByteArray()
            val encryptedBytes = ByteArray(valueBytes.size)
            for (i in valueBytes.indices) {
                encryptedBytes[i] = (valueBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Base64.encodeToString(value.toByteArray(), Base64.DEFAULT)
        }
    }

    // Decrypt stored API key
    private fun decrypt(encryptedValue: String): String {
        if (encryptedValue.isEmpty()) return ""
        return try {
            val encryptedBytes = Base64.decode(encryptedValue, Base64.DEFAULT)
            val keyBytes = OBFUSCATION_SALT.toByteArray()
            val decryptedBytes = ByteArray(encryptedBytes.size)
            for (i in encryptedBytes.indices) {
                decryptedBytes[i] = (encryptedBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            String(decryptedBytes)
        } catch (e: Exception) {
            try {
                String(Base64.decode(encryptedValue, Base64.DEFAULT))
            } catch (ex: Exception) {
                encryptedValue
            }
        }
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val savedDeviceId = preferences[DEVICE_ID] ?: run {
            val generated = UUID.randomUUID().toString().take(8)
            // Save Device ID asynchronously
            generated
        }
        UserSettings(
            crmBaseUrl = preferences[CRM_BASE_URL] ?: "https://crm.company.com/api",
            crmApiKey = decrypt(preferences[CRM_API_KEY_ENCRYPTED] ?: ""),
            deviceName = preferences[DEVICE_NAME] ?: android.os.Build.MODEL,
            agentName = preferences[AGENT_NAME] ?: "Agent1",
            deviceId = savedDeviceId,
            syncIntervalMins = preferences[SYNC_INTERVAL_MINS] ?: 15,
            monitoringEnabled = preferences[MONITORING_ENABLED] ?: false
        )
    }

    suspend fun saveSettings(
        crmBaseUrl: String,
        crmApiKey: String,
        deviceName: String,
        agentName: String,
        syncIntervalMins: Int
    ) {
        context.dataStore.edit { preferences ->
            preferences[CRM_BASE_URL] = crmBaseUrl
            preferences[CRM_API_KEY_ENCRYPTED] = encrypt(crmApiKey)
            preferences[DEVICE_NAME] = deviceName
            preferences[AGENT_NAME] = agentName
            preferences[SYNC_INTERVAL_MINS] = syncIntervalMins
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MONITORING_ENABLED] = enabled
        }
    }

    suspend fun ensureDeviceIdGenerated(): String {
        var generatedId = ""
        context.dataStore.edit { preferences ->
            val existing = preferences[DEVICE_ID]
            if (existing == null) {
                generatedId = UUID.randomUUID().toString().take(12)
                preferences[DEVICE_ID] = generatedId
            } else {
                generatedId = existing
            }
        }
        return generatedId
    }
}

data class UserSettings(
    val crmBaseUrl: String,
    val crmApiKey: String,
    val deviceName: String,
    val agentName: String,
    val deviceId: String,
    val syncIntervalMins: Int,
    val monitoringEnabled: Boolean
)
