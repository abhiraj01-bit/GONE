package com.infinity.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinity.ai.health.data.HealthReportEntity
import com.infinity.ai.health.data.ValueSource
import com.infinity.ai.ui.components.GradientBackground
import com.infinity.ai.ui.theme.*
import com.infinity.ai.viewmodel.HealthViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun ReportViewScreen(
    reportId      : Long,
    isDarkTheme   : Boolean,
    bottomPadding : Dp,
    onNavigateBack: () -> Unit
) {
    val vm: HealthViewModel = viewModel()
    val dark = isDarkTheme
    val report by vm.getReportFlow(reportId).collectAsState(initial = null)
    var initialLoadDone by remember { mutableStateOf(false) }

    LaunchedEffect(report) {
        if (report != null) initialLoadDone = true
    }

    GradientBackground(darkTheme = dark, modifier = Modifier.fillMaxSize()) {
        if (!initialLoadDone && report == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue500)
            }
            return@GradientBackground
        }

        val r = report
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Report not found.", color = if (dark) TextSecondary else TextSecondaryLight)
            }
            return@GradientBackground
        }

        val bottomPad = bottomPadding + 32.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = bottomPad)
        ) {
            item { ReportHeader(r, dark, onNavigateBack) }
            item { Spacer(Modifier.height(16.dp)) }
            item { OverallStatusCard(r, dark) }
            item { Spacer(Modifier.height(16.dp)) }
            item { SessionOverviewCard(r, dark) }
            item { Spacer(Modifier.height(16.dp)) }
            item { VitalsSummarySection(r, dark) }
            item { Spacer(Modifier.height(16.dp)) }
            item { RepresentativePointsCard(r, dark) }

            val observations = parseJsonStringArray(r.observationsJson)
            if (observations.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)) }
                item { ObservationsCard(observations, dark) }
            }

            val concerns = parsePhysicalConcerns(r.physicalConcernsJson)
            if (concerns.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)) }
                item { ReportSectionHeader("Physical Concerns", Icons.Default.MonitorHeart, dark) }
                items(concerns) { c ->
                    Spacer(Modifier.height(8.dp))
                    ConcernCard(c, dark)
                }
            }

            val food = parseJsonStringArray(r.foodRecommendationsJson)
            if (food.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)) }
                item { RecommendationSection("Recommended Nutrition", Icons.Default.Restaurant, food, SuccessGreen, dark) }
            }

            val exercise = parseJsonStringArray(r.exerciseRecommendationsJson)
            if (exercise.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)) }
                item { RecommendationSection("Recommended Activity", Icons.Default.DirectionsRun, exercise, Blue500, dark) }
            }

            val lifestyle = parseJsonStringArray(r.lifestyleRecommendationsJson)
            if (lifestyle.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)) }
                item { RecommendationSection("Lifestyle", Icons.Default.Spa, lifestyle, WarnAmber, dark) }
            }

            val medical = parseJsonStringArray(r.medicalAttentionJson)
            if (medical.isNotEmpty()) {
                item { Spacer(Modifier.height(16.dp)) }
                item { MedicalAttentionCard(medical, dark) }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item { DisclaimerCard(dark) }
        }
    }
}

// ── Report Header ─────────────────────────────────────────────────────────────

@Composable
private fun ReportHeader(r: HealthReportEntity, dark: Boolean, onBack: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, null,
                tint = if (dark) TextPrimary else TextPrimaryLight)
        }
        Column(Modifier.weight(1f)) {
            Text("Session Report",
                style = MaterialTheme.typography.headlineSmall,
                color = if (dark) TextPrimary else TextPrimaryLight,
                fontWeight = FontWeight.Bold)
            Text(dateFmt.format(Date(r.sessionStart)),
                style = MaterialTheme.typography.bodySmall,
                color = if (dark) TextSecondary else TextSecondaryLight)
        }
        val isAiEnhanced = r.rawAiJson.isNotBlank()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isAiEnhanced) Blue500.copy(0.12f) else WarnAmber.copy(0.12f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isAiEnhanced) "G-ONE AI • Verified" else "G-ONE AI • Refining...",
                style = MaterialTheme.typography.labelSmall,
                color = if (isAiEnhanced) Blue500 else WarnAmber,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Overall Status Card ───────────────────────────────────────────────────────

