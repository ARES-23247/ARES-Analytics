package com.ares.analytics.ui.components.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.AlertRecord
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.ui.components.NavigationTarget
import com.ares.analytics.ui.theme.AresAmber
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGold
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary

/** Classification of active data source for clear evidence vs. simulation vs. replay boundary. */
enum class DashboardDataSourceType(
    val label: String,
    val badge: String,
    val icon: ImageVector,
    val explanation: String
) {
    SIMULATION_TRUTH(
        label = "Local Simulator",
        badge = "SIM TRUTH",
        icon = Icons.Default.Computer,
        explanation = "Synthetic dyn4j 2D physics ground truth and simulated IO"
    ),
    LIVE_ROBOT_FTC(
        label = "FTC Robot (Control Hub)",
        badge = "HARDWARE",
        icon = Icons.Default.PrecisionManufacturing,
        explanation = "Real-time measurements from FTC REV Control Hub"
    ),
    LIVE_ROBOT_FRC(
        label = "FRC Robot (RoboRIO)",
        badge = "HARDWARE",
        icon = Icons.Default.Memory,
        explanation = "Real-time measurements from FRC RoboRIO and CTRE CAN bus"
    ),
    HISTORICAL_REPLAY(
        label = "Historical Replay",
        badge = "REPLAY",
        icon = Icons.Default.Replay,
        explanation = "Deterministic log playback from DuckDB persistent session"
    ),
    NO_ACTIVE_SOURCE(
        label = "Offline / No Active Source",
        badge = "OFFLINE",
        icon = Icons.Default.WifiOff,
        explanation = "No live telemetry streaming. Select Local Sim or connect a robot."
    )
}

/** Freshness classification for real-time telemetry. */
enum class TelemetryFreshness(val label: String, val badge: String, val color: Color) {
    FRESH("Fresh", "LIVE", AresGreen),
    STALE("Stale (>500ms)", "STALE", AresAmber),
    INACTIVE("Inactive", "OFFLINE", AresTextTertiary)
}

/** Snapshot of mission control state computed from live environment. */
data class DashboardMissionSnapshot(
    val workspace: WorkspaceConfig,
    val isConnected: Boolean,
    val isLocalSimulator: Boolean,
    val isSimulatorRunning: Boolean,
    val isReplayActive: Boolean,
    val primarySessionId: String?,
    val loopTimeMs: Double? = null,
    val batteryVoltage: Double? = null,
    val brownoutCount: Int? = null,
    val loopOverruns: Int? = null,
    val activeAlerts: List<AlertRecord> = emptyList(),
    val frameRateHz: Double = 0.0,
    val lastUpdateAgeMs: Long = -1L,
    val hostIp: String = "127.0.0.1"
) {
    val sourceType: DashboardDataSourceType
        get() = when {
            isReplayActive || primarySessionId != null -> DashboardDataSourceType.HISTORICAL_REPLAY
            isConnected && isLocalSimulator -> DashboardDataSourceType.SIMULATION_TRUTH
            isConnected && workspace.league == League.FTC -> DashboardDataSourceType.LIVE_ROBOT_FTC
            isConnected && workspace.league == League.FRC -> DashboardDataSourceType.LIVE_ROBOT_FRC
            else -> DashboardDataSourceType.NO_ACTIVE_SOURCE
        }

    val freshness: TelemetryFreshness
        get() = when {
            !isConnected && primarySessionId == null -> TelemetryFreshness.INACTIVE
            lastUpdateAgeMs in 0..500 -> TelemetryFreshness.FRESH
            lastUpdateAgeMs in 501..2000 -> TelemetryFreshness.STALE
            else -> if (isConnected || isReplayActive) TelemetryFreshness.FRESH else TelemetryFreshness.INACTIVE
        }

    val healthSummary: String
        get() = when {
            !isConnected && primarySessionId == null ->
                "No live connection. You can practice safely in the Local Simulator or configure mechanisms in Robot Studio."
            isReplayActive || primarySessionId != null ->
                "Replaying session ${primarySessionId?.take(12) ?: "run"}. Review telemetry trends, alerts, and timeline scrubbing."
            brownoutCount != null && brownoutCount > 0 ->
                "Warning: $brownoutCount brownout events detected! Check battery voltage (${batteryVoltage?.let { String.format("%.2fV", it) } ?: "low"}) and motor current draw."
            batteryVoltage != null && batteryVoltage < 11.5 ->
                "Caution: Low battery voltage (${String.format("%.2fV", batteryVoltage)}). Risk of mechanism stall or brownout under acceleration."
            loopTimeMs != null && loopTimeMs > 30.0 ->
                "Degraded: Control loop period is high (${String.format("%.1f ms", loopTimeMs)} / ${String.format("%.0f Hz", 1000.0 / loopTimeMs)}). Check for blocking I/O."
            else ->
                "All systems nominal. Battery at ${batteryVoltage?.let { String.format("%.2fV", it) } ?: "healthy level"}, control loop ${loopTimeMs?.let { String.format("%.0f Hz", 1000.0 / it) } ?: "active"} with 0 overruns."
        }

    val highestPriorityAlert: AlertRecord?
        get() = activeAlerts.firstOrNull { alert ->
            alert.ruleKey.contains("brownout", ignoreCase = true) ||
                alert.ruleKey.contains("comms", ignoreCase = true) ||
                alert.ruleKey.contains("can", ignoreCase = true) ||
                alert.ruleKey.contains("battery", ignoreCase = true)
        } ?: activeAlerts.firstOrNull()
}

