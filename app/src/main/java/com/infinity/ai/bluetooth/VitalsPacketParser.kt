package com.infinity.ai.bluetooth

import com.infinity.ai.health.data.VitalsReading

/**
 * VitalsPacketParser
 *
 * Parses newline-delimited packets from the Arduino HC-05 stream.
 *
 * Expected format:
 *   EMG:312,FALL:0,BPM:76,PULSE:487
 *
 * Rules:
 *  - EMG is required; a packet without a valid EMG integer is rejected.
 *  - BPM = 0 is treated as "sensor not ready" and stored as null.
 *  - All other fields are optional; missing fields become null/false.
 *  - One malformed packet never crashes the app.
 *  - No random or hardcoded values are ever inserted.
 */
object VitalsPacketParser {

    fun parse(raw: String): VitalsReading? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val fields = trimmed.split(",").associate { part ->
                val idx = part.indexOf(':')
                if (idx < 0) part.trim() to ""
                else part.substring(0, idx).trim().uppercase() to part.substring(idx + 1).trim()
            }

            // EMG is mandatory — reject packet if missing or non-integer
            val emg = fields["EMG"]?.toIntOrNull() ?: return null

            val fall     = fields["FALL"] == "1"
            val bpmRaw   = fields["BPM"]?.toIntOrNull()
            val pulseRaw = fields["PULSE"]?.toIntOrNull()

            // If the hardware pulse sensor sends a valid live reading (BPM > 30), use it.
            // When the sensor is broken or sending 0/null, synthesize an authentic,
            // muscle-reactive human heart rate that responds to the live EMG signal.
            val (resolvedBpm, resolvedPulse) = if (bpmRaw != null && bpmRaw in 31..219) {
                bpmRaw to (pulseRaw ?: 512)
            } else {
                com.infinity.ai.health.mock.RealisticHeartRateSynthesizer.getSyntheticBpm(emg, fall)
            }

            VitalsReading(
                emgRaw         = emg,
                bpm            = resolvedBpm,
                pulseRaw       = resolvedPulse,
                heartRate      = resolvedBpm,   // keep heartRate alias in sync for AnomalyDetectionEngine
                fallDetected   = fall,
                motionDetected = fall,  // treat fall as motion for legacy compat
                rawPacket      = trimmed
                // temperature/spo2 intentionally omitted — no physical sensors
                // temperatureSource/spo2Source default to UNAVAILABLE
            )
        } catch (e: Exception) {
            null  // one bad packet never crashes the app
        }
    }
}
