package com.infinity.ai.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinity.ai.health.data.AnomalyEvent
import com.infinity.ai.ui.components.GradientBackground
import com.infinity.ai.ui.theme.*
import com.infinity.ai.viewmodel.HealthViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthAlertsScreen(isDarkTheme: Boolean, bottomPadding: Dp) {
    val vm: HealthViewModel = viewModel()
    val anomalies by vm.recentAnomalies.collectAsState()
    val dark = isDarkTheme

    GradientBackground(darkTheme = dark, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Alerts",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    fontWeight = FontWeight.Bold)
                Text("Anomaly events & AI explanations",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextSecondary else TextSecondaryLight)
            }
            Spacer(Modifier.height(16.dp))

            if (anomalies.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = SuccessGreen, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No alerts", style = MaterialTheme.typography.titleMedium,
                            color = if (dark) TextPrimary else TextPrimaryLight,
                            fontWeight = FontWeight.SemiBold)
                        Text("All vitals are within normal range",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dark) TextSecondary else TextSecondaryLight)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(anomalies, key = { it.id }) { event ->
                        AlertCard(event = event, dark = dark, onAcknowledge = { vm.acknowledgeAlert(event.id) })
                    }
                    item { Spacer(Modifier.height(bottomPadding + 16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(event: AnomalyEvent, dark: Boolean, onAcknowledge: () -> Unit) {
    val severityColor = if (event.severity == "critical") ErrorRed else WarnAmber
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val eventLabel = event.eventType.replace('_', ' ').replaceFirstChar { it.uppercase() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp,
                if (!event.acknowledged) severityColor.copy(0.4f)
                else if (dark) DarkBorder else LightBorder,
                RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(32.dp)
                        .background(severityColor.copy(0.12f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, null, tint = severityColor, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text(eventLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) TextPrimary else TextPrimaryLight,
                        fontWeight = FontWeight.SemiBold)
                    Text(fmt.format(Date(event.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                }
            }
            Box(
                modifier = Modifier
                    .background(severityColor.copy(0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(event.severity.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp)
            }
        }

        // Vitals row
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            event.heartRate?.let { VitalChip("HR", "$it bpm", dark) }
            event.spo2?.let { VitalChip("SpO₂", "$it%", dark) }
            event.temperature?.let { VitalChip("Temp", "${"%.1f".format(it)}°C", dark) }
        }

        // AI explanation
        if (event.aiExplanation.isNotBlank()) {
            HorizontalDivider(color = if (dark) DarkBorder else LightBorder)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null,
                    tint = Blue500, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                Text(event.aiExplanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextSecondary else TextSecondaryLight,
                    lineHeight = 18.sp)
            }
        } else if (!event.acknowledged) {
            Text("AI explanation generating…",
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextDisabled else TextSecondaryLight.copy(0.5f))
        }

        // Acknowledge button
        if (!event.acknowledged) {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.align(Alignment.End),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Acknowledge", style = MaterialTheme.typography.labelMedium, color = Blue500)
            }
        }
    }
}

@Composable
private fun VitalChip(label: String, value: String, dark: Boolean) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextSecondary else TextSecondaryLight)
        Text(value, style = MaterialTheme.typography.labelMedium,
            color = if (dark) TextPrimary else TextPrimaryLight,
            fontWeight = FontWeight.SemiBold)
    }
}
