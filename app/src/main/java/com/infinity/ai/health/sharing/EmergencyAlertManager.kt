package com.infinity.ai.health.sharing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.infinity.ai.data.EmergencyAlertPreference
import com.infinity.ai.health.data.HealthDatabase
import com.infinity.ai.health.data.HealthReportEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

/**
 * EmergencyAlertManager
 *
 * Isolated component responsible ONLY for:
 *  - Checking emergency settings
 *  - Checking CRITICAL ("Concerning") status
 *  - Deduplication via emergencyAlertSent flag in Room
 *  - Sending SMS via Android native SmsManager
 *  - Obtaining device location (optional, best-effort)
 *  - Opening the phone dialer (ACTION_DIAL)
 *
 * This component does NOT touch Bluetooth, sensors, Qwen, or report generation.
 * All failures are isolated — report generation is never affected.
 */
object EmergencyAlertManager {

    private const val TAG = "EmergencyAlert"
    private const val LOCATION_TIMEOUT_MS = 5_000L

    sealed class AlertResult {
        object Disabled         : AlertResult()
        object NotCritical      : AlertResult()
        object AlreadySent      : AlertResult()
        object InvalidContact   : AlertResult()
        data class SmsSent(val locationIncluded: Boolean) : AlertResult()
        data class SmsFailed(val reason: String)          : AlertResult()
        object SmsPermissionDenied                        : AlertResult()
    }

    /**
     * Entry point — called after a report is successfully saved.
     * Safe to call from any coroutine; never throws.
     */
    suspend fun maybeAlert(context: Context, reportId: Long): AlertResult {
        return try {
            maybeAlertInternal(context, reportId)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in EmergencyAlertManager (non-fatal): ${e.message}", e)
            AlertResult.SmsFailed("Unexpected error: ${e.message}")
        }
    }

    private suspend fun maybeAlertInternal(context: Context, reportId: Long): AlertResult {
        val pref = EmergencyAlertPreference(context)

        // 1. Check master toggle
        if (!pref.alertsEnabled.first()) {
            Log.d(TAG, "Emergency alerts disabled — skipping")
            return AlertResult.Disabled
        }

        // 2. Load report
        val db     = HealthDatabase.getInstance(context)
        val report = db.healthReportDao().getById(reportId) ?: run {
            Log.w(TAG, "Report $reportId not found — skipping")
            return AlertResult.Disabled
        }

        // 3. Only trigger for "Concerning" (CRITICAL) status
        if (report.overallStatus != "Concerning") {
            Log.d(TAG, "Status=${report.overallStatus} — not critical, no alert")
            return AlertResult.NotCritical
        }

        // 4. Deduplication — one alert per report, survives app restart
        if (report.emergencyAlertSent) {
            Log.d(TAG, "Report $reportId already alerted — skipping duplicate")
            return AlertResult.AlreadySent
        }

        // 5. Validate contact number
        val number = pref.contactNumber.first()
        if (!pref.isValidNumber(number)) {
            Log.w(TAG, "Emergency alerts ON but contact number invalid: '$number'")
            return AlertResult.InvalidContact
        }

        // 6. Mark sent BEFORE attempting — prevents duplicate on crash/retry
        db.healthReportDao().markEmergencyAlertSent(reportId)

        // 7. SMS
        val smsEnabled = pref.smsEnabled.first()
        val smsResult: AlertResult = if (smsEnabled) {
            sendSms(context, pref, report, number)
        } else {
            AlertResult.Disabled
        }

        // 8. Call alert — ACTION_CALL if permission granted, ACTION_DIAL fallback
        if (pref.callEnabled.first()) {
            placeCall(context, number)
        }

        return smsResult
    }

    private suspend fun sendSms(
        context: Context,
        pref: EmergencyAlertPreference,
        report: HealthReportEntity,
        number: String
    ): AlertResult {
        // Check SMS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS permission denied")
            return AlertResult.SmsPermissionDenied
        }

        // Optionally get location
        var location: Location? = null
        if (pref.locationEnabled.first()) {
            location = tryGetLocation(context)
        }

        val message = buildSmsMessage(report, location)

        return try {
            @Suppress("DEPRECATION")
            val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?: return AlertResult.SmsFailed("SmsManager unavailable on this device")
            } else {
                SmsManager.getDefault()
                    ?: return AlertResult.SmsFailed("SmsManager.getDefault() returned null")
            }
            // Split if message exceeds single SMS length
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(number, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            }
            Log.i(TAG, "Emergency SMS sent to $number (location=${location != null})")
            AlertResult.SmsSent(locationIncluded = location != null)
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed: ${e.message}", e)
            AlertResult.SmsFailed(e.message ?: "Unknown SMS error")
        }
    }

    private fun buildSmsMessage(report: HealthReportEntity, location: Location?): String {
        val timeFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
        val time    = timeFmt.format(Date(report.sessionEnd))

        val sb = StringBuilder()
        sb.appendLine("⚠️ G-ONE Health Alert")
        sb.appendLine()
        sb.appendLine("A potentially critical health pattern was detected during a health monitoring session.")
        sb.appendLine()

        report.bpmAverage?.let { sb.appendLine("Heart Rate: ${"%.0f".format(it)} bpm") }
        report.emgAverage?.let { sb.appendLine("EMG: ${"%.0f".format(it)} ADC") }
        sb.appendLine("Time: $time")

        if (location != null) {
            sb.appendLine()
            sb.appendLine("📍 Location:")
            sb.appendLine("https://maps.google.com/?q=${location.latitude},${location.longitude}")
        }

        sb.appendLine()
        sb.appendLine("Please check on the person. If they are experiencing severe or concerning symptoms, seek medical assistance immediately.")
        sb.appendLine()
        sb.append("— G-ONE Health Monitor")

        return sb.toString()
    }

    /**
     * Best-effort location fetch with a hard timeout.
     * Returns null if permission denied, GPS unavailable, or timeout exceeded.
     * Location failure NEVER prevents SMS from being sent.
     */
    private suspend fun tryGetLocation(context: Context): Location? {
        val fineGranted   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            Log.d(TAG, "Location permission not granted — SMS will be sent without location")
            return null
        }
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val cts    = CancellationTokenSource()
                val client = LocationServices.getFusedLocationProviderClient(context)
                val priority = if (fineGranted) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
                client.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { loc -> cont.resume(loc) }
                    .addOnFailureListener { e ->
                        Log.d(TAG, "Location fetch failed: ${e.message} — SMS without location")
                        cont.resume(null)
                    }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }.also {
            if (it == null) Log.d(TAG, "Location timed out — SMS without location")
        }
    }

    /**
     * Places a direct call if CALL_PHONE is granted; falls back to ACTION_DIAL otherwise.
     * Failure never affects SMS or report generation.
     */
    private fun placeCall(context: Context, number: String) {
        try {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
            val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, if (hasPermission) "Call placed to $number" else "Dialer opened (CALL_PHONE not granted) for $number")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place call: ${e.message}", e)
        }
    }
}
