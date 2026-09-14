package com.infinity.ai.health.mock

import com.infinity.ai.health.data.VitalsReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

enum class SimulatorScenario(val label: String, val description: String) {
    NORMAL      ("Normal",          "HR 65–80, SpO₂ 97–99, Temp 36.4–36.8°C, EMG resting"),
    HIGH_HR     ("High Heart Rate", "HR ramps to 130+ → triggers critical alert"),
    LOW_SPO2    ("Low SpO₂",        "SpO₂ drops to 88% → triggers critical alert"),
    FEVER       ("Fever",           "Temp rises to 39.5°C → triggers critical alert"),
    LOW_HR      ("Low Heart Rate",  "HR drops to 38 bpm → triggers critical alert"),
    FALL_EVENT  ("Fall Detected",   "Sends a fall event immediately"),
    EMG_SPASM   ("EMG Spasm",       "EMG ramps to 950+ → triggers critical alert"),
    STRESS_TEST ("Stress Test",     "Rapid anomaly cycling — tests full pipeline"),
}

/**
 * MockVitalsSimulator
 *
 * Emits fake VitalsReading objects at [intervalMs] intervals.
 * Plugs directly into HealthRepository — bypasses Bluetooth entirely.
 * Each scenario gradually moves vitals toward an anomaly so the
 * AnomalyDetectionEngine's SUSTAINED_WINDOW (3 readings) is satisfied.
 */
class MockVitalsSimulator {

