package com.ares.analytics.ui.components.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.ui.theme.*
import com.ares.analytics.util.IndicatorLightColorMapper

/**
 * Dashboard widget card that displays all registered RGB indicator lights
 * and their current colors in real-time. Each light is shown as a row
 * with a colored circle, the light name, color name, and raw servo position.
 *
 * Subscribes to NT4 topics matching `Superstructure/IndicatorLight/{name}`.
 * Automatically discovers lights as they appear in the telemetry stream.
 */
@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun IndicatorLightsCard(
    nt4ClientService: Nt4ClientService,
    modifier: Modifier = Modifier
) {
    // Track discovered indicator lights — keyed by name, valued by last position
    val lights = remember { mutableStateMapOf<String, Double>() }

    // Collect the telemetry flow to discover indicator light topics
    LaunchedEffect(Unit) {
        nt4ClientService.telemetryFlow.collect { frame ->
            if (frame.key.startsWith("Superstructure/IndicatorLight/")) {
                val lightName = frame.key.substringAfterLast("/")
                lights[lightName] = frame.value
            }
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = AresAmber
                )
                Text(
                    "Indicator Lights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AresTextPrimary
                )
            }

            // Count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AresSurfaceElevated)
                    .border(0.5.dp, AresBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${lights.size} light${if (lights.size != 1) "s" else ""}",
                    fontSize = 11.sp,
                    color = AresTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = AresBorder, thickness = 1.dp)

        if (lights.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "No indicator lights detected",
                        fontSize = 13.sp,
                        color = AresTextTertiary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Waiting for Superstructure/IndicatorLight/* topics…",
                        fontSize = 11.sp,
                        color = AresTextTertiary.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            // Light rows
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((name, position) in lights.toSortedMap()) {
                    IndicatorLightRow(name = name, position = position)
                }
            }
        }
    }
}

@Composable
private fun IndicatorLightRow(
    name: String,
    position: Double
) {
    val displayColor = IndicatorLightColorMapper.positionToColor(position)
    val colorName = IndicatorLightColorMapper.positionToName(position)

    // Smooth color transition animation
    val animatedColor by animateColorAsState(
        targetValue = displayColor,
        animationSpec = tween(durationMillis = 150),
        label = "indicatorColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AresSurfaceElevated)
            .border(0.5.dp, AresBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Glowing color indicator circle
        Box(
            modifier = Modifier
                .size(28.dp)
                .shadow(
                    elevation = if (position > 0.05) 6.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = animatedColor.copy(alpha = 0.5f),
                    spotColor = animatedColor.copy(alpha = 0.8f)
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedColor,
                            animatedColor.copy(alpha = 0.7f)
                        )
                    )
                )
                .border(1.5.dp, animatedColor.copy(alpha = 0.9f), CircleShape)
        )

        // Light name
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AresTextPrimary
            )
            Text(
                text = colorName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = animatedColor
            )
        }

        // Raw position value
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(AresSurface)
                .border(0.5.dp, AresBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = String.format("%.3f", position),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AresTextSecondary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
