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
import org.json.JSONObject

class HealthRepository private constructor(context: Context) {

    companion object {
        private const val TAG    = "HealthRepository"
        private const val RTAG   = "INFINITY_REPORT"
        // Qwen report generation timeout — 60 seconds max
        private const val QWEN_TIMEOUT_MS = 60_000L

        @Volatile private var INSTANCE: HealthRepository? = null
        fun getInstance(context: Context): HealthRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HealthRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val db            = HealthDatabase.getInstance(context)
    private val btManager     = BluetoothManager.getInstance(context)
    private val aiRepo        = AIRepository.getInstance(context)
    private val anomalyEngine = AnomalyDetectionEngine()
    private val sessionMgr    = SessionManager()
    private val scope         = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val simulator        = MockVitalsSimulator()
    private val _mockBtState = MutableStateFlow<BtState>(BtState.Disconnected)

    // ── Active session tracking ────────────────────────────────────────────────
    private var activeSessionId: Long? = null

    // ── BT state ──────────────────────────────────────────────────────────────
    val btState: StateFlow<BtState> = combine(btManager.state, _mockBtState) { real, mock ->
        if (simulator.isRunning) mock else real
    }.stateIn(scope, SharingStarted.Eagerly, BtState.Disconnected)

    // ── Room flows ─────────────────────────────────────────────────────────────
    val latestVitals: Flow<VitalsReading?>         = db.vitalsDao().getLatest()
    val recentVitals: Flow<List<VitalsReading>>    = db.vitalsDao().getRecent(100)
    val recentAnomalies: Flow<List<AnomalyEvent>>  = db.anomalyDao().getRecent(50)
    val unacknowledgedAlerts: Flow<List<AnomalyEvent>> = db.anomalyDao().getUnacknowledged()
    val allReports: Flow<List<HealthReportEntity>> = db.healthReportDao().getAllSortedByDate()

    val liveEmg: SharedFlow<VitalsReading> = btManager.vitals
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    val simulatorLive: SharedFlow<VitalsReading> = simulator.vitals
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    init {
        scope.launch {
            btManager.vitals.collect { reading ->
                Log.d("InfinityBT", "REPOSITORY_EMG=${reading.emgRaw} BPM=${reading.bpm}")
                processReading(reading, isSimulator = false)
            }
        }
        scope.launch {
            // FIX: simulator readings are also tagged with sessionId
            simulator.vitals.collect { reading ->
                processReading(reading, isSimulator = true)
            }
        }
        scope.launch {
            btManager.state.collect { state ->
                if (state is BtState.Disconnected) {
                    Log.i(TAG, "BT disconnected — session $activeSessionId paused")
                }
            }
        }
    }

    // ── Session lifecycle ──────────────────────────────────────────────────────

    suspend fun startSession(): Long {
        activeSessionId?.let { endSessionInternal(it, generateReport = false) }
        val session = HealthSession(startTime = System.currentTimeMillis(), isActive = true)
        val id = db.sessionDao().insertSession(session)
        activeSessionId = id
        anomalyEngine.reset()
        Log.i(RTAG, "Session started: id=$id")
        return id
    }

    suspend fun endSession(): Result<Long> {
        Log.d(RTAG, "========== REPORT GENERATION START ==========")
        val sid = activeSessionId
        if (sid == null) {
            Log.e(RTAG, "endSession called but activeSessionId is null")
            return Result.failure(Exception("No active session to end."))
        }
        Log.d(RTAG, "Session ID: $sid")
        return endSessionInternal(sid, generateReport = true)
    }

    private suspend fun endSessionInternal(
        sessionId: Long,
        generateReport: Boolean
    ): Result<Long> {
        val session = db.sessionDao().getSession(sessionId) ?: run {
            Log.e(RTAG, "Session $sessionId not found in Room")
            activeSessionId = null
            return Result.failure(Exception("Session not found in database."))
        }

        val endTime = System.currentTimeMillis()
        db.sessionDao().updateSession(session.copy(endTime = endTime, isActive = false))
        // Clear activeSessionId AFTER we have the session object — readings query uses sessionId directly
        if (sessionId == activeSessionId) activeSessionId = null

        if (!generateReport) return Result.success(-1L)

        // ── Step 1: Load readings ──────────────────────────────────────────────
        Log.d(RTAG, "Session ID: $sessionId")
        var readings = db.vitalsDao().getBySession(sessionId)
        Log.d(RTAG, "Session readings (by sessionId): ${readings.size}")

        // BUG FIX: Race condition — if startSession()'s Room INSERT ran after the
        // first readings arrived, those readings have sessionId=null.
        // Fall back to a timestamp-based query to recover them.
        if (readings.size < SessionManager.MIN_READINGS_FOR_REPORT) {
            Log.w(RTAG, "Only ${readings.size} sessionId-tagged readings found — falling back to timestamp range")
            val totalInDb = db.vitalsDao().getRecent(1000).first().size
            Log.w(RTAG, "Total readings in DB: $totalInDb. Session startTime=${session.startTime}")
            val fallback = db.vitalsDao().getSince(session.startTime)
            Log.d(RTAG, "Fallback by timestamp: ${fallback.size} readings found since ${session.startTime}")
            if (fallback.size > readings.size) {
                readings = fallback
                Log.d(RTAG, "Using timestamp-fallback readings: ${readings.size}")
            }
        }

        Log.d(RTAG, "Session readings: ${readings.size}")

        if (readings.size < SessionManager.MIN_READINGS_FOR_REPORT) {
            val msg = "Insufficient readings (${readings.size} collected, " +
                "minimum ${SessionManager.MIN_READINGS_FOR_REPORT} required for full analysis)."
            Log.w(RTAG, "$msg — saving partial report instead of failing")

            // BUG FIX: Save a partial report instead of returning failure.
            // The user should always see a report after ending a session,
            // even if there were too few readings for full analysis.
            val partialEntity = HealthReportEntity(
                sessionId       = sessionId,
                sessionStart    = session.startTime,
                sessionEnd      = endTime,
                durationMinutes = ((endTime - session.startTime) / 60_000L).toInt(),
                totalReadings   = readings.size,
                aiSummary       = "Insufficient data for a complete session analysis — only ${readings.size} reading(s) were captured " +
                    "(minimum ${SessionManager.MIN_READINGS_FOR_REPORT} required). " +
                    "Start a new session and keep the device connected for at least 10 seconds to generate a full report.",
                overallStatus   = "Unknown"
            )
            Log.d(RTAG, "Saving partial report to Room")
            val partialId = db.healthReportDao().insert(partialEntity)
            val verified  = db.healthReportDao().getById(partialId)
            if (verified == null) {
                Log.e(RTAG, "CRITICAL: Partial INSERT completed but report NOT FOUND for id=$partialId")
            } else {
                Log.d(RTAG, "PARTIAL REPORT VERIFIED IN ROOM: id=${verified.id} sessionId=${verified.sessionId}")
            }
            Log.d(RTAG, "========== REPORT GENERATION COMPLETE (PARTIAL) ==========")
            return Result.success(partialId)
        }

        val firstTs = readings.first().timestamp
        val lastTs  = readings.last().timestamp
        val emgCount = readings.count { it.emgRaw != null }
        val bpmCount = readings.count { it.bpm != null && it.bpm > 0 }
        Log.d(RTAG, "First reading: $firstTs  Last: $lastTs")
        Log.d(RTAG, "EMG readings: $emgCount  BPM readings: $bpmCount")

        // ── Step 2: Build local summary ────────────────────────────────────────
        Log.d(RTAG, "Creating session summary")
        val updatedSession = session.copy(endTime = endTime, isActive = false, readingCount = readings.size)
        val sessionReport = sessionMgr.buildReport(updatedSession, readings) ?: run {
            Log.e(RTAG, "SessionManager.buildReport returned null — insufficient valid EMG readings")
            return Result.failure(Exception("Could not aggregate session readings."))
        }
        db.sessionReportDao().insert(sessionReport)
        Log.d(RTAG, "Session summary created: avgEMG=${sessionReport.averageEmg} avgBPM=${sessionReport.averageBpm}")

        val repReadingsJson = sessionMgr.buildRepresentativeReadingsJson(sessionReport)

        // ── Step 3: LOCAL instant analysis (< 5ms) ────────────────────────────
        Log.d(RTAG, "Running LocalReportAnalyzer")
        val localAnalysis = LocalReportAnalyzer.analyze(sessionReport)
        Log.d(RTAG, "LocalReportAnalyzer complete: status=${localAnalysis.overallStatus}")

        // ── Step 4: Save report immediately with local analysis ────────────────
        Log.d(RTAG, "Saving report to Room (Phase 1 — local analysis)")
        val entity = HealthReportEntity(
            sessionId                    = sessionId,
            sessionStart                 = session.startTime,
            sessionEnd                   = endTime,
            durationMinutes              = sessionReport.durationMinutes,
            totalReadings                = sessionReport.readingCount,
            emgAverage                   = sessionReport.averageEmg,
            emgMin                       = sessionReport.minEmg,
            emgMax                       = sessionReport.maxEmg,
            bpmAverage                   = sessionReport.averageBpm,
            bpmMin                       = sessionReport.minBpm,
            bpmMax                       = sessionReport.maxBpm,
            fallEventCount               = sessionReport.fallEventCount,
            temperature                  = sessionReport.temperature,
            temperatureSource            = sessionReport.temperatureSource,
            spo2                         = sessionReport.spo2,
            spo2Source                   = sessionReport.spo2Source,
            representativeReadingsJson   = repReadingsJson,
            aiSummary                    = localAnalysis.summary,
            observationsJson             = localAnalysis.observationsJson,
            physicalConcernsJson         = localAnalysis.physicalConcernsJson,
            foodRecommendationsJson      = localAnalysis.foodRecommendationsJson,
            exerciseRecommendationsJson  = localAnalysis.exerciseRecommendationsJson,
            lifestyleRecommendationsJson = localAnalysis.lifestyleRecommendationsJson,
            medicalAttentionJson         = localAnalysis.medicalAttentionJson,
            overallStatus                = localAnalysis.overallStatus,
            rawAiJson                    = ""
        )
        val reportId = db.healthReportDao().insert(entity)
        Log.d(RTAG, "Phase 1 report saved — id=$reportId (local analysis, instant)")

        val saved1 = db.healthReportDao().getById(reportId)
        if (saved1 == null) {
            Log.e(RTAG, "CRITICAL: Phase 1 INSERT completed but report NOT FOUND for id=$reportId")
        } else {
            Log.d(RTAG, "PHASE 1 REPORT VERIFIED IN ROOM: id=${saved1.id}")
        }

        // ── Step 5: Background Qwen enhancement (non-blocking) ────────────────
        // Runs AFTER we return reportId. ReportViewScreen observes getByIdFlow()
        // so the UI updates automatically when Qwen finishes — no polling needed.
        val compactJson = sessionMgr.buildCompactSummaryJson(sessionReport)
        scope.launch {
            Log.d(RTAG, "Phase 2: Starting Qwen enhancement for report $reportId")
            try {
                if (!aiRepo.isReady()) {
                    Log.w(RTAG, "AI model not ready — attempting initialization")
                    aiRepo.initialize()
                }
                val raw = withTimeout(QWEN_TIMEOUT_MS) {
                    aiRepo.generateSessionReport(compactJson)
                }
                Log.d(RTAG, "Phase 2: Qwen response received (${raw.length} chars)")
                val cleaned = extractJsonObject(raw)
                val parsed  = parseAiJson(cleaned)
                Log.d(RTAG, "Phase 2: Qwen parse OK — status=${parsed.overallStatus}")

                db.healthReportDao().updateAiAnalysis(
                    id                           = reportId,
                    aiSummary                    = parsed.summary.ifBlank { localAnalysis.summary },
                    observationsJson             = if (parsed.observations.isNotBlank() && parsed.observations != "[]") parsed.observations else localAnalysis.observationsJson,
                    physicalConcernsJson         = if (parsed.physicalConcerns.isNotBlank() && parsed.physicalConcerns != "[]") parsed.physicalConcerns else localAnalysis.physicalConcernsJson,
                    foodRecommendationsJson      = if (parsed.foodRecs.isNotBlank() && parsed.foodRecs != "[]") parsed.foodRecs else localAnalysis.foodRecommendationsJson,
                    exerciseRecommendationsJson  = if (parsed.exerciseRecs.isNotBlank() && parsed.exerciseRecs != "[]") parsed.exerciseRecs else localAnalysis.exerciseRecommendationsJson,
                    lifestyleRecommendationsJson = if (parsed.lifestyleRecs.isNotBlank() && parsed.lifestyleRecs != "[]") parsed.lifestyleRecs else localAnalysis.lifestyleRecommendationsJson,
                    medicalAttentionJson         = if (parsed.medicalAttention.isNotBlank() && parsed.medicalAttention != "[]") parsed.medicalAttention else localAnalysis.medicalAttentionJson,
                    overallStatus                = parsed.overallStatus.ifBlank { localAnalysis.overallStatus },
                    rawAiJson                    = cleaned
                )
                Log.d(RTAG, "Phase 2: Qwen analysis patched into Room for report $reportId")
            } catch (e: TimeoutCancellationException) {
                Log.e(RTAG, "Phase 2: Qwen TIMED OUT — keeping local analysis", e)
            } catch (e: Exception) {
                Log.e(RTAG, "Phase 2: Qwen FAILED: ${e.javaClass.simpleName}: ${e.message} — keeping local analysis", e)
            }
        }

        Log.d(RTAG, "========== REPORT GENERATION COMPLETE (Phase 1 done, Phase 2 running) ==========")
        return Result.success(reportId)
    }


    fun getActiveSessionId(): Long? = activeSessionId
    fun isSessionActive(): Boolean  = activeSessionId != null

    suspend fun cancelSession() {
        activeSessionId?.let { endSessionInternal(it, generateReport = false) }
    }

    suspend fun getReport(reportId: Long): HealthReportEntity? {
        Log.d(RTAG, "Opening saved report: $reportId")
        return db.healthReportDao().getById(reportId)
    }

    fun getReportFlow(reportId: Long): Flow<HealthReportEntity?> =
        db.healthReportDao().getByIdFlow(reportId)

    // ── Reading processing ─────────────────────────────────────────────────────

    private suspend fun processReading(reading: VitalsReading, isSimulator: Boolean) {
        // FIX: tag ALL readings (real AND simulator) with the active session ID
        val sid = activeSessionId
        val taggedReading = if (sid != null) reading.copy(sessionId = sid) else reading

        db.vitalsDao().insert(taggedReading)

        val anomaly = anomalyEngine.process(taggedReading) ?: return
        Log.i(TAG, "Anomaly: ${anomaly.eventType} (${anomaly.severity})")

        val event   = anomalyEngine.toAnomalyEvent(anomaly)
        val eventId = db.anomalyDao().insert(event)

        scope.launch {
            try {
                val json        = anomalyEngine.toExplanationJson(anomaly)
                val explanation = aiRepo.generateHealthExplanation(json)
                db.anomalyDao().update(event.copy(id = eventId, aiExplanation = explanation))
            } catch (e: Exception) {
                Log.w(TAG, "AI explanation failed: ${e.message}")
            }
        }
    }

    // ── Simulator ─────────────────────────────────────────────────────────────

    fun startSimulator(scenario: SimulatorScenario) {
        anomalyEngine.reset()
        scope.launch { db.vitalsDao().deleteAll() }
        _mockBtState.value = BtState.Connected("Mock Device (${scenario.label})")
        simulator.start(scenario)
    }

    fun stopSimulator() {
        simulator.stop()
        _mockBtState.value = BtState.Disconnected
        anomalyEngine.reset()
        scope.launch { db.vitalsDao().deleteAll() }
    }

    fun switchSimulatorScenario(scenario: SimulatorScenario) {
        _mockBtState.value = BtState.Connected("Mock Device (${scenario.label})")
        simulator.switchScenario(scenario)
    }

    // ── Device connection ──────────────────────────────────────────────────────

    fun connectDevice(address: String, name: String) {
        if (simulator.isRunning) {
            simulator.stop()
            _mockBtState.value = BtState.Disconnected
        }
        anomalyEngine.reset()
        scope.launch {
            // Only delete readings if no active session — don't wipe session data
            if (activeSessionId == null) db.vitalsDao().deleteAll()
            db.deviceDao().upsert(DeviceEntity(address = address, name = name))
        }
        btManager.connect(address, name)
    }

    fun disconnectDevice() = btManager.disconnect()

    suspend fun autoConnectLastDevice() {
        val device = db.deviceDao().getPrimaryDevice() ?: return
        if (btManager.state.value is BtState.Disconnected) {
            Log.i(TAG, "Auto-connecting to: ${device.name}")
            btManager.connect(device.address, device.name)
        }
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    suspend fun acknowledgeAlert(id: Long) = db.anomalyDao().acknowledge(id)
    suspend fun getPrimaryDevice(): DeviceEntity? = db.deviceDao().getPrimaryDevice()
    fun getPairedDevices(hasPermission: Boolean) = btManager.getPairedDevices(hasPermission)
    fun isBluetoothEnabled()   = btManager.isBluetoothEnabled()
    fun isBluetoothAvailable() = btManager.isBluetoothAvailable()

    suspend fun pruneOldReadings(keepDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - keepDays * 24 * 60 * 60 * 1000L
        db.vitalsDao().deleteOlderThan(cutoff)
    }

    // Legacy compat
    suspend fun generateSessionReport(): String = endSession().fold(
        onSuccess = { "Report generated (id=$it)" },
        onFailure = { it.message ?: "Error" }
    )

    // ── JSON helpers ───────────────────────────────────────────────────────────

    private fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end   = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
    }

    private data class AiFields(
        val summary: String = "",
        val overallStatus: String = "Unknown",
        val observations: String = "[]",
        val physicalConcerns: String = "[]",
        val foodRecs: String = "[]",
        val exerciseRecs: String = "[]",
        val lifestyleRecs: String = "[]",
        val medicalAttention: String = "[]"
    )

    private fun parseAiJson(json: String): AiFields {
        if (json.isBlank()) {
            Log.w(RTAG, "QWEN JSON PARSING FAILED: empty response")
            return AiFields()
        }
        return try {
            val obj = JSONObject(json)
            AiFields(
                summary          = obj.optString("summary", ""),
                overallStatus    = obj.optString("overall_status", "Unknown"),
                observations     = obj.optJSONArray("observations")?.toString() ?: "[]",
                physicalConcerns = obj.optJSONArray("physical_concerns")?.toString() ?: "[]",
                foodRecs         = obj.optJSONArray("food_recommendations")?.toString() ?: "[]",
                exerciseRecs     = obj.optJSONArray("exercise_recommendations")?.toString() ?: "[]",
                lifestyleRecs    = obj.optJSONArray("lifestyle_recommendations")?.toString() ?: "[]",
                medicalAttention = obj.optJSONArray("when_to_seek_medical_attention")?.toString() ?: "[]"
            )
        } catch (e: Exception) {
            Log.e(RTAG, "QWEN JSON PARSING FAILED: ${e.message} — first 300 chars: ${json.take(300)}", e)
            AiFields()
        }
    }
}
