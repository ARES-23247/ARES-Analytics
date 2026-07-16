package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
fun PoseViewerCard(
    nt4ClientService: Nt4ClientService,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var trueX by remember { mutableStateOf<Double?>(null) }
    var trueY by remember { mutableStateOf<Double?>(null) }
    var trueHeading by remember { mutableStateOf<Double?>(null) }

    var ekfX by remember { mutableStateOf<Double?>(null) }
    var ekfY by remember { mutableStateOf<Double?>(null) }
    var ekfHeading by remember { mutableStateOf<Double?>(null) }

    var pinpointX by remember { mutableStateOf<Double?>(null) }
    var pinpointY by remember { mutableStateOf<Double?>(null) }
    var pinpointHeading by remember { mutableStateOf<Double?>(null) }

    var visionX by remember { mutableStateOf<Double?>(null) }
    var visionY by remember { mutableStateOf<Double?>(null) }
    var visionHeading by remember { mutableStateOf<Double?>(null) }

    var lastUpdateMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            nt4ClientService.telemetryFlow.collect { frame ->
                val key = frame.key
                val value = frame.value
                lastUpdateMs = System.currentTimeMillis()
                when (key) {
                    "ARES/EstimatedPose/0", "Drive/Pose_X" -> {
                        trueX = value
                        if (key == "Drive/Pose_X") ekfX = value
                    }
                    "ARES/EstimatedPose/1", "Drive/Pose_Y" -> {
                        trueY = value
                        if (key == "Drive/Pose_Y") ekfY = value
                    }
                    "ARES/EstimatedPose/2", "Drive/Pose_Heading", "Drive/Drive_Heading" -> {
                        trueHeading = value
                        if (key != "ARES/EstimatedPose/2") ekfHeading = value
                    }

                    "Drive/Odom_X", "pinpoint_x", "pinpoint/x" -> pinpointX = value
                    "Drive/Odom_Y", "pinpoint_y", "pinpoint/y" -> pinpointY = value
                    "Drive/Odom_Heading", "pinpoint_heading", "pinpoint/heading" -> pinpointHeading = value

                    "Vision/Pose_X", "Vision/Pose/X" -> visionX = value
                    "Vision/Pose_Y", "Vision/Pose/Y" -> visionY = value
                    "Vision/Pose_Heading", "Vision/Pose/Heading" -> visionHeading = value
                }
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
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = AresCyan
                )
                Text(
                    "Robot Pose Telemetry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AresTextPrimary
                )
            }
            
            // Connection/Update indicator
            val elapsed = lastUpdateMs?.let { System.currentTimeMillis() - it }
            val (statusText, statusColor) = when {
                elapsed == null -> "No Data" to AresTextTertiary
                elapsed < 500 -> "Active" to AresGreen
                elapsed < 2000 -> "Stale" to AresAmber
                else -> "Offline" to AresError
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = AresBorder, thickness = 1.dp)

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            PoseRow("True (Actual)", trueX, trueY, trueHeading, AresCyan)
            HorizontalDivider(color = AresBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            PoseRow("Estimated (EKF)", ekfX, ekfY, ekfHeading, AresAmber)
            HorizontalDivider(color = AresBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            PoseRow("Pinpoint (Odom)", pinpointX, pinpointY, pinpointHeading, AresGreen)
            HorizontalDivider(color = AresBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            PoseRow("Vision (Limelight)", visionX, visionY, visionHeading, AresGold)
        }
    }
}

@Composable
private fun PoseRow(
    title: String,
    x: Double?,
    y: Double?,
    heading: Double?,
    color: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AresTextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PoseValueCard("X", x, "m", color, Modifier.weight(1f))
            PoseValueCard("Y", y, "m", color, Modifier.weight(1f))
            PoseValueCard("Heading", heading?.let { Math.toDegrees(it) }, "°", color, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PoseValueCard(
    label: String,
    value: Double?,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AresSurfaceElevated)
            .border(0.5.dp, AresBorder, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = AresTextTertiary,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value?.let { String.format("%.3f", it) } ?: "---",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (value != null) color else AresTextTertiary,
                    fontFamily = FontFamily.Monospace
                )
                if (value != null) {
                    Text(
                        text = unit,
                        fontSize = 9.sp,
                        color = AresTextTertiary,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

