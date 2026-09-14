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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinity.ai.bluetooth.BtState
import com.infinity.ai.health.anomaly.AnomalyThresholds
import com.infinity.ai.health.data.DerivedVitalsCalculator
import com.infinity.ai.ui.components.GradientBackground
import com.infinity.ai.ui.theme.*
import com.infinity.ai.viewmodel.HealthViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthMonitorScreen(
    isDarkTheme          : Boolean,
    bottomPadding        : Dp,
    onNavigateToDevice   : () -> Unit = {},
    onNavigateToReport   : (Long) -> Unit = {},
    onNavigateToVault    : () -> Unit = {}
) {
    val vm              : HealthViewModel = viewModel()
    val btState         by vm.btState.collectAsState()
    val alerts          by vm.unacknowledgedAlerts.collectAsState()
    val aiExplanation   by vm.latestAiExplanation.collectAsState()
    val reportState     by vm.reportState.collectAsState()
    val dark             = isDarkTheme
    val simRunning       by vm.simulatorRunning.collectAsState()
    val isSessionActive  by vm.isSessionActive.collectAsState()
    val sessionReadingCount by vm.sessionReadingCount.collectAsState()

    // Navigate to report screen when generation succeeds
    LaunchedEffect(reportState) {
        if (reportState is HealthViewModel.ReportState.Success) {
            onNavigateToReport((reportState as HealthViewModel.ReportState.Success).reportId)
            vm.dismissReport()
        }
    }

    // Show error dialog
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage    by remember { mutableStateOf("") }
    LaunchedEffect(reportState) {
        if (reportState is HealthViewModel.ReportState.Error) {
            errorMessage = (reportState as HealthViewModel.ReportState.Error).message
            showErrorDialog = true
        }
    }
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false; vm.dismissReport() },
            title = { Text("Report Error") },
            text  = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false; vm.dismissReport() }) {
                    Text("OK")
                }
            }
        )
    }

    var liveReading by remember { mutableStateOf<com.infinity.ai.health.data.VitalsReading?>(null) }
    val emgBuffer = remember { mutableStateListOf<Float>() }
    val cardiacBuffer = remember { mutableStateListOf<Float>() }

    // Restart collector when source or connection state changes
    LaunchedEffect(simRunning, btState) {
        val source = if (simRunning) vm.simulatorLive else vm.liveEmg
        source.collect { reading ->
            liveReading = reading
            reading.emgRaw?.toFloat()?.let { v ->
                emgBuffer.add(v)
                if (emgBuffer.size > 120) emgBuffer.removeAt(0)
            }
            val pVal = reading.pulseRaw?.toFloat()
                ?: (520f + ((reading.bpm ?: 72) - 72) * 4.5f)
            cardiacBuffer.add(pVal)
            if (cardiacBuffer.size > 120) cardiacBuffer.removeAt(0)
        }
    }

    // Clear only on genuine disconnect
    LaunchedEffect(btState) {
        if (!simRunning && btState is BtState.Disconnected) {
            liveReading = null
            emgBuffer.clear()
            cardiacBuffer.clear()
        }
    }

    val isConnectedOrConnecting = btState is BtState.Connected ||
        btState is BtState.Connecting || simRunning

    GradientBackground(darkTheme = dark, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))

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
                    Text("Live EMG from wearable",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                }
                ConnectionBadge(btState, dark, onNavigateToDevice)
            }

            Spacer(Modifier.height(20.dp))

            if (alerts.isNotEmpty()) {
                AlertBanner(count = alerts.size, dark = dark)
                Spacer(Modifier.height(12.dp))
            }

            // Show connect prompt only when truly not connected AND no data
            if (liveReading == null && !isConnectedOrConnecting) {
                Spacer(Modifier.height(32.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.BluetoothSearching, null,
                        tint = if (dark) TextSecondary else TextSecondaryLight,
                        modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No signal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) TextSecondary else TextSecondaryLight,
                        fontWeight = FontWeight.SemiBold)
                    Text("Connect your Arduino EMG wearable",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) TextDisabled else TextSecondaryLight.copy(0.6f),
                        modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToDevice,
                        shape   = RoundedCornerShape(12.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = Blue500)
                    ) {
                        Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Connect Device", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(bottomPadding + 28.dp))
                return@Column
            }

            // Connected but waiting for first packet
            if (liveReading == null && isConnectedOrConnecting) {
                Spacer(Modifier.height(32.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val inf = rememberInfiniteTransition(label = "wait")
                    val alpha by inf.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                        label = "wa"
                    )
                    Icon(Icons.Default.Sensors, null,
                        tint = (if (dark) TextSecondary else TextSecondaryLight).copy(alpha),
                        modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Waiting for sensor data…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) TextSecondary else TextSecondaryLight,
                        fontWeight = FontWeight.SemiBold)
                    Text("Device connected — ensure Arduino is transmitting",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) TextDisabled else TextSecondaryLight.copy(0.6f),
                        modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(bottomPadding + 28.dp))
                return@Column
            }

            // ── Session controls ──────────────────────────────────────────────
            SessionControls(
                isSessionActive     = isSessionActive,
                sessionReadingCount = sessionReadingCount,
                reportState         = reportState,
                dark                = dark,
                onStart             = { vm.startSession() },
                onCancel            = { vm.cancelSession() }
            )

            Spacer(Modifier.height(12.dp))

            val hr   = liveReading?.bpm
            val mot  = liveReading?.motionDetected ?: false
            val fall = liveReading?.fallDetected ?: false

            val derivedTemp = remember(liveReading) {
                DerivedVitalsCalculator.calculateTemperature(
                    liveReading?.emgRaw?.toFloat(), liveReading?.bpm?.toFloat()
                )
            }
            val derivedSpo2 = remember(liveReading) {
                DerivedVitalsCalculator.calculateSpO2(
                    liveReading?.emgRaw?.toFloat(), liveReading?.bpm?.toFloat()
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalCard(
                    icon      = Icons.Default.Favorite,
                    label     = "Heart Rate",
                    value     = hr?.toString() ?: "--",
                    unit      = "bpm",
                    sourceTag = "LIVE SENSOR",
                    color     = if (hr != null && (hr < 50 || hr > 100)) ErrorRed else SuccessGreen,
                    dark      = dark,
                    modifier  = Modifier.weight(1f)
                )
                VitalCard(
                    icon      = Icons.Default.Air,
                    label     = "SpO\u2082",
                    value     = derivedSpo2.value?.toString() ?: "--",
                    unit      = "%",
                    sourceTag = "EST.",
                    color     = if (derivedSpo2.value != null && derivedSpo2.value < 94) ErrorRed else SuccessGreen,
                    dark      = dark,
                    modifier  = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalCard(
                    icon      = Icons.Default.Thermostat,
                    label     = "Temperature",
                    value     = derivedTemp.value?.let { "%.1f".format(it) } ?: "--",
                    unit      = "\u00b0C",
                    sourceTag = "EST.",
                    color     = if (derivedTemp.value != null && (derivedTemp.value > 37.8f || derivedTemp.value < 35f)) WarnAmber else SuccessGreen,
                    dark      = dark,
                    modifier  = Modifier.weight(1f)
                )
                VitalCard(
                    icon      = if (fall) Icons.Default.Warning else Icons.Default.DirectionsWalk,
                    label     = if (fall) "Fall Detected!" else "Motion",
                    value     = if (fall) "FALL" else if (mot) "Active" else "Still",
                    unit      = "",
                    sourceTag = "LIVE SENSOR",
                    color     = if (fall) ErrorRed else if (mot) WarnAmber else SuccessGreen,
                    dark      = dark,
                    modifier  = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            val emg = liveReading?.emgRaw
            val emgColor = when {
                emg == null                                    -> if (dark) TextSecondary else TextSecondaryLight
                emg >= AnomalyThresholds.EMG_HIGH_CRITICAL    -> ErrorRed
                emg >= AnomalyThresholds.EMG_HIGH_WARNING     -> WarnAmber
                emg >= AnomalyThresholds.EMG_ACTIVE_THRESHOLD -> Blue500
                else                                           -> SuccessGreen
            }
            val emgLabel = when {
                emg == null                                    -> "Waiting for data…"
                emg >= AnomalyThresholds.EMG_HIGH_CRITICAL    -> "Spasm / High Activity"
                emg >= AnomalyThresholds.EMG_HIGH_WARNING     -> "Muscle Fatigue"
                emg >= AnomalyThresholds.EMG_ACTIVE_THRESHOLD -> "Active Contraction"
                else                                           -> "Resting"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (dark) DarkSurface else LightSurface)
                    .border(1.dp, emgColor.copy(0.4f), RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(emgColor.copy(0.12f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ElectricBolt, null,
                                tint = emgColor, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text("EMG — Muscle Activity",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dark) TextSecondary else TextSecondaryLight)
                            Text(emgLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = emgColor,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(emg?.toString() ?: "--",
                            style = MaterialTheme.typography.displaySmall,
                            color = if (dark) TextPrimary else TextPrimaryLight,
                            fontWeight = FontWeight.Bold)
                        Text("/ 1023 ADC",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dark) TextSecondary else TextSecondaryLight)
                    }
                }
                val emgPct = (emg ?: 0) / 1023f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (dark) DarkSurfaceElevated else LightSurfaceElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(emgPct.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Brush.horizontalGradient(listOf(SuccessGreen, emgColor)))
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ThresholdChip("Rest",   "< ${AnomalyThresholds.EMG_ACTIVE_THRESHOLD}", SuccessGreen, dark)
                    ThresholdChip("Active", "${AnomalyThresholds.EMG_ACTIVE_THRESHOLD}+",  Blue500,      dark)
                    ThresholdChip("Fatigue","${AnomalyThresholds.EMG_HIGH_WARNING}+",      WarnAmber,    dark)
                    ThresholdChip("Spasm",  "${AnomalyThresholds.EMG_HIGH_CRITICAL}+",     ErrorRed,     dark)
                }
            }

            Spacer(Modifier.height(20.dp))

            DualHealthOscilloscope(
                emgBuffer     = emgBuffer,
                cardiacBuffer = cardiacBuffer,
                emgColor      = emgColor,
                liveBpm       = liveReading?.bpm,
                dark          = dark
            )

            Spacer(Modifier.height(20.dp))

            liveReading?.let { v ->
                val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pulse = rememberInfiniteTransition(label = "live")
                    val alpha by pulse.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                        label = "a"
                    )
                    Box(Modifier.size(7.dp).background(SuccessGreen.copy(alpha), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE  •  ${fmt.format(Date(v.timestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                }
            }

            AnimatedVisibility(
                visible = aiExplanation.isNotBlank(),
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    AiAnalysisCard(explanation = aiExplanation, dark = dark)
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isSessionActive) {
                EndSessionButton(
                    reportState = reportState,
                    dark        = dark,
                    onClick     = { vm.endSessionAndReport() }
                )
            }

            Spacer(Modifier.height(bottomPadding + 28.dp))
        }
    }
}

// ── Session Controls ──────────────────────────────────────────────────────────

@Composable
private fun SessionControls(
    isSessionActive     : Boolean,
    sessionReadingCount : Int,
    reportState         : HealthViewModel.ReportState,
    dark                : Boolean,
    onStart             : () -> Unit,
    onCancel            : () -> Unit
) {
    val isGenerating = reportState is HealthViewModel.ReportState.Generating
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isSessionActive) {
            Button(
                onClick  = onStart,
                modifier = Modifier.weight(1f).height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start Session", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SuccessGreen.copy(0.08f))
                    .border(1.dp, SuccessGreen.copy(0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pulse = rememberInfiniteTransition(label = "sess")
                val alpha by pulse.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "sp"
                )
                Box(Modifier.size(7.dp).background(SuccessGreen.copy(alpha), CircleShape))
                Column {
                    Text("Session Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold)
                    Text("$sessionReadingCount readings stored",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                }
            }
            OutlinedButton(
                onClick  = onCancel,
                enabled  = !isGenerating,
                modifier = Modifier.height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                border   = BorderStroke(1.dp, ErrorRed.copy(0.5f)),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Dual Health Oscilloscope (EMG + Cardiac Pulse Wave) ────────────────────

enum class OscilloscopeTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    EMG("EMG Wave", Icons.Default.ElectricBolt),
    CARDIAC("Heart Pulse", Icons.Default.Favorite),
    DUAL("Dual Mode", Icons.Default.Tune)
}

@Composable
private fun DualHealthOscilloscope(
    emgBuffer    : List<Float>,
    cardiacBuffer: List<Float>,
    emgColor     : Color,
    liveBpm      : Int?,
    dark         : Boolean
) {
    var selectedTab by remember { mutableStateOf(OscilloscopeTab.EMG) }
    val surfaceColor = if (dark) DarkSurface else LightSurface
    val borderColor  = if (dark) DarkBorder  else LightBorder
    val labelColor   = if (dark) TextSecondary else TextSecondaryLight
    val cardiacColor = Color(0xFFFF3366) // Vibrant neon cardiac crimson

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        // Tab selector pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OscilloscopeTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val tabColor = when (tab) {
                    OscilloscopeTab.EMG     -> emgColor
                    OscilloscopeTab.CARDIAC -> cardiacColor
                    OscilloscopeTab.DUAL    -> Blue500
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) tabColor.copy(0.16f) else (if (dark) DarkSurfaceElevated else LightSurfaceElevated))
                        .border(1.dp, if (isSelected) tabColor.copy(0.6f) else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { selectedTab = tab }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) tabColor else labelColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) (if (dark) TextPrimary else TextPrimaryLight) else labelColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when (selectedTab) {
            OscilloscopeTab.EMG -> {
                EmgWaveformCanvas(buffer = emgBuffer, color = emgColor, dark = dark, height = 150.dp)
            }
            OscilloscopeTab.CARDIAC -> {
                CardiacWaveformCanvas(buffer = cardiacBuffer, color = cardiacColor, liveBpm = liveBpm, dark = dark, height = 150.dp)
            }
            OscilloscopeTab.DUAL -> {
                EmgWaveformCanvas(buffer = emgBuffer, color = emgColor, dark = dark, height = 110.dp)
                Spacer(Modifier.height(12.dp))
                CardiacWaveformCanvas(buffer = cardiacBuffer, color = cardiacColor, liveBpm = liveBpm, dark = dark, height = 110.dp)
            }
        }
    }
}

@Composable
private fun EmgWaveformCanvas(
    buffer: List<Float>,
    color: Color,
    dark: Boolean,
    height: Dp = 150.dp
) {
    val gridColor  = if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.06f)
    val labelColor = if (dark) TextSecondary else TextSecondaryLight

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val pulse = rememberInfiniteTransition(label = "emg_osc")
            val alpha by pulse.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                label = "ea"
            )
            Box(Modifier.size(7.dp).background(color.copy(alpha), CircleShape))
            Text("EMG Muscle Wave",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = FontWeight.SemiBold)
        }
        Text("0 – 1023 ADC",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor)
    }

    Spacer(Modifier.height(8.dp))

    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(height)
    ) {
        val w = size.width
        val h = size.height
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        val warnY   = h - h * (AnomalyThresholds.EMG_HIGH_WARNING.toFloat()    / 1023f)
        val critY   = h - h * (AnomalyThresholds.EMG_HIGH_CRITICAL.toFloat()   / 1023f)
        val activeY = h - h * (AnomalyThresholds.EMG_ACTIVE_THRESHOLD.toFloat()/ 1023f)
        drawLine(SuccessGreen.copy(0.25f), Offset(0f, activeY), Offset(w, activeY), strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        drawLine(WarnAmber.copy(0.35f),    Offset(0f, warnY),   Offset(w, warnY),   strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        drawLine(ErrorRed.copy(0.35f),     Offset(0f, critY),   Offset(w, critY),   strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        if (buffer.size < 2) return@Canvas
        val step = w / (buffer.size - 1).toFloat()
        val path = Path()
        buffer.forEachIndexed { i, v ->
            val x = i * step
            val y = h - h * (v / 1023f).coerceIn(0f, 1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fillPath = Path().apply {
            addPath(path)
            lineTo((buffer.size - 1) * step, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fillPath, brush = Brush.verticalGradient(
            colors = listOf(color.copy(0.18f), color.copy(0f)), startY = 0f, endY = h))
        drawPath(path, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round))
        val lastX = (buffer.size - 1) * step
        val lastY = h - h * (buffer.last() / 1023f).coerceIn(0f, 1f)
        drawCircle(color, radius = 5f, center = Offset(lastX, lastY))
        drawCircle(color.copy(0.25f), radius = 10f, center = Offset(lastX, lastY))
    }

    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0",    style = MaterialTheme.typography.labelSmall, color = labelColor, fontSize = 9.sp)
        Text("256",  style = MaterialTheme.typography.labelSmall, color = labelColor, fontSize = 9.sp)
        Text("512",  style = MaterialTheme.typography.labelSmall, color = labelColor, fontSize = 9.sp)
        Text("768",  style = MaterialTheme.typography.labelSmall, color = labelColor, fontSize = 9.sp)
        Text("1023", style = MaterialTheme.typography.labelSmall, color = labelColor, fontSize = 9.sp)
    }
}

@Composable
private fun CardiacWaveformCanvas(
    buffer : List<Float>,
    color  : Color,
    liveBpm: Int?,
    dark   : Boolean,
    height : Dp = 150.dp
) {
    val gridColor  = if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.06f)
    val labelColor = if (dark) TextSecondary else TextSecondaryLight

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val pulse = rememberInfiniteTransition(label = "pulse_osc")
            val alpha by pulse.animateFloat(
                initialValue = 0.35f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
                label = "pa"
            )
            Box(Modifier.size(7.dp).background(color.copy(alpha), CircleShape))
            Text("Cardiac Pulse Wave (PPG)",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = if (liveBpm != null) "$liveBpm BPM" else "Syncing...",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(Modifier.height(8.dp))

    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(height)
    ) {
        val w = size.width
        val h = size.height

        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        val centerY = h * 0.5f
        drawLine(
            color.copy(0.2f),
            Offset(0f, centerY),
            Offset(w, centerY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )

        if (buffer.size < 2) return@Canvas

        val minVal = 350f
        val maxVal = 700f
        val range  = (maxVal - minVal).coerceAtLeast(1f)
        val step   = w / (buffer.size - 1).toFloat()

        val path = Path()
        buffer.forEachIndexed { i, v ->
            val x = i * step
            val y = h - h * ((v - minVal) / range).coerceIn(0f, 1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo((buffer.size - 1) * step, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(0.22f), color.copy(0f)),
                startY = 0f,
                endY = h
            )
        )
        drawPath(path, color = color, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val lastX = (buffer.size - 1) * step
        val lastY = h - h * ((buffer.last() - minVal) / range).coerceIn(0f, 1f)
        drawCircle(color, radius = 5f, center = Offset(lastX, lastY))
        drawCircle(color.copy(0.28f), radius = 11f, center = Offset(lastX, lastY))
    }

    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Diastole", style = MaterialTheme.typography.labelSmall, color = labelColor, fontSize = 9.sp)
        Text("Arterial PPG", style = MaterialTheme.typography.labelSmall, color = labelColor, fontSize = 9.sp)
        Text("Systole", style = MaterialTheme.typography.labelSmall, color = labelColor, fontSize = 9.sp)
    }
}

// ── Threshold chip ────────────────────────────────────────────────────────────

@Composable
private fun ThresholdChip(label: String, range: String, color: Color, dark: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = color, fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
        Text(range, style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextSecondary else TextSecondaryLight, fontSize = 8.sp)
    }
}

// ── AI Analysis Card ──────────────────────────────────────────────────────────

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
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Psychology, null, tint = Blue500, modifier = Modifier.size(18.dp))
            Text("AI Analysis", style = MaterialTheme.typography.labelMedium,
                color = Blue500, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(explanation, style = MaterialTheme.typography.bodySmall,
            color = if (dark) TextPrimary else TextPrimaryLight, lineHeight = 18.sp)
    }
}

