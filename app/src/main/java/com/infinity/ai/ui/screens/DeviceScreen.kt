package com.infinity.ai.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinity.ai.bluetooth.BtState
import com.infinity.ai.ui.components.GradientBackground
import com.infinity.ai.ui.theme.*
import com.infinity.ai.viewmodel.HealthViewModel

@Composable
fun DeviceScreen(isDarkTheme: Boolean, bottomPadding: Dp) {
    val vm: HealthViewModel = viewModel()
    val btState by vm.btState.collectAsState()
    var paired by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var connectingAddress by remember { mutableStateOf<String?>(null) }
    val dark     = isDarkTheme
    val context  = LocalContext.current

    // ── Runtime Bluetooth permission (Android 12+) ────────────────────────────
    val btPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    else
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)

    var permissionsGranted by remember {
        mutableStateOf(btPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PermissionChecker.PERMISSION_GRANTED
        })
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    // Clear connectingAddress once connected or on error
    LaunchedEffect(btState) {
        if (btState is BtState.Connected || btState is BtState.Error || btState is BtState.Disconnected) {
            connectingAddress = null
        }
    }

    // Only fetch paired devices + auto-connect AFTER permission is confirmed
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            paired = vm.getPairedDevices(hasPermission = true)
            if (vm.isBluetoothEnabled()) vm.autoConnectLastDevice()
        }
    }

    GradientBackground(darkTheme = dark, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Header ────────────────────────────────────────────────────────
            Text("Device",
                style = MaterialTheme.typography.headlineMedium,
                color = if (dark) TextPrimary else TextPrimaryLight,
                fontWeight = FontWeight.Bold)
            Text("Connect your Arduino EMG wearable",
                style = MaterialTheme.typography.bodySmall,
                color = if (dark) TextSecondary else TextSecondaryLight)

            Spacer(Modifier.height(24.dp))

            // ── Permission gate ───────────────────────────────────────────────
            if (!permissionsGranted) {
                PermissionCard(dark = dark, onRequest = { permLauncher.launch(btPermissions) })
                Spacer(Modifier.height(bottomPadding + 28.dp))
                return@Column
            }

            // ── BT disabled warning ───────────────────────────────────────────
            if (!vm.isBluetoothAvailable()) {
                InfoCard("Bluetooth not available on this device.", ErrorRed, dark)
                Spacer(Modifier.height(bottomPadding + 28.dp))
                return@Column
            }
            if (!vm.isBluetoothEnabled()) {
                InfoCard("Bluetooth is OFF. Enable it in Android Settings → Bluetooth.", WarnAmber, dark)
                Spacer(Modifier.height(12.dp))
            }

            // ── Live connection status card ───────────────────────────────────
            ConnectionStatusCard(btState = btState, dark = dark, onDisconnect = { vm.disconnectDevice() })

            Spacer(Modifier.height(20.dp))

            // ── Paired devices list ───────────────────────────────────────────
            SectionLabel("Paired Devices", dark)
            Spacer(Modifier.height(8.dp))

            // Refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { paired = vm.getPairedDevices(hasPermission = true) }) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (paired.isEmpty()) {
                InfoCard(
                    "No paired devices found.\n\n" +
                    "Steps to pair HC-05:\n" +
                    "1. Power on your Arduino\n" +
                    "2. Android Settings → Bluetooth → Scan\n" +
                    "3. Tap HC-05 → PIN: 1234\n" +
                    "4. Come back here and tap Refresh, then Connect",
                    Blue500, dark
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (dark) DarkSurface else LightSurface)
                        .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(16.dp))
                ) {
                    paired.forEachIndexed { idx, (name, address) ->
                        val isConnected  = btState is BtState.Connected &&
                                (btState as BtState.Connected).deviceName == name
                        val isConnecting = btState is BtState.Connecting &&
                                connectingAddress == address
                        DeviceRow(
                            name        = name,
                            address     = address,
                            connected   = isConnected,
                            connecting  = isConnecting,
                            dark        = dark,
                            onConnect   = {
                                connectingAddress = address
                                vm.connectDevice(address, name)
                            }
                        )
                        if (idx < paired.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color    = if (dark) DarkBorder else LightBorder
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── How it works ──────────────────────────────────────────────────
            SectionLabel("How It Works", dark)
            Spacer(Modifier.height(8.dp))
            HowItWorksCard(dark)

            Spacer(Modifier.height(20.dp))

            // ── Protocol info ─────────────────────────────────────────────────
            SectionLabel("Protocol", dark)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (dark) DarkSurface else LightSurface)
                    .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProtoRow("Interface",     "Bluetooth Classic SPP",  dark)
                ProtoRow("Baud Rate",     "9600",                   dark)
                ProtoRow("Packet",        "EMG:312,FALL:0,BPM:76,PULSE:487", dark)
                ProtoRow("Interval",      "Every 500 ms",           dark)
                ProtoRow("HR / BPM",      "Pulse Sensor Amped (A1)", dark)
                ProtoRow("SpO₂ / Temp",   "Estimated (no sensor)",  dark)
            }

            Spacer(Modifier.height(bottomPadding + 28.dp))
        }
    }
}