@Composable
private fun OverallStatusCard(r: HealthReportEntity, dark: Boolean) {
    val (statusColor, statusIcon) = when (r.overallStatus) {
        "Concerning"      -> ErrorRed   to Icons.Default.Warning
        "Needs Attention" -> WarnAmber  to Icons.Default.Info
        else              -> SuccessGreen to Icons.Default.CheckCircle
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(statusColor.copy(0.08f))
            .border(1.dp, statusColor.copy(0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp)
                .background(statusColor.copy(0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Overall Status",
                style = MaterialTheme.typography.labelSmall,
                color = statusColor.copy(0.8f))
            Text(r.overallStatus.ifBlank { "Unknown" },
                style = MaterialTheme.typography.titleLarge,
                color = statusColor,
                fontWeight = FontWeight.Bold)
            if (r.aiSummary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(r.aiSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    lineHeight = 18.sp)
            }
        }
    }
}

// ── Session Overview Card ─────────────────────────────────────────────────────

@Composable
private fun SessionOverviewCard(r: HealthReportEntity, dark: Boolean) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    SectionCard(dark = dark) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AccessTime, null,
                tint = Blue500, modifier = Modifier.size(16.dp))
            Text("Session Overview",
                style = MaterialTheme.typography.labelMedium,
                color = Blue500, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewStat("Start",    timeFmt.format(Date(r.sessionStart)), dark, Modifier.weight(1f))
            OverviewStat("End",      timeFmt.format(Date(r.sessionEnd)),   dark, Modifier.weight(1f))
            OverviewStat("Duration", "${r.durationMinutes} min",           dark, Modifier.weight(1f))
            OverviewStat("Readings", "${r.totalReadings}",                 dark, Modifier.weight(1f))
        }
        if (r.fallEventCount > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ErrorRed.copy(0.1f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                Text("${r.fallEventCount} fall event${if (r.fallEventCount > 1) "s" else ""} detected",
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OverviewStat(label: String, value: String, dark: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value,
            style = MaterialTheme.typography.titleSmall,
            color = if (dark) TextPrimary else TextPrimaryLight,
            fontWeight = FontWeight.Bold)
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextSecondary else TextSecondaryLight)
    }
}

// ── Vitals Summary ────────────────────────────────────────────────────────────

