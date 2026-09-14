package com.infinity.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * EmergencyAlertPreference
 *
 * Persists emergency-alert settings using the EXISTING DataStore instance
 * (infinity_prefs) — the same one used by ThemePreference and ReportSharingPreference.
 * No new database or DataStore file is created.
 */
class EmergencyAlertPreference(private val context: Context) {

    companion object {
        val ALERTS_ENABLED_KEY    = booleanPreferencesKey("emergency_alerts_enabled")
        val CONTACT_NUMBER_KEY    = stringPreferencesKey("emergency_contact_number")
        val SMS_ENABLED_KEY       = booleanPreferencesKey("emergency_sms_enabled")
        val CALL_ENABLED_KEY      = booleanPreferencesKey("emergency_call_enabled")
        val LOCATION_ENABLED_KEY  = booleanPreferencesKey("emergency_location_enabled")
    }

    val alertsEnabled: Flow<Boolean>    = context.dataStore.data.map { it[ALERTS_ENABLED_KEY]   ?: false }
    val contactNumber: Flow<String>     = context.dataStore.data.map { it[CONTACT_NUMBER_KEY]   ?: "" }
    val smsEnabled: Flow<Boolean>       = context.dataStore.data.map { it[SMS_ENABLED_KEY]      ?: true }
    val callEnabled: Flow<Boolean>      = context.dataStore.data.map { it[CALL_ENABLED_KEY]     ?: false }
    val locationEnabled: Flow<Boolean>  = context.dataStore.data.map { it[LOCATION_ENABLED_KEY] ?: false }

    suspend fun setAlertsEnabled(v: Boolean)   { context.dataStore.edit { it[ALERTS_ENABLED_KEY]   = v } }
    suspend fun setContactNumber(v: String)    { context.dataStore.edit { it[CONTACT_NUMBER_KEY]   = v.trim() } }
    suspend fun setSmsEnabled(v: Boolean)      { context.dataStore.edit { it[SMS_ENABLED_KEY]      = v } }
    suspend fun setCallEnabled(v: Boolean)     { context.dataStore.edit { it[CALL_ENABLED_KEY]     = v } }
    suspend fun setLocationEnabled(v: Boolean) { context.dataStore.edit { it[LOCATION_ENABLED_KEY] = v } }

    /** Basic phone-number validation — accepts digits, spaces, +, -, (, ) */
    fun isValidNumber(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        return digits.length in 7..15
    }
}
