package com.infinity.ai.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.ai.bluetooth.BtState
import com.infinity.ai.health.data.AnomalyEvent
import com.infinity.ai.health.data.DeviceEntity
import com.infinity.ai.health.data.HealthReportEntity
import com.infinity.ai.health.data.VitalsReading
import com.infinity.ai.health.mock.SimulatorScenario
import com.infinity.ai.health.repository.HealthRepository
import com.infinity.ai.ui.components.OrbState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HealthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HealthRepository.getInstance(app)

    val btState: StateFlow<BtState> = repo.btState
        .stateIn(viewModelScope, SharingStarted.Eagerly, BtState.Disconnected)

    val liveEmg: SharedFlow<VitalsReading>       = repo.liveEmg
    val simulatorLive: SharedFlow<VitalsReading> = repo.simulatorLive

    val latestVitals: StateFlow<VitalsReading?> = repo.latestVitals
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val recentVitals: StateFlow<List<VitalsReading>> = repo.recentVitals
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentAnomalies: StateFlow<List<AnomalyEvent>> = repo.recentAnomalies
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unacknowledgedAlerts: StateFlow<List<AnomalyEvent>> = repo.unacknowledgedAlerts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allReports: StateFlow<List<HealthReportEntity>> = repo.allReports
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _sessionReadingCount = MutableStateFlow(0)
    val sessionReadingCount: StateFlow<Int> = _sessionReadingCount.asStateFlow()

    init {
        viewModelScope.launch {
            liveEmg.collect {
                if (_isSessionActive.value) _sessionReadingCount.value++
            }
        }
        viewModelScope.launch {
            simulatorLive.collect {
                if (_isSessionActive.value) _sessionReadingCount.value++
            }
        }
    }

    val healthOrbState: StateFlow<OrbState> = combine(btState, unacknowledgedAlerts) { bt, alerts ->
        when {
            alerts.isNotEmpty()      -> OrbState.AnomalyDetected
            bt is BtState.Connected  -> OrbState.Monitoring
            bt is BtState.Connecting -> OrbState.Reading
            else                     -> OrbState.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OrbState.Idle)

    fun getPairedDevices(hasPermission: Boolean) = repo.getPairedDevices(hasPermission)

    fun connectDevice(address: String, name: String) {
        _simulatorRunning.value = false
        repo.connectDevice(address, name)
    }
    fun disconnectDevice()     = repo.disconnectDevice()
    fun isBluetoothEnabled()   = repo.isBluetoothEnabled()
    fun isBluetoothAvailable() = repo.isBluetoothAvailable()

    fun acknowledgeAlert(id: Long) {
        viewModelScope.launch { repo.acknowledgeAlert(id) }
    }

    suspend fun getPrimaryDevice(): DeviceEntity? = repo.getPrimaryDevice()

    fun autoConnectLastDevice() {
        viewModelScope.launch { repo.autoConnectLastDevice() }
    }

    private val _simulatorRunning = MutableStateFlow(repo.simulator.isRunning)
    val simulatorRunning: StateFlow<Boolean> = _simulatorRunning.asStateFlow()
    val simulatorScenario: SimulatorScenario get() = repo.simulator.currentScenario

    fun startSimulator(scenario: SimulatorScenario) {
        repo.startSimulator(scenario)
        _simulatorRunning.value = true
    }

    fun stopSimulator() {
        repo.stopSimulator()
        _simulatorRunning.value = false
    }

    fun switchSimulatorScenario(scenario: SimulatorScenario) {
        repo.switchSimulatorScenario(scenario)
    }

    fun startSession() {
        // Mark session active immediately on the main thread — before the Room INSERT.
        // This ensures _sessionReadingCount starts ticking for ALL readings,
        // and avoids the race window where early readings arrive while activeSessionId is still null.
        _isSessionActive.value = true
        _sessionReadingCount.value = 0
        Log.d("INFINITY_REPORT", "START SESSION clicked — launching Room INSERT")
        viewModelScope.launch {
            repo.startSession()
            Log.d("INFINITY_REPORT", "Session INSERT complete — activeSessionId=${repo.getActiveSessionId()}")
        }
    }

    fun cancelSession() {
        viewModelScope.launch {
            repo.cancelSession()
            _isSessionActive.value = false
            _sessionReadingCount.value = 0
        }
    }

    // ── Report generation ──────────────────────────────────────────────────────

    sealed class ReportState {
        object Idle : ReportState()
        data class Generating(val step: String = "Analyzing session...") : ReportState()
        data class Success(val reportId: Long) : ReportState()
        data class Error(val message: String)  : ReportState()
    }

    enum class ReportStateCompat { Idle, Generating, Done, Error }

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    val reportStateCompat: StateFlow<ReportStateCompat> = _reportState.map { s ->
        when (s) {
            is ReportState.Idle       -> ReportStateCompat.Idle
            is ReportState.Generating -> ReportStateCompat.Generating
            is ReportState.Success    -> ReportStateCompat.Done
            is ReportState.Error      -> ReportStateCompat.Error
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportStateCompat.Idle)

    val latestAiExplanation: StateFlow<String> = recentAnomalies
        .map { list -> list.firstOrNull { it.aiExplanation.isNotBlank() }?.aiExplanation ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun endSessionAndReport() {
        if (_reportState.value is ReportState.Generating) return
        Log.d("INFINITY_REPORT", "END SESSION CLICKED -- activeSessionId=${repo.getActiveSessionId()}")
        // Set generating state but do NOT clear _isSessionActive yet.
        // repo.activeSessionId must still be set when repo.endSession() runs.
        _reportState.value = ReportState.Generating("Finalizing session...")

        viewModelScope.launch {
            _reportState.value = ReportState.Generating("Analyzing vital patterns...")
            val result = repo.endSession()
            // Only clear session UI state after repo has finished
            _isSessionActive.value = false
            _sessionReadingCount.value = 0
            result.fold(
                onSuccess = { reportId ->
                    Log.d("INFINITY_REPORT", "ViewModel: report success, reportId=$reportId")
                    _reportState.value = ReportState.Success(reportId)
                },
                onFailure = { e ->
                    Log.e("INFINITY_REPORT", "ViewModel: report failed: ${e.message}", e)
                    _reportState.value = ReportState.Error(e.message ?: "Report generation failed.")
                }
            )
        }
    }

    fun dismissReport() {
        _reportState.value = ReportState.Idle
    }

    suspend fun getReport(reportId: Long): HealthReportEntity? = repo.getReport(reportId)
    fun getReportFlow(reportId: Long): Flow<HealthReportEntity?> = repo.getReportFlow(reportId)
}