/**
 * Accessible, responsive Mission Control Header for ARES Analytics.
 * Answers the 9 novice student questions in clear, prioritized hierarchy.
 */
@Composable
fun DashboardMissionHeader(
    snapshot: DashboardMissionSnapshot,
    onNavigate: (NavigationTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedDetails by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AresSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AresBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BoxWithConstraints {
                val compact = maxWidth < 880.dp

                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IdentityAndSourceRow(snapshot)
                        HealthAndFreshnessRow(snapshot)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IdentityAndSourceRow(snapshot, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(16.dp))
                        HealthAndFreshnessRow(snapshot)
                    }
                }
            }

            // Plain Language Health Summary & Action Banner
            HealthSummaryBanner(
                snapshot = snapshot,
                onNavigate = onNavigate
            )

            // Quick Navigation Action Strip for Novices
            QuickNavigationStrip(onNavigate = onNavigate)

            // Expandable Technical Diagnostics Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDetails = !expandedDetails }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expandedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expandedDetails) "Collapse technical details" else "Expand technical details",
                        tint = AresTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (expandedDetails) "Hide technical diagnostics" else "Show technical diagnostics & connection metrics",
                        color = AresTextSecondary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "${snapshot.sourceType.badge} • ${snapshot.hostIp}",
                    color = AresTextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            AnimatedVisibility(
                visible = expandedDetails,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TechnicalDiagnosticsPanel(snapshot)
            }
        }
    }
}

