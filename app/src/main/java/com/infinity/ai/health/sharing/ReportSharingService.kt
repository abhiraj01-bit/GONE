package com.infinity.ai.health.sharing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.*
import com.infinity.ai.BuildConfig
import com.infinity.ai.data.ReportSharingPreference
import com.infinity.ai.health.data.HealthDatabase
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val RESEND_ENDPOINT = "https://api.resend.com/emails"
private const val FROM_ADDRESS    = "Infinity Health <onboarding@resend.dev>"

object ReportSharingService {

    private const val TAG = "ReportSharing"

    suspend fun maybeShareReport(context: Context, reportId: Long) {
        val pref    = ReportSharingPreference(context)
        val enabled = pref.sharingEnabled.first()
        val email   = pref.sharingEmail.first()

        if (!enabled) return
        if (!pref.isValidEmail(email)) {
            Log.d(TAG, "Sharing enabled but email invalid — skipping")
            return
        }

        val db     = HealthDatabase.getInstance(context)
        val report = db.healthReportDao().getById(reportId) ?: return

        val subject = ReportEmailBuilder.buildSubject(report)
        val html    = ReportEmailBuilder.buildHtml(report)

        if (hasInternet(context)) {
            db.healthReportDao().updateEmailStatus(reportId, "SENDING", null, email)
            val ok = sendViaResend(subject, html, email)
            if (ok) {
                db.healthReportDao().updateEmailStatus(reportId, "SENT", System.currentTimeMillis(), email)
                Log.i(TAG, "Report $reportId emailed to $email")
            } else {
                db.healthReportDao().updateEmailStatus(reportId, "FAILED", null, email)
                Log.w(TAG, "Report $reportId email failed — enqueuing retry")
                enqueueRetry(context, reportId, email)
            }
        } else {
            db.healthReportDao().updateEmailStatus(reportId, "PENDING", null, email)
            Log.i(TAG, "No internet — report $reportId marked PENDING")
            enqueueRetry(context, reportId, email)
        }
    }

    fun hasInternet(context: Context): Boolean {
        val cm   = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net  = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Posts directly to Resend REST API. Returns true on 2xx. */
    fun sendViaResend(subject: String, html: String, to: String): Boolean {
        val apiKey = BuildConfig.RESEND_API_KEY
        if (apiKey.isBlank()) {
            Log.e(TAG, "RESEND_API_KEY is not set in local.properties")
            return false
        }
        return try {
            val body = JSONObject().apply {
                put("from", FROM_ADDRESS)
                put("to",   JSONArray().put(to))
                put("subject", subject)
                put("html", html)
            }.toString().toRequestBody("application/json".toMediaType())

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(RESEND_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    Log.e(TAG, "Resend HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendViaResend error: ${e.message}")
            false
        }
    }

    private fun enqueueRetry(context: Context, reportId: Long, email: String) {
        val data = workDataOf(
            EmailRetryWorker.KEY_REPORT_ID to reportId,
            EmailRetryWorker.KEY_EMAIL     to email
        )
        val request = OneTimeWorkRequestBuilder<EmailRetryWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "email_retry_$reportId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

class EmailRetryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_REPORT_ID = "report_id"
        const val KEY_EMAIL     = "email"
    }

    override suspend fun doWork(): Result {
        val reportId = inputData.getLong(KEY_REPORT_ID, -1L)
        val email    = inputData.getString(KEY_EMAIL) ?: return Result.failure()
        if (reportId < 0) return Result.failure()

        val db     = HealthDatabase.getInstance(applicationContext)
        val report = db.healthReportDao().getById(reportId) ?: return Result.failure()

        db.healthReportDao().updateEmailStatus(reportId, "SENDING", null, email)
        val ok = ReportSharingService.sendViaResend(
            ReportEmailBuilder.buildSubject(report),
            ReportEmailBuilder.buildHtml(report),
            email
        )
        return if (ok) {
            db.healthReportDao().updateEmailStatus(reportId, "SENT", System.currentTimeMillis(), email)
            Result.success()
        } else {
            db.healthReportDao().updateEmailStatus(reportId, "FAILED", null, email)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
