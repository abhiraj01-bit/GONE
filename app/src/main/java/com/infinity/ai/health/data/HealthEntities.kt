package com.infinity.ai.health.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vitals_readings")
data class VitalsReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val heartRate: Int?,        // bpm, null if sensor error
    val spo2: Int?,             // %, null if sensor error
    val temperature: Float?,    // °C, null if sensor error
    val motionDetected: Boolean = false,
    val fallDetected: Boolean = false,
    val rawPacket: String = ""  // original packet for debugging
)

@Entity(tableName = "anomaly_events")
data class AnomalyEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,          // e.g. "low_spo2", "high_hr", "fall_detected"
    val severity: String,           // "warning" | "critical"
    val heartRate: Int?,
    val spo2: Int?,
    val temperature: Float?,
    val motionDetected: Boolean,
    val durationMinutes: Int = 0,
    val trend: String = "stable",   // "stable" | "improving" | "declining"
    val aiExplanation: String = "", // filled after Qwen generates explanation
    val acknowledged: Boolean = false
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val address: String, // Bluetooth MAC address
    val name: String,
    val lastConnected: Long = System.currentTimeMillis(),
    val isPrimary: Boolean = true
)
