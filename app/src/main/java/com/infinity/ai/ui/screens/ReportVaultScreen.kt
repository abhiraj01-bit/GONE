package com.infinity.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.infinity.ai.health.data.HealthReportEntity
import com.infinity.ai.ui.components.GradientBackground
import com.infinity.ai.ui.theme.*
import com.infinity.ai.viewmodel.HealthViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportVaultScreen(
    isDarkTheme   : Boolean,
    bottomPadding : Dp,
    onOpenReport  : (Long) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val vm: HealthViewModel = viewModel()
    val reports by vm.allReports.collectAsState()
    val dark = isDarkTheme

    GradientBackground(darkTheme = dark, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, null,
                        tint = if (dark) TextPrimary else TextPrimaryLight)
                }
                Column(Modifier.weight(1f)) {
                    Text("Report Vault",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (dark) TextPrimary else TextPrimaryLight,
                        fontWeight = FontWeight.Bold)
                    Text("${reports.size} saved report${if (reports.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                }
            }
            Spacer(Modifier.height(16.dp))

            if (reports.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null,
                            tint = if (dark) TextSecondary else TextSecondaryLight,
                            modifier = Modifier.size(56.dp))
                        Text("No reports yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (dark) TextPrimary else TextPrimaryLight,
                            fontWeight = FontWeight.SemiBold)
                        Text("Complete a monitoring session to generate your first report.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dark) TextSecondary else TextSecondaryLight)
                    }
                }
            } else {
                val bottomPad = bottomPadding + 24.dp
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reports, key = { it.id }) { report ->
                        VaultReportCard(
                            report  = report,
                            dark    = dark,
                            onClick = { onOpenReport(report.id) }
                        )
                    }
                    item { ReportVaultDebugPanel(reports = reports, dark = dark) }
                }
            }
        }
    }
}


@Composable
private fun VaultReportCard(
    report : HealthReportEntity,
    dark   : Boolean,
    onClick: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val (statusColor, statusIcon) = when (report.overallStatus) {
        "Concerning"      -> ErrorRed    to Icons.Default.Warning
        "Needs Attention" -> WarnAmber   to Icons.Default.Info
        else              -> SuccessGreen to Icons.Default.CheckCircle
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) DarkSurface else LightSurface)
            .border(1.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Blue500.copy(0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MonitorHeart, null,
                        tint = Blue500, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Health Session",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (dark) TextPrimary else TextPrimaryLight,
                        fontWeight = FontWeight.Bold)
                    Text(dateFmt.format(Date(report.sessionStart)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) TextSecondary else TextSecondaryLight)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${report.durationMinutes} min",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (dark) TextPrimary else TextPrimaryLight,
                    fontWeight = FontWeight.SemiBold)
                Text(timeFmt.format(Date(report.sessionStart)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dark) TextSecondary else TextSecondaryLight)
            }
        }

        HorizontalDivider(
            color = if (dark) DarkBorder else LightBorder,
            thickness = 0.5.dp
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VaultStat(
                label    = "EMG",
                value    = report.emgAverage?.let { "%.0f".format(it) } ?: "--",
                color    = vaultEmgColor(report.emgAverage),
                modifier = Modifier.weight(1f)
            )
            VaultStat(
                label    = "Heart Rate",
                value    = report.bpmAverage?.let { "%.0f BPM".format(it) } ?: "--",
                color    = vaultBpmColor(report.bpmAverage),
                modifier = Modifier.weight(1f)
            )
            VaultStat(
                label    = "Readings",
                value    = "${report.totalReadings}",
                color    = if (dark) TextPrimary else TextPrimaryLight,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(0.1f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(statusIcon, null,
                    tint = statusColor, modifier = Modifier.size(12.dp))
                Text(
                    report.overallStatus.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Blue500.copy(0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("View Report",
                    style = MaterialTheme.typography.labelSmall,
                    color = Blue500,
                    fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, null,
                    tint = Blue500, modifier = Modifier.size(14.dp))
            }
        }

        // ── Email delivery status indicator (additive) ────────────────────────
        EmailStatusChip(report.emailStatus, dark)
    }
}

@Composable
private fun EmailStatusChip(emailStatus: String, dark: Boolean) {
    if (emailStatus == "NOT_REQUESTED") return
    val (icon, label, color) = when (emailStatus) {
        "SENT"    -> Triple(Icons.Default.MarkEmailRead, "Email sent",    SuccessGreen)
        "PENDING" -> Triple(Icons.Default.Schedule,      "Email pending", WarnAmber)
        "SENDING" -> Triple(Icons.Default.Send,          "Sending...",    Blue500)
        "FAILED"  -> Triple(Icons.Default.ErrorOutline,  "Email failed",  ErrorRed)
        else      -> return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(11.dp))
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp)
    }
}

@Composable
private fun VaultStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold)
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(0.6f),
            fontSize = 9.sp)
    }
}

private fun vaultEmgColor(avg: Float?): Color = when {
    avg == null -> SuccessGreen
    avg >= 900f -> ErrorRed
    avg >= 700f -> WarnAmber
    avg >= 400f -> Blue500
    else        -> SuccessGreen
}

private fun vaultBpmColor(avg: Float?): Color = when {
    avg == null             -> SuccessGreen
    avg < 50f || avg > 100f -> ErrorRed
    avg < 60f || avg > 90f  -> WarnAmber
    else                    -> SuccessGreen
}

// ── Dev Debug Panel ───────────────────────────────────────────────────────────
// Shows live Room persistence state. Subtle, informational, dev-only.
// Appears after the last report card in the LazyColumn.

@Composable
private fun ReportVaultDebugPanel(reports: List<HealthReportEntity>, dark: Boolean) {
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()) }
    val latest  = reports.firstOrNull()
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (dark) DarkSurface.copy(0.6f) else LightSurface.copy(0.6f))
            .border(0.5.dp, if (dark) DarkBorder else LightBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(SuccessGreen, CircleShape))
            Text("DB Debug — gone_health.db › health_reports",
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) TextSecondary else TextSecondaryLight,
                fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
        }
        DebugRow("Saved reports",    "${reports.size}", dark)
        DebugRow("Latest report ID", latest?.id?.toString() ?: "—", dark)
        DebugRow("Latest saved",     latest?.createdAt?.let { dateFmt.format(Date(it)) } ?: "—", dark)
        DebugRow("Latest session",   latest?.let { "${it.durationMinutes} min · ${it.totalReadings} readings" } ?: "—", dark)
        DebugRow("Latest status",    latest?.overallStatus ?: "—", dark)
    }
}

@Composable
private fun DebugRow(label: String, value: String, dark: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = (if (dark) TextSecondary else TextSecondaryLight).copy(0.7f),
            fontSize = 9.sp)
        Text(value, style = MaterialTheme.typography.labelSmall,
            color = if (dark) TextSecondary else TextSecondaryLight,
            fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
    }
}
