package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.service.DashboardHealthService
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.ares.analytics.ui.components.core.*

@Composable
fun SystemHealthCard(
    nt4ClientService: Nt4ClientService,
    dashboardHealthService: DashboardHealthService? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var loopTimeMs by remember { mutableStateOf<Double?>(null) }
    var batteryVoltage by remember { mutableStateOf<Double?>(null) }
    var brownoutCount by remember { mutableStateOf<Int?>(null) }
    var loopOverruns by remember { mutableStateOf<Int?>(null) }
    val runtimeHealth = dashboardHealthService?.health?.collectAsState()?.value
    val connected by nt4ClientService.isConnected.collectAsState()

    LaunchedEffect(Unit) {
        scope.launch {
            nt4ClientService.uiTelemetryFlow.collect { frame ->
                val key = frame.key.lowercase()
                val value = frame.value

                when {
                    key.contains("looptime") || key.contains("loop_time") -> {
                        loopTimeMs = value
                    }
                    key.contains("batteryvoltage") || key.contains("battery_voltage") -> {
                        batteryVoltage = value
                    }
                    key.contains("brownoutcount") || key.contains("brownout_count") -> {
                        brownoutCount = value.toInt()
                    }
                    key.contains("loopoverruns") || key.contains("loop_overruns") -> {
                        loopOverruns = value.toInt()
                    }
                }
            }
        }
    }

    AnalyticsCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = AresSurfaceElevated
    ) {
        CardHeader(
            title = "RoboRIO / Control Hub Health",
            icon = Icons.Default.Memory,
            iconTint = if (connected) AresGreen else AresTextTertiary,
            statusText = if (connected) "LIVE" else "OFFLINE",
            statusColor = if (connected) AresGreen else AresTextTertiary
        )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Loop Time
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LOOP TIME", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val hz = loopTimeMs?.takeIf { it > 0.0 }?.let { 1000.0 / it } ?: 0.0
                    val loopColor = when {
                        hz < 35.0 -> AresError
                        hz < 45.0 -> AresGold
                        else -> AresGreen
                    }
                    Text(
                        text = loopTimeMs?.let { String.format("%.1f ms", it) } ?: "--",
                        color = loopColor,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (loopTimeMs != null) String.format("(%.0f Hz)", hz) else "",
                        color = loopColor,
                        fontSize = 12.sp
                    )
                }

                // Overruns
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("OVERRUNS", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val overrunVal = loopOverruns ?: 0
                    val overrunColor = if (overrunVal > 0) AresGold else AresGreen
                    Text(
                        text = overrunVal.toString(),
                        color = overrunColor,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Battery Voltage
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BATTERY", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val voltage = batteryVoltage
                    val batteryColor = when {
                        voltage == null -> AresTextPrimary
                        voltage < 11.5 -> AresError
                        voltage < 12.2 -> AresGold
                        else -> AresGreen
                    }
                    Text(
                        text = voltage?.let { String.format("%.2f V", it) } ?: "--",
                        color = batteryColor,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Brownouts
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BROWNOUTS", color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val brownoutVal = brownoutCount ?: 0
                    val brownoutColor = if (brownoutVal > 0) AresError else AresTextPrimary
                    Text(
                        text = brownoutVal.toString(),
                        color = brownoutColor,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (runtimeHealth != null) {
                HorizontalDivider(color = AresBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RuntimeMetric("INGEST", "%.0f fps".format(runtimeHealth.ingestFramesPerSecond))
                    RuntimeMetric("DB P95", "%.1f ms".format(runtimeHealth.databaseP95Ms))
                    RuntimeMetric("CACHE HIT", "%.0f%%".format(runtimeHealth.replayCacheHitRatio * 100.0))
                    RuntimeMetric("RECONNECTS", runtimeHealth.reconnects.toString())
                    RuntimeMetric("DROPS", runtimeHealth.droppedFrames.toString())
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RuntimeMetric("LOG PROFILE", runtimeHealth.robotLogProfile)
                    RuntimeMetric("LOG FILE", formatRuntimeBytes(runtimeHealth.robotLogCurrentFileBytes))
                    RuntimeMetric("LOG RATE", "${formatRuntimeBytes(runtimeHealth.robotLogBytesPerSecond.toLong())}/s")
                    RuntimeMetric("LOG QUEUE", runtimeHealth.robotLogQueueDepth.toString())
                    RuntimeMetric("LOG DROPS", runtimeHealth.robotLogDroppedFrames.toString())
                    RuntimeMetric("LOG PRUNED", runtimeHealth.robotLogPrunedFiles.toString())
                }
            }
        }
    }

@Composable
private fun RuntimeMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = AresTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = AresCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun formatRuntimeBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}

