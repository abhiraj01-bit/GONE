package com.infinity.ai.health.repository

import android.util.Log
import com.infinity.ai.health.data.DerivedVitalsCalculator
import com.infinity.ai.health.data.HealthSession
import com.infinity.ai.health.data.SessionReport
import com.infinity.ai.health.data.ValueSource
import com.infinity.ai.health.data.VitalsReading

/**
 * SessionManager
 *
 * Owns the five-point representative aggregation logic and compact summary building.
 *
 * FIVE-POINT SELECTION RULE:
 *  - N < 5  → return null (insufficient data, never fabricate)
 *  - N = 5  → use all five readings directly
 *  - N > 5  → divide timeline into 5 equal segments; compute window-average per segment
 *
 * The five points represent: Start → 25% → 50% → 75% → End
 *
 * EMG and BPM use window-averages so a single noisy packet doesn't skew the report.
 * BPM = 0 / null readings are excluded from BPM averages.
 */
class SessionManager {

    companion object {
        private const val TAG = "SessionManager"
        const val MIN_READINGS_FOR_REPORT = 5
    }

    data class RepresentativePoint(
        val label: String,          // "Start", "25%", "50%", "75%", "End"
        val timestamp: Long,
        val avgEmg: Float?,
        val avgBpm: Float?,         // null if no valid BPM in this segment
        val fallDetected: Boolean
    )

    /**
     * Build a SessionReport from all readings collected during [session].
     * Returns null if fewer than MIN_READINGS_FOR_REPORT valid readings exist.
     */
    fun buildReport(
        session: HealthSession,
        readings: List<VitalsReading>
    ): SessionReport? {
        val valid = readings.filter { it.emgRaw != null }
        Log.i(TAG, "buildReport: ${valid.size} valid readings for session ${session.id}")

        if (valid.size < MIN_READINGS_FOR_REPORT) {
            Log.w(TAG, "Insufficient readings (${valid.size} < $MIN_READINGS_FOR_REPORT)")
            return null
        }

        val points = selectFivePoints(valid)
        val endTime = session.endTime ?: System.currentTimeMillis()
        val durationMin = ((endTime - session.startTime) / 60_000L).toInt()

        val allEmg = valid.mapNotNull { it.emgRaw }
        val allBpm = valid.mapNotNull { it.bpm }.filter { it > 0 }

        val avgEmgAll = if (allEmg.isNotEmpty()) allEmg.average().toFloat() else null
        val avgBpmAll = if (allBpm.isNotEmpty()) allBpm.average().toFloat() else null

        val tempResult = DerivedVitalsCalculator.calculateTemperature(avgEmgAll, avgBpmAll)
        val spo2Result = DerivedVitalsCalculator.calculateSpO2(avgEmgAll, avgBpmAll)

        val fallCount = valid.count { it.fallDetected }

        return SessionReport(
            sessionId             = session.id,
            startTime             = session.startTime,
            endTime               = endTime,
            durationMinutes       = durationMin,
            readingCount          = valid.size,
            representativeEmgJson = buildEmgJson(points),
            averageEmg            = avgEmgAll,
            minEmg                = allEmg.minOrNull(),
            maxEmg                = allEmg.maxOrNull(),
            representativeBpmJson = buildBpmJson(points),
            averageBpm            = avgBpmAll,
            minBpm                = allBpm.minOrNull(),
            maxBpm                = allBpm.maxOrNull(),
            temperature           = tempResult.value,
            temperatureSource     = tempResult.source,
            spo2                  = spo2Result.value,
            spo2Source            = spo2Result.source,
            fallEventCount        = fallCount,
            fivePointSummary      = buildFivePointSummary(points)
        )
    }

    /**
     * Build the compact JSON summary sent to Qwen.
     * Contains ONLY pre-calculated statistics — never raw packet arrays.
     * Clearly labels DERIVED fields so the AI never treats them as sensor measurements.
     */
    fun buildCompactSummaryJson(report: SessionReport): String = buildString {
        append("{")
        append("\"session_id\":${report.sessionId},")
        append("\"duration_minutes\":${report.durationMinutes},")
        append("\"total_readings\":${report.readingCount},")
        append("\"emg\":{")
        append("\"average\":${report.averageEmg ?: "null"},")
        append("\"min\":${report.minEmg ?: "null"},")
        append("\"max\":${report.maxEmg ?: "null"},")
        append("\"source\":\"LIVE_SENSOR\",")
        append("\"five_points\":${report.representativeEmgJson}")
        append("},")
        append("\"heart_rate\":{")
        append("\"average\":${report.averageBpm ?: "null"},")
        append("\"min\":${report.minBpm ?: "null"},")
        append("\"max\":${report.maxBpm ?: "null"},")
        append("\"source\":\"LIVE_SENSOR\",")
        append("\"five_points\":${report.representativeBpmJson}")
        append("},")
        append("\"temperature\":{")
        append("\"value\":${report.temperature ?: "null"},")
        append("\"source\":\"${report.temperatureSource.name}\",")
        append("\"note\":\"ESTIMATED_NOT_MEASURED\"")
        append("},")
        append("\"spo2\":{")
        append("\"value\":${report.spo2 ?: "null"},")
        append("\"source\":\"${report.spo2Source.name}\",")
        append("\"note\":\"ESTIMATED_NOT_MEASURED\"")
        append("},")
        append("\"fall_events\":${report.fallEventCount}")
        append("}")
    }

