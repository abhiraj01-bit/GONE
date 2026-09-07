package com.infinity.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinity.ai.health.data.VitalsReading
import com.infinity.ai.ui.components.GradientBackground
import com.infinity.ai.ui.theme.*
import com.infinity.ai.viewmodel.HealthViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthHistoryScreen(isDarkTheme: Boolean, bottomPadding: Dp) {
    val vm: HealthViewModel = viewModel()
    val history by vm.recentVitals.collectAsState()
    val dark = isDarkTheme

    GradientBackground(darkTheme = dark, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("History",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    fontWeight = FontWeight.Bold)
                Text("${history.size} readings stored locally",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextSecondary else TextSecondaryLight)
            }
            Spacer(Modifier.height(16.dp))

            if (history.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null,
                            tint = if (dark) TextSecondary else TextSecondaryLight,
                            modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No history yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (dark) TextPrimary else TextPrimaryLight,
                            fontWeight = FontWeight.SemiBold)
                        Text("Readings will appear here once monitoring starts",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dark) TextSecondary else TextSecondaryLight)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history, key = { it.id }) { reading ->
                        HistoryRow(reading = reading, dark = dark)
                    }
                    item { Spacer(Modifier.height(bottomPadding + 16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(reading: VitalsReading, dark: Boolean) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val hasAnomaly = (reading.heartRate?.let { it < 50 || it > 100 } == true) ||
                     (reading.spo2?.let { it < 94 } == true) ||
                     (reading.temperature?.let { it > 37.8f || it < 35f } == true) ||
                     reading.fallDetected

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp,
                if (hasAnomaly) ErrorRed.copy(0.3f) else if (dark) DarkBorder else LightBorder,
                RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(8.dp)
                .background(if (hasAnomaly) ErrorRed else SuccessGreen, CircleShape)
        )
        Text(fmt.format(Date(reading.timestamp)),
            style = MaterialTheme.typography.labelMedium,
            color = if (dark) TextSecondary else TextSecondaryLight,
            modifier = Modifier.width(64.dp))

        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            reading.heartRate?.let {
                MiniStat(Icons.Default.Favorite, "$it", "bpm",
                    if (it < 50 || it > 100) ErrorRed else SuccessGreen, dark)
            }
            reading.spo2?.let {
                MiniStat(Icons.Default.Air, "$it", "%",
                    if (it < 94) ErrorRed else SuccessGreen, dark)
            }
            reading.temperature?.let {
                MiniStat(Icons.Default.Thermostat, "${"%.1f".format(it)}", "°C",
                    if (it > 37.8f || it < 35f) WarnAmber else SuccessGreen, dark)
            }
        }

        if (reading.fallDetected) {
            Icon(Icons.Default.Warning, null, tint = ErrorRed, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector,
                     value: String, unit: String, color: androidx.compose.ui.graphics.Color,
                     dark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
        Text("$value$unit",
            style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextPrimary else TextPrimaryLight,
            fontWeight = FontWeight.Medium)
    }
}
