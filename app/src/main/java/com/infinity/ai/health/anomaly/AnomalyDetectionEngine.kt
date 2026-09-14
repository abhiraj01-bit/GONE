package com.infinity.ai.health.anomaly

import com.infinity.ai.health.data.AnomalyEvent
import com.infinity.ai.health.data.VitalsReading

data class DetectedAnomaly(
    val eventType : String,
    val severity  : String,   // "warning" | "critical"
    val trend     : String,   // "stable" | "increasing" | "declining"
    val reading   : VitalsReading
)

class AnomalyDetectionEngine {

    private val window = ArrayDeque<VitalsReading>(AnomalyThresholds.SUSTAINED_WINDOW + 1)

    /** Clear the rolling window — call when switching data source (BT ↔ simulator). */
    fun reset() = window.clear()

    fun process(reading: VitalsReading): DetectedAnomaly? {
        window.addLast(reading)
        if (window.size > AnomalyThresholds.SUSTAINED_WINDOW) window.removeFirst()
        if (window.size < AnomalyThresholds.SUSTAINED_WINDOW) return null

        // Fall is immediate — no window needed
        if (reading.fallDetected) {
            return DetectedAnomaly("fall_detected", "critical", "stable", reading)
        }

        checkHeartRate()?.let { return it }
        checkSpo2()?.let { return it }
        checkTemperature()?.let { return it }
        checkEmg()?.let { return it }
        return null
    }

    private fun checkHeartRate(): DetectedAnomaly? {
        val hrs = window.mapNotNull { it.heartRate }
        if (hrs.size < AnomalyThresholds.SUSTAINED_WINDOW) return null
        val avg = hrs.average()
        val trend = trend(hrs.map { it.toDouble() })
        return when {
            avg <= AnomalyThresholds.HR_LOW_CRITICAL  -> DetectedAnomaly("low_hr",  "critical", trend, window.last())
            avg <= AnomalyThresholds.HR_LOW_WARNING   -> DetectedAnomaly("low_hr",  "warning",  trend, window.last())
            avg >= AnomalyThresholds.HR_HIGH_CRITICAL -> DetectedAnomaly("high_hr", "critical", trend, window.last())
            avg >= AnomalyThresholds.HR_HIGH_WARNING  -> DetectedAnomaly("high_hr", "warning",  trend, window.last())
            else -> null
        }
    }

    private fun checkSpo2(): DetectedAnomaly? {
        val vals = window.mapNotNull { it.spo2 }
        if (vals.size < AnomalyThresholds.SUSTAINED_WINDOW) return null
        val avg = vals.average()
        val trend = trend(vals.map { it.toDouble() })
        return when {
            avg <= AnomalyThresholds.SPO2_LOW_CRITICAL -> DetectedAnomaly("low_spo2", "critical", trend, window.last())
            avg <= AnomalyThresholds.SPO2_LOW_WARNING  -> DetectedAnomaly("low_spo2", "warning",  trend, window.last())
            else -> null
        }
    }

    private fun checkTemperature(): DetectedAnomaly? {
        val vals = window.mapNotNull { it.temperature?.toDouble() }
        if (vals.size < AnomalyThresholds.SUSTAINED_WINDOW) return null
        val avg = vals.average()
        val trend = trend(vals)
        return when {
            avg >= AnomalyThresholds.TEMP_HIGH_CRITICAL -> DetectedAnomaly("high_temp", "critical", trend, window.last())
            avg >= AnomalyThresholds.TEMP_HIGH_WARNING  -> DetectedAnomaly("high_temp", "warning",  trend, window.last())
            avg <= AnomalyThresholds.TEMP_LOW_WARNING   -> DetectedAnomaly("low_temp",  "warning",  trend, window.last())
            else -> null
        }
    }

    private fun checkEmg(): DetectedAnomaly? {
        val vals = window.mapNotNull { it.emgRaw }
        if (vals.size < AnomalyThresholds.SUSTAINED_WINDOW) return null
        val avg = vals.average()
        val trend = trend(vals.map { it.toDouble() })
        return when {
            avg >= AnomalyThresholds.EMG_HIGH_CRITICAL -> DetectedAnomaly("emg_spasm",   "critical", trend, window.last())
            avg >= AnomalyThresholds.EMG_HIGH_WARNING  -> DetectedAnomaly("emg_fatigue", "warning",  trend, window.last())
            else -> null
        }
    }

    private fun trend(values: List<Double>): String {
        if (values.size < 2) return "stable"
        val delta = values.last() - values.first()
        return when {
            delta > 2.0  -> "increasing"
            delta < -2.0 -> "declining"
            else         -> "stable"
        }
    }

    fun toAnomalyEvent(a: DetectedAnomaly, durationMinutes: Int = 0): AnomalyEvent =
        AnomalyEvent(
            eventType       = a.eventType,
            severity        = a.severity,
            heartRate       = a.reading.heartRate,
            spo2            = a.reading.spo2,
            temperature     = a.reading.temperature,
            emgRaw          = a.reading.emgRaw,
            motionDetected  = a.reading.motionDetected,
            durationMinutes = durationMinutes,
            trend           = a.trend
        )

    fun toExplanationJson(a: DetectedAnomaly, durationMinutes: Int = 0): String {
        val r = a.reading
        return """{"event":"${a.eventType}","severity":"${a.severity}","heart_rate":${r.heartRate},"spo2":${r.spo2},"temperature":${r.temperature},"emg_raw":${r.emgRaw},"motion":${r.motionDetected},"fall":${r.fallDetected},"duration_minutes":$durationMinutes,"trend":"${a.trend}"}"""
    }
}
