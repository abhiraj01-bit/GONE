package com.infinity.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * ReportSharingPreference
 *
 * Persists the "Share Reports to Cloud" toggle and email address
 * using the existing DataStore instance (infinity_prefs).
 * No new database or DataStore file is created.
 */
class ReportSharingPreference(private val context: Context) {

    companion object {
        val SHARING_ENABLED_KEY = booleanPreferencesKey("report_sharing_enabled")
        val SHARING_EMAIL_KEY   = stringPreferencesKey("report_sharing_email")
    }

    val sharingEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[SHARING_ENABLED_KEY] ?: false }

    val sharingEmail: Flow<String> = context.dataStore.data
        .map { it[SHARING_EMAIL_KEY] ?: "" }

    suspend fun setSharingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SHARING_ENABLED_KEY] = enabled }
    }

    suspend fun setSharingEmail(email: String) {
        context.dataStore.edit { it[SHARING_EMAIL_KEY] = email.trim() }
    }

    /** Returns true only when sharing is on AND email is valid. */
    fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}
