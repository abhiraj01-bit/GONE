package com.infinity.ai.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.infinity.ai.health.data.VitalsReading
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

sealed class BtState {
    object Disconnected : BtState()
    object Connecting   : BtState()
    data class Connected(val deviceName: String) : BtState()
    data class Error(val message: String) : BtState()
}

@SuppressLint("MissingPermission")
class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val READ_TIMEOUT_MS    = 10_000L  // 10 s without data = stale connection

        @Volatile private var INSTANCE: BluetoothManager? = null
        fun getInstance(context: Context): BluetoothManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<BtState>(BtState.Disconnected)
    val state: StateFlow<BtState> = _state.asStateFlow()

    private val _vitals = MutableSharedFlow<VitalsReading>(extraBufferCapacity = 64)
    val vitals: SharedFlow<VitalsReading> = _vitals.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var connectJob: Job? = null
    private var autoReconnect = false

    // ── Public API ─────────────────────────────────────────────────────────────

    fun connect(address: String, deviceName: String) {
        autoReconnect = true
        connectJob?.cancel()
        connectJob = scope.launch {
            while (autoReconnect && isActive) {
                _state.value = BtState.Connecting
                val result = runCatching { openSocket(address, deviceName) }
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: "Connection failed"
                    Log.w(TAG, "Connection failed: $msg")
                    _state.value = BtState.Error(msg)
                    if (autoReconnect) delay(RECONNECT_DELAY_MS)
                }
            }
        }
    }

    fun disconnect() {
        autoReconnect = false
        connectJob?.cancel()
        closeSocket()
        _state.value = BtState.Disconnected
    }

    fun isBluetoothAvailable(): Boolean = adapter != null
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun getPairedDevices(hasPermission: Boolean): List<Pair<String, String>> {
        val bt = adapter
        if (!hasPermission || bt == null) return emptyList()
        return runCatching {
            bt.bondedDevices?.mapNotNull { d ->
                val name = runCatching { d.name }.getOrNull() ?: return@mapNotNull null
                name to d.address
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private suspend fun openSocket(address: String, deviceName: String) {
        val device: BluetoothDevice = adapter?.getRemoteDevice(address)
            ?: throw IllegalStateException("Bluetooth adapter not available")

        adapter?.cancelDiscovery()

        // Try secure RFCOMM first; fall back to insecure (HC-05 sometimes needs this)
        val sock = runCatching {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
                .also { it.connect() }
        }.getOrElse {
            Log.w(TAG, "Secure RFCOMM failed, trying insecure: ${it.message}")
            @Suppress("DEPRECATION")
            val insecure = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            insecure.connect()
            insecure
        }

        socket = sock
        _state.value = BtState.Connected(deviceName)
        Log.i(TAG, "Connected to $deviceName ($address)")

        readLoop(sock)
    }

    private suspend fun readLoop(sock: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(sock.inputStream))
            while (currentCoroutineContext().isActive) {
                // withTimeout prevents hanging forever if HC-05 goes silent
                val line = withTimeoutOrNull(READ_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { reader.readLine() }
                }
                when {
                    line == null -> {
                        // Timeout or stream closed — treat as disconnect
                        Log.w(TAG, "Read timeout / stream closed")
                        break
                    }
                    line.isNotBlank() -> {
                        Log.d("InfinityBT", "RAW_PACKET=$line")
                        val reading = VitalsPacketParser.parse(line)
                        if (reading != null) {
                            Log.d("InfinityBT", "PARSED_EMG=${reading.emgRaw} BPM=${reading.bpm} FALL=${reading.fallDetected}")
                            _vitals.tryEmit(reading)
                        } else {
                            Log.w("InfinityBT", "PACKET_REJECTED raw=$line")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Read loop ended: ${e.message}")
        } finally {
            closeSocket()
            if (autoReconnect) {
                _state.value = BtState.Error("Disconnected — retrying…")
                delay(RECONNECT_DELAY_MS)
            } else {
                _state.value = BtState.Disconnected
            }
        }
    }

    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
    }
}
