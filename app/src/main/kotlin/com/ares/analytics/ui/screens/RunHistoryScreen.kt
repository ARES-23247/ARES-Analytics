package com.ares.analytics.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.ui.graphics.SolidColor
import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.SyncEngineService
import com.ares.analytics.shared.Session
import com.ares.analytics.shared.SessionSummary
import com.ares.analytics.shared.TelemetryFrame
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Defines a bottom border using drawBehind.
 */
fun Modifier.bottomBorder(width: Dp, color: Color) = drawBehind {
    val strokeWidth = width.toPx()
    val y = size.height - strokeWidth / 2
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = strokeWidth
    )
}

/**
 * Defines an end border using drawBehind.
 */
fun Modifier.endBorder(width: Dp, color: Color) = drawBehind {
    val strokeWidth = width.toPx()
    val x = size.width - strokeWidth / 2
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = strokeWidth
    )
}

/**
 * Defines a row metric inside the Run History Spreadsheet.
 */
data class RowDefinition(
    val label: String,
    val category: String,
    val getValue: (Session, SessionSummary?, Map<String, Double>) -> String,
    val getNumericValue: (Session, SessionSummary?, Map<String, Double>) -> Double? = { _, _, _ -> null },
    val isAnomaly: (Double) -> Boolean = { false }
)

