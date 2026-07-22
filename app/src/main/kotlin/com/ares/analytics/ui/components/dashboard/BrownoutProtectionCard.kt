package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.ares.analytics.ui.components.core.*

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun BrownoutProtectionCard(
    nt4ClientService: Nt4ClientService,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var powerScale by remember { mutableStateOf(1.0) }
    var stateOfCharge by remember { mutableStateOf(100.0) }
    var brownoutState by remember { mutableStateOf("HEALTHY") }
    var tripCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        scope.launch {
            nt4ClientService.telemetryFlow.collect { frame ->
                val key = frame.key
                when (key) {
                    "Robot/BrownoutPowerScale" -> powerScale = frame.value as? Double ?: 1.0
                    "Robot/StateOfCharge" -> stateOfCharge = frame.value as? Double ?: 100.0
                    "Robot/BrownoutState" -> brownoutState = frame.value as? String ?: "HEALTHY"
                    "Diagnostics/Power/BrownoutCount" -> tripCount = (frame.value as? Double)?.toInt() ?: 0
                }
            }
        }
    }
    val stateColor = when (brownoutState) {
        "HEALTHY" -> AresGreen
        "WARNING" -> AresAmber
        "CRITICAL" -> AresRed
        else -> AresBorder
    }

    AnalyticsCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = AresSurfaceElevated
    ) {
        CardHeader(
            title = "Brownout Protection",
            icon = Icons.Default.BatteryAlert,
            iconTint = stateColor,
            statusText = brownoutState,
            statusColor = stateColor
        )



        MetricRow(
            label = "Power Scale",
            value = "${(powerScale * 100).toInt()}%"
        )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { powerScale.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = stateColor,
                trackColor = AresBorder
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("State of Charge", color = AresTextSecondary)
                    Text(
                        text = "${String.format("%.1f", stateOfCharge)}%",
                        color = AresTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("Brownout Trips", color = AresTextSecondary)
                    Text(
                        text = "$tripCount",
                        color = if (tripCount > 0) AresAmber else AresTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

