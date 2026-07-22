package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
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
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun PowerDistributionCard(
    nt4ClientService: Nt4ClientService,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    // Map of channel name to current in amps
    val currentDraws = remember { mutableStateMapOf<String, Double>() }
    var totalCurrent by remember { mutableStateOf(0.0) }

    LaunchedEffect(Unit) {
        scope.launch {
            nt4ClientService.telemetryFlow.collect { frame ->
                val key = frame.key
                val lowerKey = key.lowercase()
                val value = frame.value as? Double ?: return@collect
                
                // Typical paths: /robot/hardware/IntakeMotor/CurrentDraw, PDH/Channel1_Current
                if (lowerKey.contains("current") && !lowerKey.contains("target")) {
                    val channelName = key.substringAfterLast("/").replace("Current", "").replace("_", "").takeIf { it.isNotEmpty() } ?: key.substringBeforeLast("/").substringAfterLast("/")
                    currentDraws[channelName] = value
                    
                    // Estimate total current if a specific total key isn't provided
                    if (lowerKey.contains("totalcurrent") || lowerKey.contains("total_current")) {
                        totalCurrent = value
                    } else {
                        // Very rough estimate: sum all currents. This might double count if there's a total, so we only sum if we haven't seen a total key.
                        if (!currentDraws.keys.any { it.lowercase().contains("total") }) {
                            totalCurrent = currentDraws.values.sum()
                        }
                    }
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = "Power Distribution",
                        tint = AresGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Power Distribution", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    text = String.format("%.1f A Total", totalCurrent),
                    color = if (totalCurrent > 120.0) AresError else AresGold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AresBorder)

            if (currentDraws.filterKeys { !it.lowercase().contains("total") }.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No current draw telemetry detected.", color = AresTextTertiary, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    currentDraws.filterKeys { !it.lowercase().contains("total") }.toList().sortedByDescending { it.second }.take(6).forEach { (channel, amps) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = channel.take(12),
                                color = AresTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.width(80.dp),
                                maxLines = 1
                            )
                            
                            // Visual bar
                            Box(modifier = Modifier.weight(1f).height(12.dp).background(AresSurface, RoundedCornerShape(4.dp))) {
                                val fraction = (amps / 40.0).coerceIn(0.0, 1.0).toFloat()
                                val barColor = when {
                                    amps > 30.0 -> AresError
                                    amps > 20.0 -> AresGold
                                    else -> AresCyan
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .background(barColor, RoundedCornerShape(4.dp))
                                )
                            }
                            
                            Text(
                                text = String.format("%5.1f A", amps),
                                color = AresTextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(60.dp).padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