@Composable
private fun VitalsSummarySection(r: HealthReportEntity, dark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.BarChart, null,
                tint = Blue500, modifier = Modifier.size(16.dp))
            Text("Vitals Summary",
                style = MaterialTheme.typography.labelMedium,
                color = Blue500, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VitalStatCard(
                label    = "EMG",
                avg      = r.emgAverage?.let { "%.0f".format(it) } ?: "--",
                min      = r.emgMin?.toString() ?: "--",
                max      = r.emgMax?.toString() ?: "--",
                unit     = "ADC",
                source   = "LIVE SENSOR",
                color    = emgStatusColor(r.emgAverage),
                dark     = dark,
                modifier = Modifier.weight(1f)
            )
            VitalStatCard(
                label    = "Heart Rate",
                avg      = r.bpmAverage?.let { "%.0f".format(it) } ?: "--",
                min      = r.bpmMin?.toString() ?: "--",
                max      = r.bpmMax?.toString() ?: "--",
                unit     = "BPM",
                source   = "LIVE SENSOR",
                color    = bpmStatusColor(r.bpmAverage),
                dark     = dark,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VitalStatCard(
                label    = "Temperature",
                avg      = r.temperature?.let { "%.1f".format(it) } ?: "--",
                min      = "--",
                max      = "--",
                unit     = "°C",
                source   = if (r.temperatureSource == ValueSource.DERIVED) "ESTIMATED" else "N/A",
                color    = WarnAmber,
                dark     = dark,
                modifier = Modifier.weight(1f)
            )
            VitalStatCard(
                label    = "SpO\u2082",
                avg      = r.spo2?.toString() ?: "--",
                min      = "--",
                max      = "--",
                unit     = "%",
                source   = if (r.spo2Source == ValueSource.DERIVED) "ESTIMATED" else "N/A",
                color    = Blue400,
                dark     = dark,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VitalStatCard(
    label: String, avg: String, min: String, max: String,
    unit: String, source: String, color: Color, dark: Boolean, modifier: Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight)
            Text(source, style = MaterialTheme.typography.labelSmall,
                color = if (source == "LIVE SENSOR") SuccessGreen.copy(0.8f) else WarnAmber.copy(0.8f),
                fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(avg, style = MaterialTheme.typography.headlineSmall,
                color = color, fontWeight = FontWeight.Bold)
            Text(unit, style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight,
                modifier = Modifier.padding(bottom = 3.dp))
        }
        Text("avg", style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextSecondary else TextSecondaryLight)
        HorizontalDivider(color = if (dark) DarkBorder else LightBorder, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(min, style = MaterialTheme.typography.labelMedium,
                    color = if (dark) TextPrimary else TextPrimaryLight, fontWeight = FontWeight.SemiBold)
                Text("min", style = MaterialTheme.typography.labelSmall,
                    color = if (dark) TextSecondary else TextSecondaryLight)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(max, style = MaterialTheme.typography.labelMedium,
                    color = if (dark) TextPrimary else TextPrimaryLight, fontWeight = FontWeight.SemiBold)
                Text("max", style = MaterialTheme.typography.labelSmall,
                    color = if (dark) TextSecondary else TextSecondaryLight)
            }
        }
    }
}

// ── Representative Points ─────────────────────────────────────────────────────

@Composable
private fun RepresentativePointsCard(r: HealthReportEntity, dark: Boolean) {
    val points = parseRepresentativeReadings(r.representativeReadingsJson)
    SectionCard(dark = dark) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Timeline, null,
                tint = Blue500, modifier = Modifier.size(16.dp))
            Text("Session Trend",
                style = MaterialTheme.typography.labelMedium,
                color = Blue500, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        // Header row
        Row(Modifier.fillMaxWidth()) {
            Text("Point", Modifier.weight(1.2f),
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight,
                fontWeight = FontWeight.SemiBold)
            Text("EMG", Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight,
                fontWeight = FontWeight.SemiBold)
            Text("BPM", Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight,
                fontWeight = FontWeight.SemiBold)
            Text("Status", Modifier.weight(1.2f),
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = if (dark) DarkBorder else LightBorder, thickness = 0.5.dp)
        points.forEach { pt ->
            Spacer(Modifier.height(8.dp))
            val statusColor = when (pt.status) {
                "Spasm"    -> ErrorRed
                "Elevated" -> WarnAmber
                "Active"   -> Blue500
                else       -> SuccessGreen
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(pt.label, Modifier.weight(1.2f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    fontWeight = FontWeight.SemiBold)
                Text(pt.emg, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextPrimary else TextPrimaryLight)
                Text(pt.bpm, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextPrimary else TextPrimaryLight)
                Box(
                    modifier = Modifier.weight(1.2f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(pt.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Observations Card ─────────────────────────────────────────────────────────

@Composable
private fun ObservationsCard(observations: List<String>, dark: Boolean) {
    SectionCard(dark = dark) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Lightbulb, null,
                tint = WarnAmber, modifier = Modifier.size(16.dp))
            Text("Key Observations",
                style = MaterialTheme.typography.labelMedium,
                color = WarnAmber, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        observations.forEach { obs ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(5.dp).background(WarnAmber, CircleShape)
                    .align(Alignment.CenterVertically))
                Text(obs,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    lineHeight = 18.sp)
            }
        }
    }
}

// ── Physical Concern Card ─────────────────────────────────────────────────────

data class PhysicalConcern(val title: String, val description: String, val severity: String)

@Composable
private fun ConcernCard(concern: PhysicalConcern, dark: Boolean) {
    val (color, label) = when (concern.severity.lowercase()) {
        "high"     -> ErrorRed  to "High"
        "moderate" -> WarnAmber to "Moderate"
        else       -> SuccessGreen to "Low"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(0.07f))
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp)
                .background(color.copy(0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MedicalServices, null,
                tint = color, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(concern.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = color, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
            Text(concern.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (dark) TextSecondary else TextSecondaryLight,
                lineHeight = 18.sp)
        }
    }
}

// ── Recommendation Section ────────────────────────────────────────────────────

@Composable
private fun RecommendationSection(
    title: String, icon: ImageVector, items: List<String>, color: Color, dark: Boolean
) {
    SectionCard(dark = dark) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(5.dp).background(color.copy(0.7f), CircleShape)
                    .align(Alignment.CenterVertically))
                Text(item,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    lineHeight = 18.sp)
            }
        }
    }
}

// ── Medical Attention Card ────────────────────────────────────────────────────

@Composable
private fun MedicalAttentionCard(items: List<String>, dark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ErrorRed.copy(0.07f))
            .border(1.dp, ErrorRed.copy(0.3f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.LocalHospital, null,
                tint = ErrorRed, modifier = Modifier.size(16.dp))
            Text("When to Seek Medical Attention",
                style = MaterialTheme.typography.labelMedium,
                color = ErrorRed, fontWeight = FontWeight.Bold)
        }
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(5.dp).background(ErrorRed.copy(0.7f), CircleShape)
                    .align(Alignment.CenterVertically))
                Text(item,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    lineHeight = 18.sp)
            }
        }
    }
}