    /**
     * Build the representative readings JSON for HealthReportEntity storage.
     * Format: [{label, emg, bpm, status}]
     */
    fun buildRepresentativeReadingsJson(report: SessionReport): String {
        val emgPoints = parseSimpleJsonArray(report.representativeEmgJson)
        val bpmPoints = parseSimpleJsonArray(report.representativeBpmJson)
        val labels = listOf("Start", "25%", "50%", "75%", "End")
        return "[" + labels.mapIndexed { i, label ->
            val emg = emgPoints.getOrNull(i)
            val bpm = bpmPoints.getOrNull(i)
            val emgVal = emg?.get("value")?.let { if (it == "null") null else it.toFloatOrNull() }
            val bpmVal = bpm?.get("value")?.let { if (it == "null") null else it.toFloatOrNull() }
            val status = emgStatusLabel(emgVal)
            "{\"label\":\"$label\"," +
            "\"emg\":${emgVal?.let { "%.0f".format(it) } ?: "null"}," +
            "\"bpm\":${bpmVal?.let { "%.0f".format(it) } ?: "null"}," +
            "\"status\":\"$status\"}"
        }.joinToString(",") + "]"
    }

    // ── Five-point selection ───────────────────────────────────────────────────

    fun selectFivePoints(readings: List<VitalsReading>): List<RepresentativePoint> {
        val n = readings.size
        if (n == 5) {
            return readings.mapIndexed { i, r ->
                RepresentativePoint(
                    label        = pointLabel(i),
                    timestamp    = r.timestamp,
                    avgEmg       = r.emgRaw?.toFloat(),
                    avgBpm       = r.bpm?.takeIf { it > 0 }?.toFloat(),
                    fallDetected = r.fallDetected
                )
            }
        }

        val segmentSize = n / 5.0
        return (0 until 5).map { seg ->
            val fromIdx = (seg * segmentSize).toInt()
            val toIdx   = if (seg == 4) n else ((seg + 1) * segmentSize).toInt()
            val window  = readings.subList(fromIdx.coerceIn(0, n - 1), toIdx.coerceIn(1, n))

            val emgVals = window.mapNotNull { it.emgRaw }
            val bpmVals = window.mapNotNull { it.bpm }.filter { it > 0 }

            RepresentativePoint(
                label        = pointLabel(seg),
                timestamp    = window[window.size / 2].timestamp,
                avgEmg       = if (emgVals.isNotEmpty()) emgVals.average().toFloat() else null,
                avgBpm       = if (bpmVals.isNotEmpty()) bpmVals.average().toFloat() else null,
                fallDetected = window.any { it.fallDetected }
            )
        }
    }

    private fun pointLabel(index: Int) = when (index) {
        0    -> "Start"
        1    -> "25%"
        2    -> "50%"
        3    -> "75%"
        4    -> "End"
        else -> "Point ${index + 1}"
    }

    private fun emgStatusLabel(emg: Float?): String = when {
        emg == null -> "No Data"
        emg >= 900  -> "Spasm"
        emg >= 700  -> "Elevated"
        emg >= 400  -> "Active"
        else        -> "Normal"
    }

    // ── Formatting helpers ─────────────────────────────────────────────────────

    private fun buildFivePointSummary(points: List<RepresentativePoint>): String =
        points.joinToString("\n\n") { p ->
            val emgStr = p.avgEmg?.let { "%.0f".format(it) } ?: "--"
            val bpmStr = p.avgBpm?.let { "%.0f".format(it) } ?: "--"
            val fallStr = if (p.fallDetected) " ⚠ Fall detected" else ""
            "Point — ${p.label}\nEMG: $emgStr  BPM: $bpmStr$fallStr"
        }

    private fun buildEmgJson(points: List<RepresentativePoint>): String =
        "[" + points.joinToString(",") { p ->
            "{\"label\":\"${p.label}\",\"value\":${p.avgEmg ?: "null"}}"
        } + "]"

    private fun buildBpmJson(points: List<RepresentativePoint>): String =
        "[" + points.joinToString(",") { p ->
            "{\"label\":\"${p.label}\",\"value\":${p.avgBpm ?: "null"}}"
        } + "]"

    /** Minimal JSON array parser for [{label,value}] format — avoids a JSON library dep. */
    private fun parseSimpleJsonArray(json: String): List<Map<String, String>> {
        return try {
            val result = mutableListOf<Map<String, String>>()
            val objects = json.trim().removePrefix("[").removeSuffix("]").split("},{")
            for (obj in objects) {
                val clean = obj.trim().removePrefix("{").removeSuffix("}")
                val map = mutableMapOf<String, String>()
                clean.split(",").forEach { pair ->
                    val kv = pair.split(":")
                    if (kv.size >= 2) {
                        val k = kv[0].trim().removeSurrounding("\"")
                        val v = kv.drop(1).joinToString(":").trim().removeSurrounding("\"")
                        map[k] = v
                    }
                }
                result.add(map)
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Kept for backward compat with HealthRepository legacy call
    fun buildReportJson(report: SessionReport): String = buildCompactSummaryJson(report)
}
