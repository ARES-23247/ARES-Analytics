package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.DatabaseService
import com.ares.analytics.shared.TelemetryFrame
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.launch
import com.ares.analytics.ui.components.core.*

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun BatteryHealthCard(
    databaseService: DatabaseService,
    sessionId: String?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var voltageFrames by remember { mutableStateOf<List<TelemetryFrame>>(emptyList()) }

    LaunchedEffect(sessionId) {
        if (sessionId != null) {
            while (isActive) {
                val allTelemetry = databaseService.getTelemetryRange(sessionId, 0L, Long.MAX_VALUE)
                voltageFrames = allTelemetry.filter { 
                    val lower = it.key.lowercase()
                    lower.contains("voltage") || lower.contains("battery")
                }
                if (sessionId != "live-telemetry") break
                delay(1000)
            }
        } else {
            voltageFrames = emptyList()
        }
    }
    val latestVoltage = voltageFrames.lastOrNull()?.value ?: 12.0
    val minVoltage = voltageFrames.minOfOrNull { it.value } ?: 12.0
    val statusColor = when {
        latestVoltage < 11.5 -> AresError
        latestVoltage < 12.5 -> AresAmber
        else -> AresCyan
    }
    val statusText = when {
        latestVoltage < 11.5 -> "CRITICAL BROWNOUT RISK"
        latestVoltage < 12.5 -> "Warning: Voltage Dropping"
        else -> "Healthy State"
    }

    AnalyticsCard(
        modifier = modifier
    ) {
        CardHeader(
            title = "Battery Diagnostics",
            icon = if (latestVoltage < 11.5) Icons.Default.BatteryAlert else Icons.Default.BatteryChargingFull,
            iconTint = statusColor
        )



        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("LATEST VOLTAGE", fontSize = 10.sp, color = AresTextTertiary)
                Text(
                    String.format("%.2f V", latestVoltage),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = statusColor
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("MINIMUM LOGGED", fontSize = 10.sp, color = AresTextTertiary)
                Text(
                    String.format("%.2f V", minVoltage),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AresTextSecondary
                )
            }
        }

        StatusBadge(
            text = statusText,
            color = statusColor
        )

        if (voltageFrames.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No battery voltage logged for this session.",
                    color = AresTextTertiary,
                    fontSize = 11.sp
                )
            }
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                BatteryVoltageChart(frames = voltageFrames.sortedBy { it.timestampMs }, statusColor = statusColor)
            }
        }
    }
}

@Composable
private fun BatteryVoltageChart(frames: List<TelemetryFrame>, statusColor: Color) {
    if (frames.size < 2) return
    val minTime = frames.first().timestampMs.toDouble()
    val maxTime = frames.last().timestampMs.toDouble()
    val timeRange = (maxTime - minTime).coerceAtLeast(1.0)
    val minVal = (frames.minOf { it.value } - 0.5).coerceAtLeast(0.0)
    val maxVal = (frames.maxOf { it.value } + 0.5).coerceAtLeast(14.0)
    val valRange = (maxVal - minVal).coerceAtLeast(1.0)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
            .background(AresBackground)
    ) {
        val w = size.width
        val h = size.height

        // Draw horizontal grid lines
        val linesCount = 4
        for (i in 1 until linesCount) {
            val y = h * (i.toFloat() / linesCount)
            drawLine(color = AresBorder, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
        }
        val splinePath = Path()
        val fillPath = Path()
        val firstFrame = frames.first()
        val firstX = ((firstFrame.timestampMs - minTime) / timeRange * w).toFloat()
        val firstY = (h - ((firstFrame.value - minVal) / valRange * h)).toFloat().coerceIn(0f, h)

        splinePath.moveTo(firstX, firstY)
        fillPath.moveTo(firstX, h)
        fillPath.lineTo(firstX, firstY)

        for (i in 1 until frames.size) {
            val f = frames[i]
            val x = ((f.timestampMs - minTime) / timeRange * w).toFloat()
            val y = (h - ((f.value - minVal) / valRange * h)).toFloat().coerceIn(0f, h)
            splinePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }

        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(statusColor.copy(alpha = 0.2f), Color.Transparent),
                startY = 0f,
                endY = h
            )
        )

        drawPath(
            path = splinePath,
            color = statusColor,
            style = Stroke(width = 2f)
        )
    }
}