// ── Disclaimer Card ───────────────────────────────────────────────────────────

@Composable
private fun DisclaimerCard(dark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Info, null,
            tint = if (dark) TextSecondary else TextSecondaryLight,
            modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Text(
            "This report is an AI-assisted interpretation of session data and is not a medical " +
            "diagnosis. Food, exercise, and lifestyle recommendations are general wellness guidance " +
            "only and are not a substitute for advice from a qualified clinician or dietitian.",
            style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextSecondary else TextSecondaryLight,
            lineHeight = 16.sp
        )
    }
}

// ── Shared section helpers ────────────────────────────────────────────────────

@Composable
private fun ReportSectionHeader(title: String, icon: ImageVector, dark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Blue500, modifier = Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.labelMedium,
            color = Blue500, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionCard(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

// ── Color helpers ─────────────────────────────────────────────────────────────

private fun emgStatusColor(avg: Float?): Color = when {
    avg == null  -> SuccessGreen
    avg >= 900f  -> ErrorRed
    avg >= 700f  -> WarnAmber
    avg >= 400f  -> Blue500
    else         -> SuccessGreen
}

private fun bpmStatusColor(avg: Float?): Color = when {
    avg == null                  -> SuccessGreen
    avg < 50f || avg > 100f      -> ErrorRed
    avg < 60f || avg > 90f       -> WarnAmber
    else                         -> SuccessGreen
}

// ── Data parsing helpers ──────────────────────────────────────────────────────

private data class RepPoint(val label: String, val emg: String, val bpm: String, val status: String)

private fun parseRepresentativeReadings(json: String): List<RepPoint> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            RepPoint(
                label  = obj.optString("label", "--"),
                emg    = obj.opt("emg")?.let { if (it == JSONObject.NULL) "--" else it.toString() } ?: "--",
                bpm    = obj.opt("bpm")?.let { if (it == JSONObject.NULL) "--" else it.toString() } ?: "--",
                status = obj.optString("status", "Normal")
            )
        }
    } catch (e: Exception) { emptyList() }
}

private fun parseJsonStringArray(json: String): List<String> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) { emptyList() }
}

private fun parsePhysicalConcerns(json: String): List<PhysicalConcern> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            PhysicalConcern(
                title       = obj.optString("title", ""),
                description = obj.optString("description", ""),
                severity    = obj.optString("severity", "low")
            )
        }
    } catch (e: Exception) { emptyList() }
}
