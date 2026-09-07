package com.infinity.ai.health.anomaly

/**
 * All medical thresholds live here — change this file only.
 * Qwen never reads these; the anomaly engine uses them exclusively.
 */
object AnomalyThresholds {
    // Heart rate (bpm)
    const val HR_LOW_CRITICAL  = 40
    const val HR_LOW_WARNING   = 50
    const val HR_HIGH_WARNING  = 100
    const val HR_HIGH_CRITICAL = 130

    // SpO2 (%)
    const val SPO2_LOW_CRITICAL = 90
    const val SPO2_LOW_WARNING  = 94

    // Temperature (°C)
    const val TEMP_LOW_WARNING   = 35.0f
    const val TEMP_HIGH_WARNING  = 37.8f
    const val TEMP_HIGH_CRITICAL = 39.0f

    // Sustained-reading window before confirming anomaly
    const val SUSTAINED_WINDOW = 3   // consecutive readings
}