@Composable
fun RunHistoryScreen(
    databaseService: DatabaseService,
    syncEngineService: SyncEngineService
) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    var summaries by remember { mutableStateOf<Map<String, SessionSummary>>(emptyMap()) }
    var diagnosticsMap by remember { mutableStateOf<Map<String, Map<String, Double>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    var isAiAnalystOpen by remember { mutableStateOf(false) }
    val analystChatHistory = remember { mutableStateListOf<Pair<String, String>>() }
    var userQueryText by remember { mutableStateOf("") }
    var isAnalystLoading by remember { mutableStateOf(false) }

    var selectedRowForGraph by remember { mutableStateOf<RowDefinition?>(null) }

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.US).withZone(ZoneId.systemDefault())
    }

    // Load data from DuckDB
    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val list = databaseService.getSessions().sortedBy { it.createdAt }
            val sums = list.associate { it.sessionId to databaseService.getSessionSummary(it.sessionId) }
                .filterValues { it != null }
                .mapValues { it.value!! }
            val diags = list.associate { session ->
                session.sessionId to databaseService.getDiagnosticsTelemetry(session.sessionId).associate { it.key to it.value }
            }
            sessions = list
            summaries = sums
            diagnosticsMap = diags
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AresCyan)
        }
        return
    }

    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No runs recorded yet. Upload a log or run a routine to begin.", color = AresTextSecondary)
        }
        return
    }

    // Dynamic Motor Subsystem rows discovery
    val motorNames = remember(diagnosticsMap) {
        diagnosticsMap.values.flatMap { it.keys }
            .filter { it.startsWith("Diagnostics/SysId/Motors/") }
            .map { it.split("/")[3] }
            .map { canonicalizeMotorName(it) }
            .distinct()
            .sorted()
    }

    val allMotorNames = remember(motorNames, summaries) {
        val currentMotors = summaries.values.flatMap { it.motorCurrentAverages.keys }
            .map { canonicalizeMotorName(it) }
        (motorNames + currentMotors).distinct().sorted()
    }

    // Core spreadsheet row definitions
    val baseRowDefinitions = remember {
        listOf(
            RowDefinition("Match Number", "Session Info", { session, _, _ -> session.matchNumber?.toString() ?: "N/A" }),
            RowDefinition("Alliance", "Session Info", { session, _, _ -> session.allianceColor ?: "N/A" }),
            RowDefinition("Tags", "Session Info", { session, _, _ -> session.tags.joinToString(", ") }),
            RowDefinition("Duration (s)", "Session Info", { _, summary, _ -> summary?.let { String.format("%.1fs", it.durationMs / 1000.0) } ?: "N/A" }, { _, summary, _ -> summary?.durationMs?.toDouble()?.div(1000.0) }),
            
            // Health
            RowDefinition("Min Battery Voltage (V)", "System Health", { _, summary, _ -> summary?.let { String.format("%.2fV", it.minBatteryVoltage) } ?: "N/A" }, { _, summary, _ -> summary?.minBatteryVoltage }, { it < 9.5 }),
            RowDefinition("Battery Resistance (Ω)", "System Health", { _, summary, _ -> summary?.let { String.format("%.3f Ω", it.avgBatteryResistance) } ?: "N/A" }, { _, summary, _ -> summary?.avgBatteryResistance }, { it > 0.15 }),
            RowDefinition("Avg Loop Time (ms)", "System Health", { _, summary, _ -> summary?.let { String.format("%.2f ms", it.avgLoopTimeMs) } ?: "N/A" }, { _, summary, _ -> summary?.avgLoopTimeMs }, { it > 15.0 }),
            RowDefinition("P95 Loop Time (ms)", "System Health", { _, summary, _ -> summary?.let { String.format("%.2f ms", it.p95LoopTimeMs) } ?: "N/A" }, { _, summary, _ -> summary?.p95LoopTimeMs }, { it > 25.0 }),
            RowDefinition("Traction Loss (%)", "System Health", { _, _, diag -> diag["Diagnostics/Drive/TractionLoss"]?.let { String.format("%.1f%%", it * 100.0) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/Drive/TractionLoss"] }, { it > 0.20 }),
            RowDefinition("Comms Loss Count", "System Health", { _, _, diag -> diag["Diagnostics/System/CommsLosses"]?.toInt()?.toString() ?: "N/A" }, { _, _, diag -> diag["Diagnostics/System/CommsLosses"] }, { it > 0.0 }),
            RowDefinition("Loop Overrun Count", "System Health", { _, _, diag -> diag["Diagnostics/System/LoopOverruns"]?.toInt()?.toString() ?: "N/A" }, { _, _, diag -> diag["Diagnostics/System/LoopOverruns"] }, { it > 5.0 }),
            RowDefinition("Max CANbus Util (%)", "System Health", { _, _, diag -> diag["Diagnostics/System/MaxCANBusUtilization"]?.let { String.format("%.1f%%", it * 100.0) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/System/MaxCANBusUtilization"] }, { it > 0.90 }),
            RowDefinition("Total CANbus Errors", "System Health", { _, _, diag -> diag["Diagnostics/System/TotalCANBusErrors"]?.toInt()?.toString() ?: "N/A" }, { _, _, diag -> diag["Diagnostics/System/TotalCANBusErrors"] }, { it > 0.0 }),
            RowDefinition("CANbus Offs", "System Health", { _, _, diag -> diag["Diagnostics/System/CANBusOffs"]?.toInt()?.toString() ?: "N/A" }, { _, _, diag -> diag["Diagnostics/System/CANBusOffs"] }, { it > 0.0 }),
            RowDefinition("Max CANbus Latency (ms)", "System Health", { _, _, diag -> diag["Diagnostics/System/MaxCANBusLatencyMs"]?.let { String.format("%.1f ms", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/System/MaxCANBusLatencyMs"] }, { it > 20.0 }),
            RowDefinition("Power Brownouts", "System Health", { _, _, diag -> diag["Diagnostics/System/BrownoutCount"]?.toInt()?.toString() ?: "N/A" }, { _, _, diag -> diag["Diagnostics/System/BrownoutCount"] }, { it > 0.0 }),

            // Vision
            RowDefinition("Max EKF Drift (m)", "Vision & Localization", { _, summary, _ -> summary?.let { String.format("%.3fm", it.maxEkfDrift) } ?: "N/A" }, { _, summary, _ -> summary?.maxEkfDrift }, { it > 0.10 }),
            RowDefinition("Avg Cross-Track Error (m)", "Vision & Localization", { _, summary, _ -> summary?.let { String.format("%.3fm", it.avgCrossTrackError) } ?: "N/A" }, { _, summary, _ -> summary?.avgCrossTrackError }, { it > 0.10 }),
            RowDefinition("Vision Latency (ms)", "Vision & Localization", { _, summary, _ -> summary?.let { String.format("%.1f ms", it.avgVisionLatencyMs) } ?: "N/A" }, { _, summary, _ -> summary?.avgVisionLatencyMs }, { it > 100.0 }),
            RowDefinition("Vision Acceptance (%)", "Vision & Localization", { _, summary, _ -> summary?.let { String.format("%.1f%%", it.visionAcceptanceRate * 100.0) } ?: "N/A" }, { _, summary, _ -> summary?.visionAcceptanceRate }, { it < 0.60 }),

            // Linear SysId
            RowDefinition("Linear kS (V)", "Drivetrain SysId (Linear)", { _, _, diag -> diag["Diagnostics/SysId/kS"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/kS"] }),
            RowDefinition("Linear kV (V/m/s)", "Drivetrain SysId (Linear)", { _, _, diag -> diag["Diagnostics/SysId/kV"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/kV"] }),
            RowDefinition("Linear kA (V/m/s²)", "Drivetrain SysId (Linear)", { _, _, diag -> diag["Diagnostics/SysId/kA"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/kA"] }),
            RowDefinition("Linear R²", "Drivetrain SysId (Linear)", { _, _, diag -> diag["Diagnostics/SysId/R2"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/R2"] }, { it < 0.70 }),
            RowDefinition("Linear ADRC b0", "Drivetrain SysId (Linear)", { _, _, diag -> diag["Diagnostics/SysId/ADRC_b0"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/ADRC_b0"] }),

            // Angular SysId
            RowDefinition("Angular kS (V)", "Drivetrain SysId (Angular)", { _, _, diag -> diag["Diagnostics/SysId/Angular/kS"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/Angular/kS"] }),
            RowDefinition("Angular kV (V/rad/s)", "Drivetrain SysId (Angular)", { _, _, diag -> diag["Diagnostics/SysId/Angular/kV"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/Angular/kV"] }),
            RowDefinition("Angular kA (V/rad/s²)", "Drivetrain SysId (Angular)", { _, _, diag -> diag["Diagnostics/SysId/Angular/kA"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/Angular/kA"] }),
            RowDefinition("Angular R²", "Drivetrain SysId (Angular)", { _, _, diag -> diag["Diagnostics/SysId/Angular/R2"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/Angular/R2"] }, { it < 0.70 }),
            RowDefinition("Angular ADRC b0", "Drivetrain SysId (Angular)", { _, _, diag -> diag["Diagnostics/SysId/Angular/ADRC_b0"]?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/SysId/Angular/ADRC_b0"] }),

            // Driver Jitter
            RowDefinition("Driver Rec. Exponent", "Driver Profiles", { _, _, diag -> diag["Diagnostics/Driver/RecommendedExponent"]?.let { String.format("%.2f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/Driver/RecommendedExponent"] }),
            RowDefinition("Driver Rec. Slew Rate", "Driver Profiles", { _, _, diag -> diag["Diagnostics/Driver/RecommendedSlewRate"]?.let { if (it >= 999.0) "None" else String.format("%.1f", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/Driver/RecommendedSlewRate"] }),
            RowDefinition("Jitter Present", "Driver Profiles", { _, _, diag -> diag["Diagnostics/Driver/JitterPresent"]?.let { if (it > 0.5) "Yes" else "No" } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/Driver/JitterPresent"] }, { it > 0.5 }),
            RowDefinition("Peak Jitter Freq (Hz)", "Driver Profiles", { _, _, diag -> diag["Diagnostics/Driver/PeakJitterFrequency"]?.let { String.format("%.1f Hz", it) } ?: "N/A" }, { _, _, diag -> diag["Diagnostics/Driver/PeakJitterFrequency"] })
        )
    }

    // Dynamic Motor Current Draw Comparison Rows
    val currentDrawRowDefinitions = remember(allMotorNames) {
        allMotorNames.map { motor ->
            RowDefinition(
                label = "Motor [$motor] Avg Current",
                category = "Motor Current Draw",
                getValue = { _, summary, _ ->
                    getMotorCurrentAverage(summary, motor)?.let { String.format("%.2f A", it) } ?: "N/A"
                },
                getNumericValue = { _, summary, _ ->
                    getMotorCurrentAverage(summary, motor)
                }
            )
        }
    }

    // Dynamic Motor Subsystems Rows definition
    val motorRowDefinitions = remember(allMotorNames) {
        allMotorNames.flatMap { motor ->
            listOf(
                RowDefinition("Motor [$motor] kS", "Subsystem Motors ($motor)", { _, _, diag -> getDiagnosticValue(diag, motor, "kS")?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> getDiagnosticValue(diag, motor, "kS") }),
                RowDefinition("Motor [$motor] kV", "Subsystem Motors ($motor)", { _, _, diag -> getDiagnosticValue(diag, motor, "kV")?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> getDiagnosticValue(diag, motor, "kV") }),
                RowDefinition("Motor [$motor] kA", "Subsystem Motors ($motor)", { _, _, diag -> getDiagnosticValue(diag, motor, "kA")?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> getDiagnosticValue(diag, motor, "kA") }),
                RowDefinition("Motor [$motor] kG (Gravity)", "Subsystem Motors ($motor)", { _, _, diag -> getDiagnosticValue(diag, motor, "kG")?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> getDiagnosticValue(diag, motor, "kG") }),
                RowDefinition("Motor [$motor] ADRC b0", "Subsystem Motors ($motor)", { _, _, diag -> getDiagnosticValue(diag, motor, "ADRC_b0")?.let { String.format("%.3f", it) } ?: "N/A" }, { _, _, diag -> getDiagnosticValue(diag, motor, "ADRC_b0") })
            )
        }
    }

    val allRows = remember(baseRowDefinitions, currentDrawRowDefinitions, motorRowDefinitions) {
        baseRowDefinitions + currentDrawRowDefinitions + motorRowDefinitions
    }

    val groupedRows = remember(allRows) {
        allRows.groupBy { it.category }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Robot Run History Spreadsheet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AresTextPrimary
                    )
                    Text(
                        "Interactive matrix of session telemetry & control constants. Click any row header to chart its values.",
                        fontSize = 13.sp,
                        color = AresTextSecondary
                    )
                }

                Button(
                    onClick = { isAiAnalystOpen = !isAiAnalystOpen },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isAiAnalystOpen) AresCyan.copy(alpha = 0.2f) else AresCyan),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AresCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isAiAnalystOpen) AresCyan else AresBackground,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "AI Run Analyst",
                        color = if (isAiAnalystOpen) AresCyan else AresBackground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

        // Spreadsheet Container
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, AresBorder, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = AresSurface
        ) {
            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberScrollState()

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Row Headers (Sticky)
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(AresSurfaceElevated)
                        .endBorder(1.dp, AresBorder)
                        .verticalScroll(verticalScrollState)
                ) {
                    // Header corner block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .bottomBorder(1.dp, AresBorder)
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Calculated Metrics", fontWeight = FontWeight.Bold, color = AresTextPrimary, fontSize = 13.sp)
                    }

                    // Group Category Labels and Row Labels
                    groupedRows.forEach { (category, rows) ->
                        // Category Header Row
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(Color(0xFF252C3D))
                                .bottomBorder(1.dp, AresBorder)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(category, fontWeight = FontWeight.Bold, color = AresCyan, fontSize = 11.sp)
                        }

                        rows.forEach { rowDef ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .bottomBorder(1.dp, AresBorder)
                                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                                    .clickable { selectedRowForGraph = rowDef }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(rowDef.label, color = AresTextPrimary, fontSize = 12.sp, maxLines = 1)
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                        contentDescription = "Graph row",
                                        tint = AresTextTertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Section: Scrollable Columns (Runs)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        sessions.forEachIndexed { sessionIdx, session ->
                            val summary = summaries[session.sessionId]
                            val diags = diagnosticsMap[session.sessionId] ?: emptyMap()

                            Column(
                                modifier = Modifier
                                    .width(180.dp)
                                    .fillMaxHeight()
                                    .endBorder(1.dp, AresBorder)
                                    .verticalScroll(verticalScrollState)
                            ) {
                                // Run Header block
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .background(if (sessionIdx % 2 == 0) AresSurface else AresSurfaceElevated)
                                        .bottomBorder(1.dp, AresBorder)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = session.matchNumber?.let { "Match #$it" } ?: "Run #${sessionIdx + 1}",
                                            fontWeight = FontWeight.Bold,
                                            color = AresTextPrimary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = dateFormatter.format(Instant.ofEpochMilli(session.createdAt)),
                                            color = AresTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                // Cells grouped by categories
                                groupedRows.forEach { (_, rows) ->
                                    // Empty category cell placeholder for spacing
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp)
                                            .background(Color(0xFF252C3D))
                                            .bottomBorder(1.dp, AresBorder)
                                    )

                                    rows.forEach { rowDef ->
                                        val displayVal = rowDef.getValue(session, summary, diags)
                                        val numVal = rowDef.getNumericValue(session, summary, diags)
                                        val isAnomaly = numVal?.let { rowDef.isAnomaly(it) } ?: false

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .background(
                                                    when {
                                                        isAnomaly -> Color(0x35FF5252)
                                                        sessionIdx % 2 == 0 -> AresSurface
                                                        else -> AresSurfaceElevated
                                                    }
                                                )
                                                .bottomBorder(1.dp, AresBorder)
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = displayVal,
                                                color = if (isAnomaly) AresError else AresTextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isAnomaly) FontWeight.Bold else FontWeight.Normal,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Collapsible AI Analyst side panel!
    AnimatedVisibility(
        visible = isAiAnalystOpen,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(AresSurfaceElevated)
                .border(1.dp, AresBorder)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AresCyan)
                    Text("AI Telemetry SQL Analyst", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                }
                IconButton(
                    onClick = { isAiAnalystOpen = false },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AresTextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(color = AresBorder)

            val listState = rememberLazyListState()
            LaunchedEffect(analystChatHistory.size) {
                if (analystChatHistory.isNotEmpty()) {
                    listState.animateScrollToItem(analystChatHistory.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(AresSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Ask me to generate SQL queries and summarize cross-session telemetry trends (e.g., 'What is our average EKF drift?' or 'List matches where battery fell below 9.5V').",
                        fontSize = 11.sp,
                        color = AresTextSecondary
                    )
                }

                items(analystChatHistory) { (role, msg) ->
                    val isUser = role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .background(
                                    color = if (isUser) AresCyan.copy(alpha = 0.15f) else AresSurfaceElevated,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, if (isUser) AresCyan else AresBorder, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (isUser) "You" else "SQL Analyst",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) AresCyan else AresTextSecondary
                            )
                            Text(text = msg, fontSize = 11.sp, color = AresTextPrimary)
                        }
                    }
                }

                if (isAnalystLoading) {
                    item {
                        CircularProgressIndicator(
                            color = AresCyan,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = userQueryText,
                    onValueChange = { userQueryText = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                    cursorBrush = SolidColor(AresCyan),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(AresSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, AresBorder, RoundedCornerShape(6.dp)),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (userQueryText.isEmpty()) {
                                    Text("Ask database analyst...", fontSize = 11.sp, color = AresTextTertiary)
                                }
                                innerTextField()
                            }
                        }
                    }
                )

                Button(
                    onClick = {
                        if (userQueryText.trim().isNotEmpty() && !isAnalystLoading) {
                            val queryCopy = userQueryText.trim()
                            analystChatHistory.add(Pair("user", queryCopy))
                            userQueryText = ""
                            isAnalystLoading = true
                            scope.launch {
                                try {
                                    val reply = syncEngineService.requestSqlAnalysis(queryCopy, databaseService)
                                    analystChatHistory.add(Pair("analyst", reply))
                                } catch (e: Exception) {
                                    analystChatHistory.add(Pair("analyst", "Error: ${e.message ?: "Failed to analyze query."}"))
                                } finally {
                                    isAnalystLoading = false
                                }
                            }
                        }
                    },
                    enabled = userQueryText.trim().isNotEmpty() && !isAnalystLoading,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = AresBackground, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    }

    // Row Graphing overlay Modal
    selectedRowForGraph?.let { rowDef ->
        RowGraphDialog(
            rowDefinition = rowDef,
            sessions = sessions,
            summaries = summaries,
            diagnosticsMap = diagnosticsMap,
            dateFormatter = dateFormatter,
            onDismiss = { selectedRowForGraph = null }
        )
    }
}

@Composable
fun RowGraphDialog(
    rowDefinition: RowDefinition,
    sessions: List<Session>,
    summaries: Map<String, SessionSummary>,
    diagnosticsMap: Map<String, Map<String, Double>>,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit
) {
    val dataPoints = remember(rowDefinition, sessions, summaries, diagnosticsMap) {
        sessions.mapIndexedNotNull { idx, s ->
            val sum = summaries[s.sessionId]
            val diag = diagnosticsMap[s.sessionId] ?: emptyMap()
            val valNum = rowDefinition.getNumericValue(s, sum, diag)
            if (valNum != null) {
                val label = s.matchNumber?.let { "M#$it" } ?: "Run ${idx + 1}"
                val detail = dateFormatter.format(Instant.ofEpochMilli(s.createdAt))
                Triple(label, valNum, detail)
            } else null
        }
    }

    val textMeasurer = rememberTextMeasurer()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(480.dp).border(1.dp, AresBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = AresSurfaceElevated
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(rowDefinition.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                        Text("Historical trend across runs", fontSize = 12.sp, color = AresTextSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = AresTextPrimary)
                    }
                }

                if (dataPoints.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No numerical data points to plot for this row.", color = AresTextSecondary)
                    }
                } else {
                    // Line Graph View
                    Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val paddingLeft = 60f
                            val paddingBottom = 40f
                            val paddingTop = 20f
                            val paddingRight = 20f

                            val graphW = w - paddingLeft - paddingRight
                            val graphH = h - paddingTop - paddingBottom

                            val yValues = dataPoints.map { it.second }
                            val yMin = yValues.minOrNull() ?: 0.0
                            val yMax = yValues.maxOrNull() ?: 12.0
                            val yRange = if (yMax - yMin < 1e-4) 1.0 else yMax - yMin

                            // 1. Draw Grid axes and labels
                            drawLine(
                                color = AresBorder,
                                start = Offset(paddingLeft, paddingTop),
                                end = Offset(paddingLeft, h - paddingBottom),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = AresBorder,
                                start = Offset(paddingLeft, h - paddingBottom),
                                end = Offset(w - paddingRight, h - paddingBottom),
                                strokeWidth = 2f
                            )

                            // Draw Y-axis labels
                            val divisions = 4
                            val textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val labelStyle = TextStyle(
                                color = Color.Gray,
                                fontSize = 9.sp
                            )

                            for (i in 0..divisions) {
                                val frac = i.toDouble() / divisions
                                val yVal = yMin + frac * yRange
                                val yPos = h - paddingBottom - (frac * graphH).toFloat()

                                drawLine(
                                    color = AresBorder.copy(alpha = 0.3f),
                                    start = Offset(paddingLeft, yPos),
                                    end = Offset(w - paddingRight, yPos),
                                    strokeWidth = 1f
                                )
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = String.format("%.2f", yVal),
                                    style = textStyle,
                                    topLeft = Offset(5f, yPos - 6f)
                                )
                            }

                            // Draw Line and Points
                            val points = dataPoints.mapIndexed { idx, item ->
                                val xFrac = if (dataPoints.size > 1) idx.toFloat() / (dataPoints.size - 1) else 0.5f
                                val yFrac = ((item.second - yMin) / yRange).toFloat()
                                val px = paddingLeft + xFrac * graphW
                                val py = h - paddingBottom - yFrac * graphH
                                Offset(px, py)
                            }

                            if (points.size > 1) {
                                val path = Path().apply {
                                    moveTo(points[0].x, points[0].y)
                                    for (i in 1 until points.size) {
                                        lineTo(points[i].x, points[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = AresCyan,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }

                            // Draw points
                            points.forEachIndexed { idx, pt ->
                                val isAnomaly = rowDefinition.isAnomaly(dataPoints[idx].second)
                                drawCircle(
                                    color = if (isAnomaly) AresError else AresCyan,
                                    radius = 6.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = AresSurfaceElevated,
                                    radius = 3.dp.toPx(),
                                    center = pt
                                )

                                // Label underneath point on x-axis
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = dataPoints[idx].first,
                                    style = labelStyle,
                                    topLeft = Offset(pt.x - 14f, h - 25f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun canonicalizeMotorName(name: String): String {
    return when (name.lowercase()) {
        "bl" -> "rl"
        "br" -> "rr"
        "lf" -> "fl"
        "rf" -> "fr"
        else -> name
    }
}

private fun getDiagnosticValue(diag: Map<String, Double>, canonicalMotor: String, param: String): Double? {
    val namesToCheck = when (canonicalMotor) {
        "rl" -> listOf("rl", "bl")
        "rr" -> listOf("rr", "br")
        "fl" -> listOf("fl", "lf")
        "fr" -> listOf("fr", "rf")
        else -> listOf(canonicalMotor)
    }
    for (name in namesToCheck) {
        val value = diag["Diagnostics/SysId/Motors/$name/$param"]
        if (value != null) return value
    }
    return null
}

private fun getMotorCurrentAverage(summary: com.ares.analytics.shared.SessionSummary?, canonicalMotor: String): Double? {
    if (summary == null) return null
    val namesToCheck = when (canonicalMotor) {
        "rl" -> listOf("rl", "bl")
        "rr" -> listOf("rr", "br")
        "fl" -> listOf("fl", "lf")
        "fr" -> listOf("fr", "rf")
        else -> listOf(canonicalMotor)
    }
    for (name in namesToCheck) {
        val value = summary.motorCurrentAverages[name]
        if (value != null) return value
    }
    return null
}