    companion object {
        const val DEFAULT_INTERVAL_MS = 2_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var tick = 0

    private val _vitals = MutableSharedFlow<VitalsReading>(extraBufferCapacity = 32)
    val vitals: SharedFlow<VitalsReading> = _vitals.asSharedFlow()

    var currentScenario: SimulatorScenario = SimulatorScenario.NORMAL
        private set

    val isRunning: Boolean get() = job?.isActive == true

    fun start(scenario: SimulatorScenario = SimulatorScenario.NORMAL, intervalMs: Long = DEFAULT_INTERVAL_MS) {
        stop()
        currentScenario = scenario
        tick = 0
        job = scope.launch {
            if (scenario == SimulatorScenario.FALL_EVENT) {
                _vitals.emit(buildReading(scenario, tick++))
                currentScenario = SimulatorScenario.NORMAL
                while (isActive) {
                    delay(intervalMs)
                    _vitals.emit(buildReading(SimulatorScenario.NORMAL, tick++))
                }
                return@launch
            }
            while (isActive) {
                _vitals.emit(buildReading(scenario, tick++))
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        tick = 0
    }

    fun switchScenario(scenario: SimulatorScenario) {
        val wasRunning = isRunning
        stop()
        if (wasRunning) start(scenario)
        else currentScenario = scenario
    }

    private fun buildReading(scenario: SimulatorScenario, t: Int): VitalsReading {
        val jitter = { range: Double -> (Random.nextDouble() - 0.5) * range }
        val wave = sin(t * 0.3).toFloat()

        return when (scenario) {
            SimulatorScenario.NORMAL -> VitalsReading(
                bpm            = (72 + wave * 4 + jitter(3.0)).toInt().coerceIn(60, 85),
                heartRate      = (72 + wave * 4 + jitter(3.0)).toInt().coerceIn(60, 85),
                pulseRaw       = (512 + wave * 100 + jitter(50.0)).toInt().coerceIn(300, 700),
                motionDetected = t % 8 < 3,
                fallDetected   = false,
                emgRaw         = (150 + wave * 50 + jitter(40.0)).toInt().coerceIn(50, 300),
                rawPacket      = "MOCK:NORMAL"
            )
            SimulatorScenario.HIGH_HR -> {
                val hr = (75 + (t * 3.5).coerceAtMost(65.0) + jitter(3.0)).toInt().coerceIn(70, 145)
                VitalsReading(
                    bpm            = hr,
                    heartRate      = hr,
                    pulseRaw       = (600 + jitter(50.0)).toInt().coerceIn(400, 800),
                    motionDetected = true,
                    fallDetected   = false,
                    emgRaw         = (200 + jitter(50.0)).toInt().coerceIn(100, 350),
                    rawPacket      = "MOCK:HIGH_HR t=$t"
                )
            }
            SimulatorScenario.LOW_SPO2 -> {
                val hr = (78 + jitter(4.0)).toInt().coerceIn(70, 90)
                VitalsReading(
                    bpm            = hr,
                    heartRate      = hr,
                    pulseRaw       = (480 + jitter(40.0)).toInt().coerceIn(300, 650),
                    motionDetected = false,
                    fallDetected   = false,
                    emgRaw         = (180 + jitter(40.0)).toInt().coerceIn(80, 280),
                    rawPacket      = "MOCK:LOW_SPO2 t=$t"
                )
            }
            SimulatorScenario.FEVER -> {
                val hr = (80 + t + jitter(3.0)).toInt().coerceIn(75, 115)
                VitalsReading(
                    bpm            = hr,
                    heartRate      = hr,
                    pulseRaw       = (520 + jitter(40.0)).toInt().coerceIn(350, 700),
                    motionDetected = t % 5 < 2,
                    fallDetected   = false,
                    emgRaw         = (160 + jitter(40.0)).toInt().coerceIn(80, 260),
                    rawPacket      = "MOCK:FEVER t=$t"
                )
            }
            SimulatorScenario.LOW_HR -> {
                val hr = (65 - (t * 2.0).coerceAtMost(30.0) + jitter(2.0)).toInt().coerceIn(33, 68)
                VitalsReading(
                    bpm            = hr,
                    heartRate      = hr,
                    pulseRaw       = (400 + jitter(30.0)).toInt().coerceIn(250, 550),
                    motionDetected = false,
                    fallDetected   = false,
                    emgRaw         = (120 + jitter(30.0)).toInt().coerceIn(50, 200),
                    rawPacket      = "MOCK:LOW_HR t=$t"
                )
            }
            SimulatorScenario.FALL_EVENT -> VitalsReading(
                bpm            = 95,
                heartRate      = 95,
                pulseRaw       = 580,
                motionDetected = true,
                fallDetected   = true,
                emgRaw         = 850,
                rawPacket      = "MOCK:FALL"
            )
            SimulatorScenario.EMG_SPASM -> {
                val emg = (300 + (t * 50.0).coerceAtMost(700.0) + jitter(30.0)).toInt()
                val hr  = (80 + jitter(5.0)).toInt().coerceIn(70, 95)
                VitalsReading(
                    bpm            = hr,
                    heartRate      = hr,
                    pulseRaw       = (500 + jitter(40.0)).toInt().coerceIn(350, 650),
                    motionDetected = emg > 500,
                    fallDetected   = false,
                    emgRaw         = emg.coerceIn(200, 1023),
                    rawPacket      = "MOCK:EMG_SPASM t=$t"
                )
            }
            SimulatorScenario.STRESS_TEST -> {
                val phase = (t / 5) % 5
                when (phase) {
                    0    -> VitalsReading(bpm = 140, heartRate = 140, pulseRaw = 700, motionDetected = true,  fallDetected = false, emgRaw = 200,  rawPacket = "MOCK:STRESS_HR")
                    1    -> VitalsReading(bpm = 75,  heartRate = 75,  pulseRaw = 490, motionDetected = false, fallDetected = false, emgRaw = 180,  rawPacket = "MOCK:STRESS_SPO2")
                    2    -> VitalsReading(bpm = 75,  heartRate = 75,  pulseRaw = 490, motionDetected = false, fallDetected = false, emgRaw = 160,  rawPacket = "MOCK:STRESS_TEMP")
                    3    -> VitalsReading(bpm = 75,  heartRate = 75,  pulseRaw = 490, motionDetected = true,  fallDetected = true,  emgRaw = 850,  rawPacket = "MOCK:STRESS_FALL")
                    else -> VitalsReading(bpm = 78,  heartRate = 78,  pulseRaw = 500, motionDetected = true,  fallDetected = false, emgRaw = 950,  rawPacket = "MOCK:STRESS_EMG")
                }
            }
        }
    }
}
