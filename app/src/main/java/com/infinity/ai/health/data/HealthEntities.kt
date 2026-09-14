package com.infinity.ai.health.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── Source labels — every health value must carry one of these ────────────────
enum class ValueSource { LIVE_SENSOR, DERIVED, UNAVAILABLE }

/**
 * VitalsReading — one packet from the Arduino hardware.
 *
 * REAL SENSOR fields  : emgRaw, bpm, pulseRaw, fallDetected
 * DERIVED fields      : temperature, spo2  (marked via temperatureSource / spo2Source)
 * sessionId           : links reading to an active HealthSession (null = legacy/no session)
 */
@Entity(tableName = "vitals_readings")
data class VitalsReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: Long? = null,

    // ── REAL SENSOR ───────────────────────────────────────────────────────────
    val emgRaw: Int? = null,          // Arduino A0 — raw ADC 0-1023
    val bpm: Int? = null,             // Arduino A1 Pulse Sensor Amped — beats per minute
    val pulseRaw: Int? = null,        // Arduino A1 — raw pulse signal
    val fallDetected: Boolean = false,
    val motionDetected: Boolean = false,

    // ── DERIVED / ESTIMATED ───────────────────────────────────────────────────
    // These are NOT physical sensor readings. Source must always be set.
    val temperature: Float? = null,
    val temperatureSource: ValueSource = ValueSource.UNAVAILABLE,
    val spo2: Int? = null,
    val spo2Source: ValueSource = ValueSource.UNAVAILABLE,

    // ── Legacy / debug ────────────────────────────────────────────────────────
    val heartRate: Int? = null,       // alias kept for AnomalyDetectionEngine compat
    val rawPacket: String = ""
)

/**
 * HealthSession — one user-initiated monitoring session.
 */
@Entity(tableName = "health_sessions")
data class HealthSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val readingCount: Int = 0,
    val isActive: Boolean = true
)

/**
 * SessionReport — generated at END SESSION from the five representative points.
 * Stored once per session.
 */
@Entity(tableName = "session_reports")
data class SessionReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val generatedAt: Long = System.currentTimeMillis(),
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Int,
    val readingCount: Int,

    // Five representative EMG points (JSON array string)
    val representativeEmgJson: String = "",
    val averageEmg: Float? = null,
    val minEmg: Int? = null,
    val maxEmg: Int? = null,

    // Five representative BPM points (JSON array string)
    val representativeBpmJson: String = "",
    val averageBpm: Float? = null,
    val minBpm: Int? = null,
    val maxBpm: Int? = null,

    // Derived vitals
    val temperature: Float? = null,
    val temperatureSource: ValueSource = ValueSource.UNAVAILABLE,
    val spo2: Int? = null,
    val spo2Source: ValueSource = ValueSource.UNAVAILABLE,

    val fallEventCount: Int = 0,
    val aiAnalysis: String = "",

    // Human-readable five-point summary (pre-formatted for display)
    val fivePointSummary: String = ""
)

/**
 * HealthReportEntity — the fully-rendered, persisted report saved after Qwen analysis.
 * Loaded instantly from Room when viewing old reports — NO Qwen call on load.
 */
@Entity(tableName = "health_reports")
data class HealthReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val sessionStart: Long,
    val sessionEnd: Long,
    val durationMinutes: Int,
    val totalReadings: Int,

    // Locally-calculated stats (never from Qwen)
    val emgAverage: Float? = null,
    val emgMin: Int? = null,
    val emgMax: Int? = null,
    val bpmAverage: Float? = null,
    val bpmMin: Int? = null,
    val bpmMax: Int? = null,
    val fallEventCount: Int = 0,

    // Derived vitals
    val temperature: Float? = null,
    val temperatureSource: ValueSource = ValueSource.UNAVAILABLE,
    val spo2: Int? = null,
    val spo2Source: ValueSource = ValueSource.UNAVAILABLE,

    // Five representative points as JSON: [{label,emg,bpm,status}]
    val representativeReadingsJson: String = "",

    // Qwen-generated structured fields (parsed from JSON response)
    val aiSummary: String = "",
    val observationsJson: String = "",        // JSON array of strings
    val physicalConcernsJson: String = "",    // JSON array of {title,description,severity}
    val foodRecommendationsJson: String = "", // JSON array of strings
    val exerciseRecommendationsJson: String = "", // JSON array of strings
    val lifestyleRecommendationsJson: String = "", // JSON array of strings
    val medicalAttentionJson: String = "",    // JSON array of strings
    val overallStatus: String = "Unknown",   // "Normal" | "Needs Attention" | "Concerning"

    // Raw Qwen JSON response (kept for debugging; never displayed directly)
    val rawAiJson: String = ""
)

@Entity(tableName = "anomaly_events")
data class AnomalyEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val severity: String,
    val heartRate: Int?,
    val spo2: Int?,
    val temperature: Float?,
    val emgRaw: Int? = null,
    val motionDetected: Boolean,
    val durationMinutes: Int = 0,
    val trend: String = "stable",
    val aiExplanation: String = "",
    val acknowledged: Boolean = false
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val address: String,
    val name: String,
    val lastConnected: Long = System.currentTimeMillis(),
    val isPrimary: Boolean = true
)
