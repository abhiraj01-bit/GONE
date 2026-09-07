package com.infinity.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.ai.bluetooth.BtState
import com.infinity.ai.health.data.AnomalyEvent
import com.infinity.ai.health.data.DeviceEntity
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

    val latestVitals: StateFlow<VitalsReading?> = repo.latestVitals
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val recentVitals: StateFlow<List<VitalsReading>> = repo.recentVitals
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentAnomalies: StateFlow<List<AnomalyEvent>> = repo.recentAnomalies
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unacknowledgedAlerts: StateFlow<List<AnomalyEvent>> = repo.unacknowledgedAlerts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pairedDevices: List<Pair<String, String>> get() = repo.pairedDevices

    val healthOrbState: StateFlow<OrbState> = combine(btState, unacknowledgedAlerts) { bt, alerts ->
        when {
            alerts.isNotEmpty()     -> OrbState.AnomalyDetected
            bt is BtState.Connected -> OrbState.Monitoring
            bt is BtState.Connecting -> OrbState.Reading
            else                    -> OrbState.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OrbState.Idle)

    fun connectDevice(address: String, name: String) = repo.connectDevice(address, name)
    fun disconnectDevice() = repo.disconnectDevice()

    fun acknowledgeAlert(id: Long) {
        viewModelScope.launch { repo.acknowledgeAlert(id) }
    }

    fun isBluetoothEnabled() = repo.isBluetoothEnabled()
    fun isBluetoothAvailable() = repo.isBluetoothAvailable()

    suspend fun getPrimaryDevice(): DeviceEntity? = repo.getPrimaryDevice()

    // ── Simulator controls ─────────────────────────────────────────────────────
    private val _simulatorRunning = MutableStateFlow(false)
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

    // ── Session Report ─────────────────────────────────────────────────────────
    enum class ReportState { Idle, Generating, Done, Error }

    private val _reportState = MutableStateFlow(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    private val _sessionReport = MutableStateFlow("")
    val sessionReport: StateFlow<String> = _sessionReport.asStateFlow()

    // Latest AI anomaly explanation (updates whenever a new anomaly fires)
    val latestAiExplanation: StateFlow<String> = recentAnomalies
        .map { list -> list.firstOrNull { it.aiExplanation.isNotBlank() }?.aiExplanation ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun endSessionAndReport() {
        if (_reportState.value == ReportState.Generating) return
        _reportState.value = ReportState.Generating
        _sessionReport.value = ""
        viewModelScope.launch {
            val result = repo.generateSessionReport()
            _sessionReport.value = result
            _reportState.value = if (result.startsWith("Report generation failed") ||
                result.startsWith("No vitals")) ReportState.Error else ReportState.Done
        }
    }

    fun dismissReport() {
        _reportState.value = ReportState.Idle
        _sessionReport.value = ""
    }
}
