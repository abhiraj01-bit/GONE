package com.infinity.ai.health.repository

import android.content.Context
import android.util.Log
import com.infinity.ai.ai.repository.AIRepository
import com.infinity.ai.bluetooth.BluetoothManager
import com.infinity.ai.bluetooth.BtState
import com.infinity.ai.health.anomaly.AnomalyDetectionEngine
import com.infinity.ai.health.data.*
import com.infinity.ai.health.mock.MockVitalsSimulator
import com.infinity.ai.health.mock.SimulatorScenario
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class HealthRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "HealthRepository"

        @Volatile private var INSTANCE: HealthRepository? = null
        fun getInstance(context: Context): HealthRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HealthRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val db        = HealthDatabase.getInstance(context)
    private val btManager = BluetoothManager.getInstance(context)
    private val aiRepo    = AIRepository.getInstance(context)
    private val anomalyEngine = AnomalyDetectionEngine()
    private val scope     = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val simulator         = MockVitalsSimulator()

    private val _mockBtState = MutableStateFlow<BtState>(BtState.Disconnected)
    var sessionStartTime: Long = 0L
        private set

    val btState: StateFlow<BtState> = combine(btManager.state, _mockBtState) { real, mock ->
        if (simulator.isRunning) mock else real
    }.stateIn(scope, SharingStarted.Eagerly, BtState.Disconnected)

    val latestVitals: Flow<VitalsReading?> = db.vitalsDao().getLatest()
    val recentVitals: Flow<List<VitalsReading>> = db.vitalsDao().getRecent(100)
    val recentAnomalies: Flow<List<AnomalyEvent>> = db.anomalyDao().getRecent(50)
    val unacknowledgedAlerts: Flow<List<AnomalyEvent>> = db.anomalyDao().getUnacknowledged()
    val pairedDevices: List<Pair<String, String>> get() = btManager.getPairedDevices()

    init {
        scope.launch { btManager.vitals.collect { processReading(it) } }
        scope.launch { simulator.vitals.collect { processReading(it) } }
    }

    fun startSimulator(scenario: SimulatorScenario) {
        sessionStartTime = System.currentTimeMillis()
        _mockBtState.value = BtState.Connected("Mock Device (${scenario.label})")
        simulator.start(scenario)
    }

    fun stopSimulator() {
        simulator.stop()
        _mockBtState.value = BtState.Disconnected
    }

    fun switchSimulatorScenario(scenario: SimulatorScenario) {
        _mockBtState.value = BtState.Connected("Mock Device (${scenario.label})")
        simulator.switchScenario(scenario)
    }

    private suspend fun processReading(reading: VitalsReading) {
        db.vitalsDao().insert(reading)

        val anomaly = anomalyEngine.process(reading) ?: return
        Log.i(TAG, "Anomaly detected: ${anomaly.eventType} (${anomaly.severity})")

        val event = anomalyEngine.toAnomalyEvent(anomaly)
        val eventId = db.anomalyDao().insert(event)

        // Ask Qwen to explain — fire-and-forget, don't block vitals pipeline
        scope.launch {
            try {
                val json = anomalyEngine.toExplanationJson(anomaly)
                val explanation = aiRepo.generateHealthExplanation(json)
                db.anomalyDao().update(event.copy(id = eventId, aiExplanation = explanation))
            } catch (e: Exception) {
                Log.w(TAG, "AI explanation failed: ${e.message}")
            }
        }
    }

    fun connectDevice(address: String, name: String) {
        scope.launch {
            db.deviceDao().upsert(DeviceEntity(address = address, name = name))
        }
        btManager.connect(address, name)
    }

    fun disconnectDevice() = btManager.disconnect()

    suspend fun acknowledgeAlert(id: Long) = db.anomalyDao().acknowledge(id)

    suspend fun getPrimaryDevice(): DeviceEntity? = db.deviceDao().getPrimaryDevice()

    fun isBluetoothEnabled() = btManager.isBluetoothEnabled()
    fun isBluetoothAvailable() = btManager.isBluetoothAvailable()

    suspend fun generateSessionReport(): String {
        val since = if (sessionStartTime > 0L) sessionStartTime
                    else System.currentTimeMillis() - 10 * 60 * 1000L
        val readings = db.vitalsDao().getSince(since)
        if (readings.isEmpty()) return "No vitals data recorded in this session."

        val avgHr   = readings.mapNotNull { it.heartRate }.let { if (it.isEmpty()) null else it.average() }
        val minHr   = readings.mapNotNull { it.heartRate }.minOrNull()
        val maxHr   = readings.mapNotNull { it.heartRate }.maxOrNull()
        val avgSpo2 = readings.mapNotNull { it.spo2 }.let { if (it.isEmpty()) null else it.average() }
        val minSpo2 = readings.mapNotNull { it.spo2 }.minOrNull()
        val avgTemp = readings.mapNotNull { it.temperature?.toDouble() }.let { if (it.isEmpty()) null else it.average() }
        val maxTemp = readings.mapNotNull { it.temperature?.toDouble() }.maxOrNull()
        val fallCount = readings.count { it.fallDetected }
        val durationMin = ((readings.maxOf { it.timestamp } - readings.minOf { it.timestamp }) / 60000).toInt()

        val json = buildString {
            append("{")
            append("\"session_duration_minutes\":$durationMin,")
            append("\"total_readings\":${readings.size},")
            append("\"heart_rate\":{\"avg\":${avgHr?.let { "%.1f".format(it) } ?: "null"},\"min\":$minHr,\"max\":$maxHr},")
            append("\"spo2\":{\"avg\":${avgSpo2?.let { "%.1f".format(it) } ?: "null"},\"min\":$minSpo2},")
            append("\"temperature\":{\"avg\":${avgTemp?.let { "%.2f".format(it) } ?: "null"},\"max\":$maxTemp},")
            append("\"fall_events\":$fallCount")
            append("}")
        }

        return try {
            aiRepo.generateSessionReport(json)
        } catch (e: Exception) {
            "Report generation failed: ${e.message}"
        }
    }

    suspend fun pruneOldReadings(keepDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - keepDays * 24 * 60 * 60 * 1000L
        db.vitalsDao().deleteOlderThan(cutoff)
    }
}