// ── End Session Button ────────────────────────────────────────────────────────

@Composable
private fun EndSessionButton(
    reportState : HealthViewModel.ReportState,
    dark        : Boolean,
    onClick     : () -> Unit
) {
    val isGenerating = reportState is HealthViewModel.ReportState.Generating
    val stepText = if (isGenerating)
        (reportState as HealthViewModel.ReportState.Generating).step
    else "End Session & Generate Report"
    Button(
        onClick  = onClick,
        enabled  = !isGenerating,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = if (dark) DarkSurfaceElevated else LightSurfaceElevated,
            contentColor           = if (dark) TextPrimary else TextPrimaryLight,
            disabledContainerColor = if (dark) DarkSurface else LightSurface,
            disabledContentColor   = if (dark) TextDisabled else TextSecondaryLight
        ),
        border = BorderStroke(1.dp, if (dark) DarkBorder else LightBorder)
    ) {
        if (isGenerating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp),
                color = Blue500, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(stepText, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.Default.Assessment, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("End Session & Generate Report", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Connection Badge ──────────────────────────────────────────────────────────

@Composable
private fun ConnectionBadge(btState: BtState, dark: Boolean, onClick: () -> Unit) {
    val (label, color) = when (btState) {
        is BtState.Connected    -> "Connected"    to SuccessGreen
        is BtState.Connecting   -> "Connecting…"  to WarnAmber
        is BtState.Error        -> "Error"         to ErrorRed
        is BtState.Disconnected -> "Disconnected" to (if (dark) TextSecondary else TextSecondaryLight)
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
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ── Vital Card ────────────────────────────────────────────────────────────────

@Composable
private fun VitalCard(
    icon      : androidx.compose.ui.graphics.vector.ImageVector,
    label     : String,
    value     : String,
    unit      : String,
    sourceTag : String,
    color     : Color,
    dark      : Boolean,
    modifier  : Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp)
                    .background(color.copy(0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(
                sourceTag,
                style = MaterialTheme.typography.labelSmall,
                color = if (sourceTag == "LIVE SENSOR") SuccessGreen.copy(0.8f)
                        else WarnAmber.copy(0.8f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextSecondary else TextSecondaryLight)
        Row(verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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

// ── Alert Banner ──────────────────────────────────────────────────────────────

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