// ── Permission card ────────────────────────────────────────────────────────────

@Composable
private fun PermissionCard(dark: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, WarnAmber.copy(0.4f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(WarnAmber.copy(0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bluetooth, null, tint = WarnAmber, modifier = Modifier.size(20.dp))
            }
            Column {
                Text("Bluetooth Permission Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    fontWeight = FontWeight.Bold)
                Text("Needed to connect to HC-05",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextSecondary else TextSecondaryLight)
            }
        }
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue500)
        ) {
            Icon(Icons.Default.BluetoothSearching, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Grant Bluetooth Permission", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Connection status card ─────────────────────────────────────────────────────

@Composable
private fun ConnectionStatusCard(btState: BtState, dark: Boolean, onDisconnect: () -> Unit) {
    val (statusLabel, statusColor, icon) = when (btState) {
        is BtState.Connected    -> Triple("Connected to ${btState.deviceName}", SuccessGreen, Icons.Default.BluetoothConnected)
        is BtState.Connecting   -> Triple("Connecting…", WarnAmber, Icons.Default.Bluetooth)
        is BtState.Error        -> Triple(btState.message, ErrorRed, Icons.Default.BluetoothDisabled)
        is BtState.Disconnected -> Triple("No device connected", if (dark) TextSecondary else TextSecondaryLight, Icons.Default.BluetoothDisabled)
    }

    // Pulse animation when connecting
    val inf = rememberInfiniteTransition(label = "btpulse")
    val scale by inf.animateFloat(
        initialValue = 1f, targetValue = if (btState is BtState.Connecting) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, statusColor.copy(0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(statusColor.copy(0.12f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = statusColor, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Wearable Status",
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight)
            Text(statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dark) TextPrimary else TextPrimaryLight,
                fontWeight = FontWeight.SemiBold)
        }
        if (btState is BtState.Connected) {
            TextButton(onClick = onDisconnect, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Disconnect", style = MaterialTheme.typography.labelMedium, color = ErrorRed)
            }
        }
    }
}

// ── Device row ─────────────────────────────────────────────────────────────────

@Composable
private fun DeviceRow(
    name: String, address: String,
    connected: Boolean, connecting: Boolean,
    dark: Boolean, onConnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !connected && !connecting, onClick = onConnect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status dot
        val dotColor = when {
            connected  -> SuccessGreen
            connecting -> WarnAmber
            else       -> if (dark) TextDisabled else LightBorder
        }
        Box(modifier = Modifier.size(9.dp).background(dotColor, CircleShape))

        Column(modifier = Modifier.weight(1f)) {
            Text(name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dark) TextPrimary else TextPrimaryLight,
                fontWeight = FontWeight.SemiBold)
            Text(address,
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight)
        }

        when {
            connected  -> Text("Connected", style = MaterialTheme.typography.labelSmall,
                color = SuccessGreen, fontWeight = FontWeight.Bold)
            connecting -> Text("Connecting…", style = MaterialTheme.typography.labelSmall,
                color = WarnAmber, fontWeight = FontWeight.Medium)
            else       -> Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Blue500)
                    .clickable(onClick = onConnect)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Connect", style = MaterialTheme.typography.labelMedium,
                    color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── How it works card ──────────────────────────────────────────────────────────

@Composable
private fun HowItWorksCard(dark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StepRow("1", Icons.Default.ElectricBolt,
            "EMG sensor reads muscle activity (0–1023 ADC)", Blue500, dark)
        StepRow("2", Icons.Default.Bluetooth,
            "Arduino sends EMG:312,FALL:0 via HC-05 every 500ms", Blue400, dark)
        StepRow("3", Icons.Default.PhoneAndroid,
            "App receives BPM (Pulse Sensor) + EMG live; estimates SpO₂ & Temp from those values", SuccessGreen, dark)
        StepRow("4", Icons.Default.Psychology,
            "G-ONE AI explains any anomaly detected", WarnAmber, dark)
    }
}

@Composable
private fun StepRow(step: String, icon: ImageVector, text: String, color: Color, dark: Boolean) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(color.copy(0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Text(text,
            style = MaterialTheme.typography.bodySmall,
            color = if (dark) TextPrimary else TextPrimaryLight,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f).padding(top = 6.dp))
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

@Composable
private fun InfoCard(message: String, color: Color, dark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(0.08f))
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, null, tint = color, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = color, lineHeight = 18.sp)
    }
}

@Composable
private fun SectionLabel(text: String, dark: Boolean) {
    Text(text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (dark) TextSecondary else TextSecondaryLight,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun ProtoRow(label: String, value: String, dark: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = if (dark) TextSecondary else TextSecondaryLight)
        Text(value, style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextPrimary else TextPrimaryLight,
            fontWeight = FontWeight.Medium)
    }
}
