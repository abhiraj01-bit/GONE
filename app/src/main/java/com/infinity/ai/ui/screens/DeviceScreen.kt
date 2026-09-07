package com.infinity.ai.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinity.ai.bluetooth.BtState
import com.infinity.ai.ui.components.GradientBackground
import com.infinity.ai.ui.theme.*
import com.infinity.ai.viewmodel.HealthViewModel

@Composable
fun DeviceScreen(isDarkTheme: Boolean, bottomPadding: Dp) {
    val vm: HealthViewModel = viewModel()
    val btState by vm.btState.collectAsState()
    val paired  = remember { vm.pairedDevices }
    val dark    = isDarkTheme

    GradientBackground(darkTheme = dark, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Text("Device",
                style = MaterialTheme.typography.headlineMedium,
                color = if (dark) TextPrimary else TextPrimaryLight,
                fontWeight = FontWeight.Bold)
            Text("HC-05 Bluetooth wearable",
                style = MaterialTheme.typography.bodySmall,
                color = if (dark) TextSecondary else TextSecondaryLight)

            Spacer(Modifier.height(24.dp))

            // Connection status card
            ConnectionStatusCard(btState = btState, dark = dark, onDisconnect = { vm.disconnectDevice() })

            Spacer(Modifier.height(20.dp))

            // Bluetooth availability
            if (!vm.isBluetoothAvailable()) {
                InfoCard("Bluetooth not available on this device", ErrorRed, dark)
            } else if (!vm.isBluetoothEnabled()) {
                InfoCard("Bluetooth is disabled. Enable it in system settings.", WarnAmber, dark)
            }

            // Paired devices
            if (paired.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Paired Devices", dark)
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (dark) DarkSurface else LightSurface)
                        .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(16.dp))
                ) {
                    paired.forEachIndexed { idx, (name, address) ->
                        val isConnected = btState is BtState.Connected &&
                                (btState as BtState.Connected).deviceName == name
                        DeviceRow(
                            name      = name,
                            address   = address,
                            connected = isConnected,
                            dark      = dark,
                            onConnect = { vm.connectDevice(address, name) }
                        )
                        if (idx < paired.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = if (dark) DarkBorder else LightBorder
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                InfoCard("No paired Bluetooth devices found.\nPair your HC-05 wearable in Android Bluetooth settings first.", Blue500, dark)
            }

            Spacer(Modifier.height(20.dp))

            // Protocol info
            SectionLabel("Protocol", dark)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (dark) DarkSurface else LightSurface)
                    .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProtoRow("Interface", "Bluetooth Classic SPP", dark)
                ProtoRow("UUID", "00001101-…34FB", dark)
                ProtoRow("Packet format", "HR:72,SPO2:98,TEMP:36.5,MOT:0,FALL:0", dark)
                ProtoRow("Sensors", "MAX30102 · DS18B20 · MPU6050", dark)
            }

            Spacer(Modifier.height(bottomPadding + 28.dp))
        }
    }
}

@Composable
private fun ConnectionStatusCard(btState: BtState, dark: Boolean, onDisconnect: () -> Unit) {
    val (statusLabel, statusColor, icon) = when (btState) {
        is BtState.Connected    -> Triple("Connected to ${btState.deviceName}", SuccessGreen, Icons.Default.BluetoothConnected)
        is BtState.Connecting   -> Triple("Connecting…", WarnAmber, Icons.Default.Bluetooth)
        is BtState.Error        -> Triple(btState.message, ErrorRed, Icons.Default.BluetoothDisabled)
        is BtState.Disconnected -> Triple("No device connected", if (dark) TextSecondary else TextSecondaryLight, Icons.Default.BluetoothDisabled)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, statusColor.copy(0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp)
                .background(statusColor.copy(0.12f), RoundedCornerShape(13.dp)),
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

@Composable
private fun DeviceRow(name: String, address: String, connected: Boolean,
                      dark: Boolean, onConnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !connected, onClick = onConnect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(8.dp)
                .background(if (connected) SuccessGreen else if (dark) TextDisabled else LightBorder, CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dark) TextPrimary else TextPrimaryLight,
                fontWeight = FontWeight.Medium)
            Text(address,
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight)
        }
        if (connected) {
            Text("Connected",
                style = MaterialTheme.typography.labelSmall,
                color = SuccessGreen,
                fontWeight = FontWeight.SemiBold)
        } else {
            Text("Connect",
                style = MaterialTheme.typography.labelSmall,
                color = Blue500,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoCard(message: String, color: Color, dark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(0.08f))
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, null, tint = color, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun SectionLabel(text: String, dark: Boolean) {
    Text(text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (dark) TextSecondary else TextSecondaryLight,
        fontWeight = FontWeight.Medium,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun ProtoRow(label: String, value: String, dark: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label,
            style = MaterialTheme.typography.bodySmall,
            color = if (dark) TextSecondary else TextSecondaryLight)
        Text(value,
            style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextPrimary else TextPrimaryLight,
            fontWeight = FontWeight.Medium)
    }
}
