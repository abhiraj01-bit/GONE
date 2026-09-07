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
        // Standard SPP UUID for HC-05
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val RECONNECT_DELAY_MS = 5_000L

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

    private val _vitals = MutableSharedFlow<VitalsReading>(extraBufferCapacity = 32)
    val vitals: SharedFlow<VitalsReading> = _vitals.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var connectJob: Job? = null
    private var autoReconnect = false

    fun connect(address: String, deviceName: String) {
        autoReconnect = true
        connectJob?.cancel()
        connectJob = scope.launch {
            while (autoReconnect && isActive) {
                _state.value = BtState.Connecting
                val result = runCatching { openSocket(address, deviceName) }
                if (result.isFailure) {
                    Log.w(TAG, "Connection failed: ${result.exceptionOrNull()?.message}")
                    _state.value = BtState.Error(result.exceptionOrNull()?.message ?: "Connection failed")
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

    private suspend fun openSocket(address: String, deviceName: String) {
        val device: BluetoothDevice = adapter?.getRemoteDevice(address)
            ?: throw IllegalStateException("Bluetooth not available")

        val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
        adapter?.cancelDiscovery()
        sock.connect()
        socket = sock
        _state.value = BtState.Connected(deviceName)
        Log.i(TAG, "Connected to $deviceName ($address)")

        readLoop(sock)
    }

    private suspend fun readLoop(sock: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(sock.inputStream))
            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                VitalsPacketParser.parse(line)?.let { _vitals.tryEmit(it) }
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

    fun isBluetoothAvailable(): Boolean = adapter != null
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun getPairedDevices(): List<Pair<String, String>> =
        adapter?.bondedDevices?.map { it.name to it.address } ?: emptyList()
}
