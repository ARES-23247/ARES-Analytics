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

@Composable
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
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = "Brownout Protection",
                        tint = stateColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Brownout Protection", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                }

                Surface(
                    color = stateColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = brownoutState,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = AresBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Power Scale", color = AresTextSecondary)
                Text("${(powerScale * 100).toInt()}%", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            }
            
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
}
