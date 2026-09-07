package com.infinity.ai.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinity.ai.bluetooth.BtState
import com.infinity.ai.health.data.VitalsReading
import com.infinity.ai.ui.components.GradientBackground
import com.infinity.ai.ui.theme.*
import com.infinity.ai.viewmodel.HealthViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthMonitorScreen(
    isDarkTheme   : Boolean,
    bottomPadding : Dp,
    onNavigateToDevice : () -> Unit = {}
) {
    val vm: HealthViewModel = viewModel()
    val btState       by vm.btState.collectAsState()
    val vitals        by vm.latestVitals.collectAsState()
    val recentVitals  by vm.recentVitals.collectAsState()
    val alerts        by vm.unacknowledgedAlerts.collectAsState()
    val aiExplanation by vm.latestAiExplanation.collectAsState()
    val reportState   by vm.reportState.collectAsState()
    val sessionReport by vm.sessionReport.collectAsState()
    val dark = isDarkTheme

    // Keep last 60 readings for graphs
    val graphData = remember(recentVitals) { recentVitals.takeLast(60).reversed() }

    if (reportState == HealthViewModel.ReportState.Done || reportState == HealthViewModel.ReportState.Error) {
        SessionReportDialog(
            report = sessionReport,
            dark   = dark,
            onDismiss = { vm.dismissReport() }
        )
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

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Health Monitor",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (dark) TextPrimary else TextPrimaryLight,
                        fontWeight = FontWeight.Bold)
                    Text("Live vitals from wearable",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                }
                ConnectionBadge(btState, dark, onNavigateToDevice)
            }

            Spacer(Modifier.height(20.dp))

            // Alert banner
            if (alerts.isNotEmpty()) {
                AlertBanner(count = alerts.size, dark = dark)
                Spacer(Modifier.height(12.dp))
            }

            // Vitals grid
            val hr   = vitals?.heartRate
            val spo2 = vitals?.spo2
            val temp = vitals?.temperature
            val fall = vitals?.fallDetected ?: false
            val mot  = vitals?.motionDetected ?: false

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalCard(
                    icon    = Icons.Default.Favorite,
                    label   = "Heart Rate",
                    value   = hr?.toString() ?: "--",
                    unit    = "bpm",
                    color   = if (hr != null && (hr < 50 || hr > 100)) ErrorRed else SuccessGreen,
                    dark    = dark,
                    modifier = Modifier.weight(1f)
                )
                VitalCard(
                    icon    = Icons.Default.Air,
                    label   = "SpO₂",
                    value   = spo2?.toString() ?: "--",
                    unit    = "%",
                    color   = if (spo2 != null && spo2 < 94) ErrorRed else SuccessGreen,
                    dark    = dark,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalCard(
                    icon    = Icons.Default.Thermostat,
                    label   = "Temperature",
                    value   = temp?.let { "%.1f".format(it) } ?: "--",
                    unit    = "°C",
                    color   = if (temp != null && (temp > 37.8f || temp < 35f)) WarnAmber else SuccessGreen,
                    dark    = dark,
                    modifier = Modifier.weight(1f)
                )
                VitalCard(
                    icon    = if (fall) Icons.Default.Warning else Icons.Default.DirectionsWalk,
                    label   = if (fall) "Fall Detected!" else "Motion",
                    value   = if (fall) "FALL" else if (mot) "Active" else "Still",
                    unit    = "",
                    color   = if (fall) ErrorRed else if (mot) WarnAmber else SuccessGreen,
                    dark    = dark,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Live Waveform Graphs ──────────────────────────────────────────
            if (graphData.size >= 2) {
                Text("Live Waveforms",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (dark) TextSecondary else TextSecondaryLight,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))

                WaveformGraph(
                    label  = "ECG — Heart Rate",
                    unit   = "bpm",
                    values = graphData.map { it.heartRate?.toFloat() },
                    color  = ErrorRed,
                    dark   = dark,
                    yMin   = 30f,
                    yMax   = 160f
                )
                Spacer(Modifier.height(10.dp))
                WaveformGraph(
                    label  = "SpO₂",
                    unit   = "%",
                    values = graphData.map { it.spo2?.toFloat() },
                    color  = Blue500,
                    dark   = dark,
                    yMin   = 80f,
                    yMax   = 100f
                )
                Spacer(Modifier.height(10.dp))
                WaveformGraph(
                    label  = "Temperature",
                    unit   = "°C",
                    values = graphData.map { it.temperature },
                    color  = WarnAmber,
                    dark   = dark,
                    yMin   = 34f,
                    yMax   = 41f
                )
                Spacer(Modifier.height(20.dp))
            }

            // ── AI Analysis Card ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = aiExplanation.isNotBlank(),
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                AiAnalysisCard(explanation = aiExplanation, dark = dark)
                Spacer(Modifier.height(16.dp))
            }

            // ── Last reading timestamp ────────────────────────────────────────
            vitals?.let { v ->
                val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(SuccessGreen, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("Last reading: ${fmt.format(Date(v.timestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                }
            }

            if (vitals == null) {
                Spacer(Modifier.height(32.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.BluetoothSearching, null,
                        tint = if (dark) TextSecondary else TextSecondaryLight,
                        modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No data yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                    Text("Connect your wearable device to start monitoring",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) TextDisabled else TextSecondaryLight.copy(0.6f),
                        modifier = Modifier.padding(top = 4.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── End Session & Generate Report ─────────────────────────────────
            EndSessionButton(
                reportState = reportState,
                hasData     = vitals != null,
                dark        = dark,
                onClick     = { vm.endSessionAndReport() }
            )

            Spacer(Modifier.height(bottomPadding + 28.dp))
        }
    }
}

// ── Waveform Graph ─────────────────────────────────────────────────────────────

@Composable
private fun WaveformGraph(
    label  : String,
    unit   : String,
    values : List<Float?>,
    color  : Color,
    dark   : Boolean,
    yMin   : Float,
    yMax   : Float
) {
    val surfaceColor = if (dark) DarkSurface else LightSurface
    val borderColor  = if (dark) DarkBorder  else LightBorder
    val gridColor    = if (dark) Color.White.copy(0.05f) else Color.Black.copy(0.05f)
    val textColor    = if (dark) TextSecondary else TextSecondaryLight

    // Animate the latest value for the live dot
    val latestValid = values.lastOrNull { it != null }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Pulsing live dot
                val pulse = rememberInfiniteTransition(label = "pulse")
                val alpha by pulse.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "alpha"
                )
                Box(Modifier.size(7.dp).background(color.copy(alpha), CircleShape))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = textColor, fontWeight = FontWeight.SemiBold)
            }
            Text(latestValid?.let { "%.1f".format(it) + " $unit" } ?: "-- $unit",
                style = MaterialTheme.typography.labelSmall,
                color = color, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            val w = size.width
            val h = size.height
            val range = (yMax - yMin).coerceAtLeast(1f)

            // Grid lines
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            // Build path from valid points
            val validPoints = values.mapIndexedNotNull { idx, v ->
                if (v == null) null
                else {
                    val x = w * idx / (values.size - 1).coerceAtLeast(1).toFloat()
                    val y = h - h * ((v - yMin) / range).coerceIn(0f, 1f)
                    Offset(x, y)
                }
            }

            if (validPoints.size >= 2) {
                // Filled gradient area
                val fillPath = Path().apply {
                    moveTo(validPoints.first().x, h)
                    validPoints.forEach { lineTo(it.x, it.y) }
                    lineTo(validPoints.last().x, h)
                    close()
                }
                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(0.25f), color.copy(0f)),
                        startY = 0f, endY = h
                    )
                )

                // ECG-style line
                val linePath = Path().apply {
                    moveTo(validPoints.first().x, validPoints.first().y)
                    for (i in 1 until validPoints.size) {
                        val prev = validPoints[i - 1]
                        val curr = validPoints[i]
                        val cx = (prev.x + curr.x) / 2f
                        cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    }
                }
                drawPath(linePath, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

                // Live dot at last point
                drawCircle(color, radius = 5f, center = validPoints.last())
                drawCircle(color.copy(0.3f), radius = 9f, center = validPoints.last())
            }
        }
    }
}

