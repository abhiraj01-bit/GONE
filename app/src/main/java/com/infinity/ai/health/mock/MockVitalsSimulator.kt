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
    NORMAL      ("Normal",          "HR 65–80, SpO₂ 97–99, Temp 36.4–36.8°C"),
    HIGH_HR     ("High Heart Rate", "HR ramps to 130+ → triggers critical alert"),
    LOW_SPO2    ("Low SpO₂",        "SpO₂ drops to 88% → triggers critical alert"),
    FEVER       ("Fever",           "Temp rises to 39.5°C → triggers critical alert"),
    LOW_HR      ("Low Heart Rate",  "HR drops to 38 bpm → triggers critical alert"),
    FALL_EVENT  ("Fall Detected",   "Sends a fall event immediately"),
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
                heartRate      = (72 + wave * 4 + jitter(3.0)).toInt().coerceIn(60, 85),
                spo2           = (98 + jitter(1.0)).toInt().coerceIn(97, 100),
                temperature    = (36.6f + wave * 0.1f + jitter(0.1).toFloat()).coerceIn(36.2f, 37.0f),
                motionDetected = t % 8 < 3,
                fallDetected   = false,
                rawPacket      = "MOCK:NORMAL"
            )
            SimulatorScenario.HIGH_HR -> {
                val hr = (75 + (t * 3.5).coerceAtMost(65.0) + jitter(3.0)).toInt()
                VitalsReading(
                    heartRate      = hr.coerceIn(70, 145),
                    spo2           = (97 + jitter(1.0)).toInt().coerceIn(95, 99),
                    temperature    = (36.8f + jitter(0.1).toFloat()).coerceIn(36.5f, 37.2f),
                    motionDetected = true,
                    fallDetected   = false,
                    rawPacket      = "MOCK:HIGH_HR t=$t"
                )
            }
            SimulatorScenario.LOW_SPO2 -> {
                val spo2 = (98 - (t * 0.8).coerceAtMost(11.0) + jitter(1.0)).toInt()
                VitalsReading(
                    heartRate      = (78 + jitter(4.0)).toInt().coerceIn(70, 90),
                    spo2           = spo2.coerceIn(86, 99),
                    temperature    = (36.7f + jitter(0.1).toFloat()),
                    motionDetected = false,
                    fallDetected   = false,
                    rawPacket      = "MOCK:LOW_SPO2 t=$t"
                )
            }
            SimulatorScenario.FEVER -> {
                val temp = (36.8f + t * 0.15f + jitter(0.05).toFloat()).coerceIn(36.5f, 40.0f)
                VitalsReading(
                    heartRate      = (80 + t + jitter(3.0)).toInt().coerceIn(75, 115),
                    spo2           = (97 + jitter(1.0)).toInt().coerceIn(95, 99),
                    temperature    = temp,
                    motionDetected = t % 5 < 2,
                    fallDetected   = false,
                    rawPacket      = "MOCK:FEVER t=$t"
                )
            }
            SimulatorScenario.LOW_HR -> {
                val hr = (65 - (t * 2.0).coerceAtMost(30.0) + jitter(2.0)).toInt()
                VitalsReading(
                    heartRate      = hr.coerceIn(33, 68),
                    spo2           = (96 + jitter(1.0)).toInt().coerceIn(94, 99),
                    temperature    = (36.5f + jitter(0.1).toFloat()),
                    motionDetected = false,
                    fallDetected   = false,
                    rawPacket      = "MOCK:LOW_HR t=$t"
                )
            }
            SimulatorScenario.FALL_EVENT -> VitalsReading(
                heartRate      = 95,
                spo2           = 96,
                temperature    = 36.7f,
                motionDetected = true,
                fallDetected   = true,
                rawPacket      = "MOCK:FALL"
            )
            SimulatorScenario.STRESS_TEST -> {
                val phase = (t / 5) % 4
                when (phase) {
                    0    -> VitalsReading(heartRate = 140, spo2 = 97,  temperature = 36.8f, motionDetected = true,  fallDetected = false, rawPacket = "MOCK:STRESS_HR")
                    1    -> VitalsReading(heartRate = 75,  spo2 = 88,  temperature = 36.8f, motionDetected = false, fallDetected = false, rawPacket = "MOCK:STRESS_SPO2")
                    2    -> VitalsReading(heartRate = 75,  spo2 = 97,  temperature = 39.5f, motionDetected = false, fallDetected = false, rawPacket = "MOCK:STRESS_TEMP")
                    else -> VitalsReading(heartRate = 75,  spo2 = 97,  temperature = 36.8f, motionDetected = true,  fallDetected = true,  rawPacket = "MOCK:STRESS_FALL")
                }
            }
        }
    }
}
