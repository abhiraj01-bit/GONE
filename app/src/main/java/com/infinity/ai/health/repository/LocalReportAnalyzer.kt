package com.infinity.ai.health.repository

import com.infinity.ai.health.anomaly.AnomalyThresholds
import com.infinity.ai.health.data.SessionReport
import com.infinity.ai.health.data.ValueSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * LocalReportAnalyzer
 *
 * Generates a complete health session analysis from pre-computed SessionReport statistics.
 * Pure Kotlin — no AI call, no network, runs in < 5ms.
 *
 * Replaces the Qwen LLM call for instant report generation.
 * Output format matches the HealthReportEntity JSON fields exactly.
 */
object LocalReportAnalyzer {

    data class AnalysisResult(
        val summary                      : String,
        val overallStatus                : String,
        val observationsJson             : String,
        val physicalConcernsJson         : String,
        val foodRecommendationsJson      : String,
        val exerciseRecommendationsJson  : String,
        val lifestyleRecommendationsJson : String,
        val medicalAttentionJson         : String
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    fun analyze(report: SessionReport): AnalysisResult {
        val emgAvg   = report.averageEmg
        val emgMax   = report.maxEmg?.toFloat()
        val bpmAvg   = report.averageBpm
        val bpmMin   = report.minBpm
        val bpmMax   = report.maxBpm
        val temp     = report.temperature
        val spo2     = report.spo2
        val falls    = report.fallEventCount
        val duration = report.durationMinutes
        val readings = report.readingCount

        val overallStatus = computeStatus(emgAvg, emgMax, bpmAvg, bpmMin, bpmMax, temp, spo2, falls)
        val observations  = buildObservations(emgAvg, emgMax, bpmAvg, bpmMin, bpmMax, temp, report.temperatureSource, spo2, report.spo2Source, falls, duration, readings)
        val concerns      = buildConcerns(emgAvg, emgMax, bpmAvg, bpmMin, bpmMax, temp, spo2, falls)
        val summary       = buildSummary(overallStatus, emgAvg, bpmAvg, falls, duration, readings)
        val food          = buildFoodRecs(overallStatus, bpmAvg, emgAvg)
        val exercise      = buildExerciseRecs(overallStatus, emgAvg, bpmAvg)
        val lifestyle     = buildLifestyleRecs(overallStatus, falls)
        val medical       = buildMedicalAttention(overallStatus, bpmAvg, bpmMin, bpmMax, falls, temp, spo2)

        return AnalysisResult(
            summary                      = summary,
            overallStatus                = overallStatus,
            observationsJson             = jsonArray(observations),
            physicalConcernsJson         = jsonConcerns(concerns),
            foodRecommendationsJson      = jsonArray(food),
            exerciseRecommendationsJson  = jsonArray(exercise),
            lifestyleRecommendationsJson = jsonArray(lifestyle),
            medicalAttentionJson         = jsonArray(medical)
        )
    }

    // ── Status ─────────────────────────────────────────────────────────────────

    private fun computeStatus(
        emgAvg: Float?, emgMax: Float?,
        bpmAvg: Float?, bpmMin: Int?, bpmMax: Int?,
        temp: Float?, spo2: Int?,
        falls: Int
    ): String {
        if (falls > 0) return "Concerning"
        if (bpmMin != null && bpmMin <= AnomalyThresholds.HR_LOW_CRITICAL)  return "Concerning"
        if (bpmMax != null && bpmMax >= AnomalyThresholds.HR_HIGH_CRITICAL) return "Concerning"
        if (emgMax != null && emgMax >= AnomalyThresholds.EMG_HIGH_CRITICAL) return "Concerning"
        if (temp   != null && temp   >= AnomalyThresholds.TEMP_HIGH_CRITICAL) return "Concerning"
        if (spo2   != null && spo2   <= AnomalyThresholds.SPO2_LOW_CRITICAL) return "Concerning"

        if (bpmAvg != null && (bpmAvg < AnomalyThresholds.HR_LOW_WARNING || bpmAvg > AnomalyThresholds.HR_HIGH_WARNING)) return "Needs Attention"
        if (emgAvg != null && emgAvg >= AnomalyThresholds.EMG_HIGH_WARNING) return "Needs Attention"
        if (temp   != null && (temp  >= AnomalyThresholds.TEMP_HIGH_WARNING || temp <= AnomalyThresholds.TEMP_LOW_WARNING)) return "Needs Attention"
        if (spo2   != null && spo2   <= AnomalyThresholds.SPO2_LOW_WARNING) return "Needs Attention"

        return "Normal"
    }

    // ── Summary ────────────────────────────────────────────────────────────────

    private fun buildSummary(
        status: String, emgAvg: Float?, bpmAvg: Float?,
        falls: Int, duration: Int, readings: Int
    ): String {
        val dStr   = if (duration < 1) "under a minute" else "$duration min"
        val emgStr = emgAvg?.let { "avg EMG %.0f ADC".format(it) } ?: "EMG data recorded"
        val bpmStr = bpmAvg?.let { "avg HR %.0f bpm".format(it) } ?: "no heart rate data"
        val fallStr = if (falls > 0) " A fall event was detected." else ""
        return when (status) {
            "Normal"          -> "Session of $dStr, $readings readings. Values within normal range — $emgStr, $bpmStr.$fallStr"
            "Needs Attention" -> "Session of $dStr, $readings readings. Some values may warrant attention — $emgStr, $bpmStr.$fallStr Review the details below."
            "Concerning"      -> "Session of $dStr, $readings readings. One or more readings are outside safe range — $emgStr, $bpmStr.$fallStr Please review and seek medical guidance if symptoms persist."
            else              -> "Session of $dStr, $readings readings. $emgStr, $bpmStr.$fallStr"
        }
    }

    // ── Observations ───────────────────────────────────────────────────────────

    private fun buildObservations(
        emgAvg: Float?, emgMax: Float?,
        bpmAvg: Float?, bpmMin: Int?, bpmMax: Int?,
        temp: Float?, tempSource: ValueSource,
        spo2: Int?, spo2Source: ValueSource,
        falls: Int, duration: Int, readings: Int
    ): List<String> {
        val obs = mutableListOf<String>()

        if (emgAvg != null) obs += when {
            emgAvg >= AnomalyThresholds.EMG_HIGH_CRITICAL    -> "Average EMG (%.0f ADC) indicates very high muscle activity — may suggest spasm or intense contraction.".format(emgAvg)
            emgAvg >= AnomalyThresholds.EMG_HIGH_WARNING     -> "Average EMG (%.0f ADC) suggests muscle fatigue patterns.".format(emgAvg)
            emgAvg >= AnomalyThresholds.EMG_ACTIVE_THRESHOLD -> "Average EMG (%.0f ADC) indicates active muscle contraction.".format(emgAvg)
            else                                              -> "Average EMG (%.0f ADC) remained in resting range.".format(emgAvg)
        }

        if (bpmAvg != null) obs += when {
            bpmAvg >= AnomalyThresholds.HR_HIGH_CRITICAL -> "Average heart rate (%.0f bpm) was above normal range.".format(bpmAvg)
            bpmAvg >= AnomalyThresholds.HR_HIGH_WARNING  -> "Average heart rate (%.0f bpm) was slightly elevated.".format(bpmAvg)
            bpmAvg <= AnomalyThresholds.HR_LOW_CRITICAL  -> "Average heart rate (%.0f bpm) was significantly below normal.".format(bpmAvg)
            bpmAvg <= AnomalyThresholds.HR_LOW_WARNING   -> "Average heart rate (%.0f bpm) was on the lower side of normal.".format(bpmAvg)
            else                                          -> "Average heart rate (%.0f bpm) remained within normal range.".format(bpmAvg)
        }

        if (bpmMin != null && bpmMax != null && (bpmMax - bpmMin) > 40)
            obs += "Heart rate varied widely between $bpmMin and $bpmMax bpm."

        if (temp != null && tempSource != ValueSource.UNAVAILABLE) obs += when {
            temp >= AnomalyThresholds.TEMP_HIGH_CRITICAL -> "Estimated temperature (${"%.1f".format(temp)}°C) suggests elevated body heat (estimated, not directly measured)."
            temp >= AnomalyThresholds.TEMP_HIGH_WARNING  -> "Estimated temperature (${"%.1f".format(temp)}°C) is slightly above normal (estimated, not directly measured)."
            temp <= AnomalyThresholds.TEMP_LOW_WARNING   -> "Estimated temperature (${"%.1f".format(temp)}°C) is below normal (estimated, not directly measured)."
            else                                         -> "Estimated temperature (${"%.1f".format(temp)}°C) is within normal range."
        }

        if (spo2 != null && spo2Source != ValueSource.UNAVAILABLE) obs += when {
            spo2 <= AnomalyThresholds.SPO2_LOW_CRITICAL -> "Estimated SpO₂ ($spo2%) is critically low (estimated from sensor patterns, not directly measured)."
            spo2 <= AnomalyThresholds.SPO2_LOW_WARNING  -> "Estimated SpO₂ ($spo2%) may be below optimal (estimated, not directly measured)."
            else                                         -> "Estimated SpO₂ ($spo2%) appears within acceptable range."
        }

        if (falls > 0) obs += "$falls fall event(s) were detected during the session."
        obs += "Session captured $readings readings."
        return obs
    }

    // ── Physical Concerns ──────────────────────────────────────────────────────

    private data class Concern(val title: String, val description: String, val severity: String)

    private fun buildConcerns(
        emgAvg: Float?, emgMax: Float?,
        bpmAvg: Float?, bpmMin: Int?, bpmMax: Int?,
        temp: Float?, spo2: Int?,
        falls: Int
    ): List<Concern> {
        val list = mutableListOf<Concern>()

        if (falls > 0) list += Concern("Fall Event Detected",
            "A fall was detected. Falls may indicate balance or coordination issues.", "high")

        if (bpmMax != null && bpmMax >= AnomalyThresholds.HR_HIGH_CRITICAL)
            list += Concern("Elevated Heart Rate", "Heart rate reached $bpmMax bpm — may indicate cardiovascular stress.", "high")
        else if (bpmAvg != null && bpmAvg >= AnomalyThresholds.HR_HIGH_WARNING)
            list += Concern("Slightly Elevated Heart Rate", "Avg HR ${"%.0f".format(bpmAvg)} bpm was above normal resting range.", "moderate")

        if (bpmMin != null && bpmMin <= AnomalyThresholds.HR_LOW_CRITICAL)
            list += Concern("Low Heart Rate", "Heart rate dropped to $bpmMin bpm — may suggest bradycardia.", "high")

        if (emgMax != null && emgMax >= AnomalyThresholds.EMG_HIGH_CRITICAL)
            list += Concern("High Muscle Activity", "EMG peaked at ${"%.0f".format(emgMax)} ADC — may indicate intense contraction or spasm.", "moderate")

        if (temp != null && temp >= AnomalyThresholds.TEMP_HIGH_WARNING)
            list += Concern("Elevated Temperature (Estimated)", "Estimated ${"%.1f".format(temp)}°C may indicate fever (not directly measured).",
                if (temp >= AnomalyThresholds.TEMP_HIGH_CRITICAL) "high" else "moderate")

        if (spo2 != null && spo2 <= AnomalyThresholds.SPO2_LOW_WARNING)
            list += Concern("Low SpO₂ (Estimated)", "Estimated SpO₂ $spo2% may be below optimal (not directly measured).",
                if (spo2 <= AnomalyThresholds.SPO2_LOW_CRITICAL) "high" else "moderate")

        return list
    }

    // ── Recommendations ────────────────────────────────────────────────────────

    private fun buildFoodRecs(status: String, bpmAvg: Float?, emgAvg: Float?): List<String> = when (status) {
        "Concerning"      -> listOf("Consult a healthcare professional for dietary guidance.", "Avoid stimulants such as caffeine or energy drinks.", "Stay well-hydrated and maintain electrolyte balance.")
        "Needs Attention" -> listOf("Increase potassium-rich foods (bananas, leafy greens) to support muscle and heart function.", "Ensure adequate magnesium intake for muscle recovery.", "Stay well-hydrated — dehydration may worsen EMG and HR patterns.")
        else              -> listOf("Maintain a balanced diet with adequate protein for muscle recovery.", "Stay well-hydrated before and after monitoring sessions.")
    }

    private fun buildExerciseRecs(status: String, emgAvg: Float?, bpmAvg: Float?): List<String> = when (status) {
        "Concerning"      -> listOf("Limit strenuous exercise until you have spoken with a medical professional.", "Light walking or gentle stretching only.")
        "Needs Attention" -> listOf("Reduce exercise intensity temporarily and monitor your response.", "Include gentle warm-up and cool-down periods.", "Avoid high-intensity activities until readings normalise.")
        else              -> listOf("Continue your current activity routine — readings indicate healthy patterns.", "Incorporate regular stretching to maintain muscle flexibility.")
    }

    private fun buildLifestyleRecs(status: String, falls: Int): List<String> {
        val recs = mutableListOf<String>()
        if (falls > 0) recs += "A fall was detected. Review your environment for hazards and speak with your doctor."
        recs += when (status) {
            "Concerning"      -> listOf("Seek medical guidance promptly if you experience ongoing symptoms.", "Monitor vital signs more frequently and log any physical symptoms.")
            "Needs Attention" -> listOf("Increase monitoring frequency to track whether patterns improve.", "Prioritise adequate sleep and stress management.", "Consider logging daily activities to identify triggers.")
            else              -> listOf("Continue regular monitoring sessions to track trends.", "Maintain consistent sleep patterns to support cardiovascular and muscular health.")
        }
        return recs
    }

    private fun buildMedicalAttention(
        status: String, bpmAvg: Float?, bpmMin: Int?, bpmMax: Int?,
        falls: Int, temp: Float?, spo2: Int?
    ): List<String> {
        if (status == "Normal") return emptyList()
        val list = mutableListOf<String>()
        if (falls > 0)         list += "Seek attention if you experienced dizziness, pain, or loss of consciousness during the fall."
        if (bpmMax != null && bpmMax >= AnomalyThresholds.HR_HIGH_CRITICAL) list += "Consult a doctor if you experience palpitations, chest discomfort, or shortness of breath."
        if (bpmMin != null && bpmMin <= AnomalyThresholds.HR_LOW_CRITICAL)  list += "Seek medical attention if you experience fainting, dizziness, or extreme fatigue alongside low HR."
        if (temp   != null && temp   >= AnomalyThresholds.TEMP_HIGH_CRITICAL) list += "If you have a confirmed high temperature with other symptoms, seek medical evaluation."
        if (spo2   != null && spo2   <= AnomalyThresholds.SPO2_LOW_CRITICAL) list += "If you experience difficulty breathing or confusion, seek emergency medical help immediately."
        if (list.isEmpty()) list += "If patterns persist across multiple sessions or you have physical symptoms, consult your healthcare provider."
        return list
    }

    // ── JSON helpers ───────────────────────────────────────────────────────────

    private fun jsonArray(items: List<String>): String =
        JSONArray().apply { items.forEach { put(it) } }.toString()

    private fun jsonConcerns(concerns: List<Concern>): String =
        JSONArray().apply {
            concerns.forEach { c ->
                put(JSONObject().apply {
                    put("title",       c.title)
                    put("description", c.description)
                    put("severity",    c.severity)
                })
            }
        }.toString()
}
