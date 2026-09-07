package com.infinity.ai.bluetooth

import com.infinity.ai.health.data.VitalsReading

/**
 * Modular packet parser for HC-05 / Arduino serial data.
 *
 * Default expected format (change ONLY this file when Arduino format changes):
 *   HR:72,SPO2:98,TEMP:36.5,MOT:0,FALL:0\n
 *
 * Fields are key:value pairs separated by commas.
 * Any missing field is treated as null (sensor error / not yet available).
 */
object VitalsPacketParser {

    fun parse(raw: String): VitalsReading? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val fields = trimmed.split(",").associate { part ->
                val idx = part.indexOf(':')
                if (idx < 0) part.trim() to ""
                else part.substring(0, idx).trim() to part.substring(idx + 1).trim()
            }

            VitalsReading(
                heartRate       = fields["HR"]?.toIntOrNull(),
                spo2            = fields["SPO2"]?.toIntOrNull(),
                temperature     = fields["TEMP"]?.toFloatOrNull(),
                motionDetected  = fields["MOT"] == "1",
                fallDetected    = fields["FALL"] == "1",
                rawPacket       = trimmed
            )
        } catch (e: Exception) {
            null
        }
    }
}