// ── AI Analysis Card ───────────────────────────────────────────────────────────

@Composable
private fun AiAnalysisCard(explanation: String, dark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Blue500.copy(0.08f) else Blue50)
            .border(1.dp, Blue500.copy(0.25f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Psychology, null, tint = Blue500, modifier = Modifier.size(18.dp))
            Text("AI Analysis",
                style = MaterialTheme.typography.labelMedium,
                color = Blue500,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(explanation,
            style = MaterialTheme.typography.bodySmall,
            color = if (dark) TextPrimary else TextPrimaryLight,
            lineHeight = 18.sp)
    }
}

// ── End Session Button ─────────────────────────────────────────────────────────

@Composable
private fun EndSessionButton(
    reportState : HealthViewModel.ReportState,
    hasData     : Boolean,
    dark        : Boolean,
    onClick     : () -> Unit
) {
    val isGenerating = reportState == HealthViewModel.ReportState.Generating

    Button(
        onClick  = onClick,
        enabled  = hasData && !isGenerating,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = if (dark) DarkSurfaceElevated else LightSurfaceElevated,
            contentColor   = if (dark) TextPrimary else TextPrimaryLight,
            disabledContainerColor = if (dark) DarkSurface else LightSurface,
            disabledContentColor   = if (dark) TextDisabled else TextSecondaryLight
        ),
        border = BorderStroke(1.dp, if (dark) DarkBorder else LightBorder)
    ) {
        if (isGenerating) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color    = Blue500,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Generating Report…", fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.Default.Assessment, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("End Session & Generate Report", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Session Report Dialog ──────────────────────────────────────────────────────

@Composable
private fun SessionReportDialog(report: String, dark: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (dark) DarkSurface else LightSurface)
                .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).background(Blue500.copy(0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MedicalServices, null, tint = Blue500, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("Body Health Report",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dark) TextPrimary else TextPrimaryLight,
                        fontWeight = FontWeight.Bold)
                    Text("Generated by G-ONE AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = if (dark) DarkBorder else LightBorder)
            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(report,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    lineHeight = 20.sp)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Blue500)
            ) {
                Text("Close", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Shared sub-composables ─────────────────────────────────────────────────────

@Composable
private fun ConnectionBadge(btState: BtState, dark: Boolean, onClick: () -> Unit) {
    val (label, color) = when (btState) {
        is BtState.Connected    -> "Connected" to SuccessGreen
        is BtState.Connecting   -> "Connecting…" to WarnAmber
        is BtState.Error        -> "Error" to ErrorRed
        is BtState.Disconnected -> "Disconnected" to if (dark) TextSecondary else TextSecondaryLight
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AlertBanner(count: Int, dark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ErrorRed.copy(0.12f))
            .border(1.dp, ErrorRed.copy(0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Warning, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
        Text("$count unacknowledged alert${if (count > 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = ErrorRed,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = ErrorRed, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun VitalCard(
    icon     : ImageVector,
    label    : String,
    value    : String,
    unit     : String,
    color    : Color,
    dark     : Boolean,
    modifier : Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(color.copy(0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextSecondary else TextSecondaryLight)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value,
                style = MaterialTheme.typography.headlineSmall,
                color = if (dark) TextPrimary else TextPrimaryLight,
                fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) {
                Text(unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dark) TextSecondary else TextSecondaryLight,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}
