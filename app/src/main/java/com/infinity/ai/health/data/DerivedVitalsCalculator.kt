package com.infinity.ai.health.data

/**
 * DerivedVitalsCalculator
 *
 * Calculates estimated Temperature and SpO2 from available real sensor data.
 *
 * IMPORTANT:
 *  - These are ESTIMATES, not physical sensor readings.
 *  - Every result is tagged ValueSource.DERIVED or ValueSource.UNAVAILABLE.
 *  - Never label results as "Measured" — always "Estimated".
 *  - When real sensors are added, replace the body of calculateTemperature()
 *    and calculateSpO2() with real sensor reads; the rest of the architecture
 *    (SessionManager, HealthRepository, ViewModel, UI) requires zero changes.
 */
object DerivedVitalsCalculator {

    data class DerivedValue<T>(val value: T?, val source: ValueSource)

    /**
     * Estimate body temperature from EMG activity and BPM.
     *
     * Physiological basis (heuristic only):
     *  - Elevated muscle activity (high EMG) correlates with increased metabolic heat.
     *  - Elevated heart rate can indicate fever or exertion.
     *  - Baseline is 36.6 °C (normal resting).
     *
     * Returns UNAVAILABLE if insufficient data.
     */
    fun calculateTemperature(
        avgEmg: Float?,
        avgBpm: Float?
    ): DerivedValue<Float> {
        if (avgEmg == null && avgBpm == null) {
            return DerivedValue(null, ValueSource.UNAVAILABLE)
        }

        var estimate = 36.6f  // normal resting baseline

        // EMG contribution: high sustained muscle activity raises core temp slightly
        if (avgEmg != null) {
            val emgNorm = (avgEmg / 1023f).coerceIn(0f, 1f)
            estimate += emgNorm * 1.2f   // max +1.2 °C at full EMG
        }

        // BPM contribution: tachycardia can indicate fever
        if (avgBpm != null && avgBpm > 0f) {
            val bpmDelta = (avgBpm - 70f).coerceAtLeast(0f)
            estimate += (bpmDelta / 100f) * 0.8f  // max +0.8 °C at BPM=150
        }

        return DerivedValue(estimate.coerceIn(35.0f, 41.0f), ValueSource.DERIVED)
    }

    /**
     * Estimate SpO2 from BPM and EMG.
     *
     * Physiological basis (heuristic only):
     *  - Normal resting SpO2 is 97–99%.
     *  - Very high heart rate or extreme EMG activity may indicate reduced O2 delivery.
     *
     * Returns UNAVAILABLE if insufficient data.
     */
    fun calculateSpO2(
        avgEmg: Float?,
        avgBpm: Float?
    ): DerivedValue<Int> {
        if (avgEmg == null && avgBpm == null) {
            return DerivedValue(null, ValueSource.UNAVAILABLE)
        }

        var estimate = 98f  // normal resting baseline

        // High BPM can correlate with slightly reduced SpO2 under exertion
        if (avgBpm != null && avgBpm > 0f) {
            val bpmDelta = (avgBpm - 80f).coerceAtLeast(0f)
            estimate -= (bpmDelta / 100f) * 4f  // max -4% at BPM=180
        }

        // Very high EMG (spasm/fatigue) may indicate reduced perfusion
        if (avgEmg != null) {
            val emgNorm = (avgEmg / 1023f).coerceIn(0f, 1f)
            if (emgNorm > 0.7f) estimate -= (emgNorm - 0.7f) * 6f  // max -1.8% at full EMG
        }

        return DerivedValue(estimate.toInt().coerceIn(88, 100), ValueSource.DERIVED)
    }
}