@Composable
private fun IdentityAndSourceRow(
    snapshot: DashboardMissionSnapshot,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // League Badge
        val leagueColor = if (snapshot.workspace.league == League.FTC) AresGold else AresCyan
        Surface(
            color = leagueColor.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, leagueColor),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = snapshot.workspace.league.name,
                color = leagueColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Robot & Workspace Name
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = snapshot.workspace.robotName.ifBlank { snapshot.workspace.robotId },
                    color = AresTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Team ${snapshot.workspace.teamId}",
                    color = AresTextSecondary,
                    fontSize = 12.sp
                )
            }

            Text(
                text = snapshot.sourceType.explanation,
                color = AresTextTertiary,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun HealthAndFreshnessRow(snapshot: DashboardMissionSnapshot) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Target & Source Pill
        Surface(
            color = when (snapshot.sourceType) {
                DashboardDataSourceType.SIMULATION_TRUTH -> AresCyan.copy(alpha = 0.12f)
                DashboardDataSourceType.LIVE_ROBOT_FTC, DashboardDataSourceType.LIVE_ROBOT_FRC -> AresGreen.copy(alpha = 0.12f)
                DashboardDataSourceType.HISTORICAL_REPLAY -> AresAmber.copy(alpha = 0.12f)
                DashboardDataSourceType.NO_ACTIVE_SOURCE -> AresSurfaceElevated
            },
            border = BorderStroke(
                1.dp,
                when (snapshot.sourceType) {
                    DashboardDataSourceType.SIMULATION_TRUTH -> AresCyan.copy(alpha = 0.5f)
                    DashboardDataSourceType.LIVE_ROBOT_FTC, DashboardDataSourceType.LIVE_ROBOT_FRC -> AresGreen.copy(alpha = 0.5f)
                    DashboardDataSourceType.HISTORICAL_REPLAY -> AresAmber.copy(alpha = 0.5f)
                    DashboardDataSourceType.NO_ACTIVE_SOURCE -> AresBorder
                }
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = snapshot.sourceType.icon,
                    contentDescription = null,
                    tint = when (snapshot.sourceType) {
                        DashboardDataSourceType.SIMULATION_TRUTH -> AresCyan
                        DashboardDataSourceType.LIVE_ROBOT_FTC, DashboardDataSourceType.LIVE_ROBOT_FRC -> AresGreen
                        DashboardDataSourceType.HISTORICAL_REPLAY -> AresAmber
                        DashboardDataSourceType.NO_ACTIVE_SOURCE -> AresTextTertiary
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = snapshot.sourceType.label,
                    color = AresTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Freshness Indicator
        Surface(
            color = snapshot.freshness.color.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, snapshot.freshness.color.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = when (snapshot.freshness) {
                        TelemetryFreshness.FRESH -> Icons.Default.CheckCircle
                        TelemetryFreshness.STALE -> Icons.Default.Warning
                        TelemetryFreshness.INACTIVE -> Icons.Default.WifiOff
                    },
                    contentDescription = snapshot.freshness.label,
                    tint = snapshot.freshness.color,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = snapshot.freshness.badge,
                    color = snapshot.freshness.color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HealthSummaryBanner(
    snapshot: DashboardMissionSnapshot,
    onNavigate: (NavigationTarget) -> Unit
) {
    val topAlert = snapshot.highestPriorityAlert
    val isCritical = topAlert != null && (
        topAlert.ruleKey.contains("brownout", ignoreCase = true) ||
            topAlert.ruleKey.contains("comms", ignoreCase = true) ||
            topAlert.ruleKey.contains("can", ignoreCase = true) ||
            topAlert.ruleKey.contains("battery", ignoreCase = true)
    )

    Surface(
        color = when {
            isCritical -> AresError.copy(alpha = 0.1f)
            topAlert != null -> AresAmber.copy(alpha = 0.08f)
            !snapshot.isConnected && snapshot.primarySessionId == null -> AresCyan.copy(alpha = 0.06f)
            else -> AresSurfaceElevated
        },
        border = BorderStroke(
            1.dp,
            when {
                isCritical -> AresError.copy(alpha = 0.5f)
                topAlert != null -> AresAmber.copy(alpha = 0.4f)
                !snapshot.isConnected && snapshot.primarySessionId == null -> AresCyan.copy(alpha = 0.35f)
                else -> AresBorder
            }
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = when {
                    isCritical -> Icons.Default.Warning
                    topAlert != null -> Icons.Default.Info
                    !snapshot.isConnected && snapshot.primarySessionId == null -> Icons.Default.PlayCircle
                    else -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = when {
                    isCritical -> AresError
                    topAlert != null -> AresAmber
                    !snapshot.isConnected && snapshot.primarySessionId == null -> AresCyan
                    else -> AresGreen
                },
                modifier = Modifier.size(20.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                if (topAlert != null) {
                    Text(
                        text = "ATTENTION: ${topAlert.ruleKey.replace('_', ' ').uppercase()}",
                        color = if (isCritical) AresError else AresAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = snapshot.healthSummary,
                    color = AresTextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            if (!snapshot.isConnected && snapshot.primarySessionId == null) {
                OutlinedButton(
                    onClick = { onNavigate(NavigationTarget.ACADEMY) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("First Mission Lab", fontSize = 11.sp)
                }
            } else if (topAlert != null) {
                OutlinedButton(
                    onClick = { onNavigate(NavigationTarget.TUNING) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Investigate", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun QuickNavigationStrip(onNavigate: (NavigationTarget) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Quick jump:", color = AresTextTertiary, fontSize = 11.sp)

        QuickNavChip(
            label = "Robot Studio",
            icon = Icons.Default.PrecisionManufacturing,
            onClick = { onNavigate(NavigationTarget.ROBOT_STUDIO) }
        )
        QuickNavChip(
            label = "Academy Labs",
            icon = Icons.Default.School,
            onClick = { onNavigate(NavigationTarget.ACADEMY) }
        )
        QuickNavChip(
            label = "Autonomous",
            icon = Icons.AutoMirrored.Filled.AltRoute,
            onClick = { onNavigate(NavigationTarget.PATH_PLANNER) }
        )
        QuickNavChip(
            label = "Run History",
            icon = Icons.Default.History,
            onClick = { onNavigate(NavigationTarget.RUN_HISTORY) }
        )
        QuickNavChip(
            label = "Imports",
            icon = Icons.Default.CloudUpload,
            onClick = { onNavigate(NavigationTarget.IMPORT_CENTER) }
        )
        QuickNavChip(
            label = "Tuning",
            icon = Icons.Default.Tune,
            onClick = { onNavigate(NavigationTarget.TUNING) }
        )
    }
}

@Composable
private fun QuickNavChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        color = AresSurfaceElevated,
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AresCyan, modifier = Modifier.size(13.dp))
            Text(text = label, color = AresTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TechnicalDiagnosticsPanel(snapshot: DashboardMissionSnapshot) {
    Surface(
        color = AresBackground,
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("TECHNICAL DIAGNOSTICS & NT4 TELEMETRY SPECIFICATION", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DiagnosticItem("Target Host", "${snapshot.hostIp}:5810")
                DiagnosticItem("Log Server", "${snapshot.hostIp}:5002")
                DiagnosticItem("Telemetry Rate", if (snapshot.frameRateHz > 0.0) String.format("%.1f Hz", snapshot.frameRateHz) else "--")
                DiagnosticItem("Control Loop", snapshot.loopTimeMs?.let { String.format("%.1f ms (%.0f Hz)", it, 1000.0 / it) } ?: "--")
                DiagnosticItem("Battery Voltage", snapshot.batteryVoltage?.let { String.format("%.2f V", it) } ?: "--")
                DiagnosticItem("Active Alerts", "${snapshot.activeAlerts.size}")
            }

            HorizontalDivider(color = AresBorder.copy(alpha = 0.5f))

            Text(
                "Data classifications: [SIM TRUTH] is deterministic physics simulation; [HARDWARE] represents raw device sensors; [ESTIMATED] is EKF fused state; [REPLAY] is historical DuckDB playback.",
                color = AresTextTertiary,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String) {
    Column {
        Text(label, color = AresTextTertiary, fontSize = 10.sp)
        Text(value, color = AresTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}
