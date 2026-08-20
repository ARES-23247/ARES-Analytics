package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.Nt4ClientService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.merge
import com.ares.analytics.ui.components.core.CardHeader
import com.ares.analytics.ui.components.core.GlassCard
import com.ares.analytics.ui.components.core.MetricValueBadge
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PoseViewerCard(
    nt4ClientService: Nt4ClientService,
    modifier: Modifier = Modifier
) {
    // Simulator ground truth uses ARES/EstimatedPose; the robot EKF uses Drive/Pose_*.
    val trueXSimFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("ARES/EstimatedPose/0") }
    val trueXSim by trueXSimFlow.collectAsState(initial = null)

    val trueYSimFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("ARES/EstimatedPose/1") }
    val trueYSim by trueYSimFlow.collectAsState(initial = null)

    val trueHeadingSimFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("ARES/EstimatedPose/2") }
    val trueHeadingSim by trueHeadingSimFlow.collectAsState(initial = null)

    val ekxXFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("Drive/Pose_X") }
    val ekfX by ekxXFlow.collectAsState(initial = null)

    val ekfYFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("Drive/Pose_Y") }
    val ekfY by ekfYFlow.collectAsState(initial = null)

    val ekfHeadingFlow = remember<Flow<Double>>(nt4ClientService) {
        merge(
            nt4ClientService.subscribeDouble("Drive/Pose_Heading"),
            nt4ClientService.subscribeDouble("Drive/Drive_Heading")
        )
    }
    val ekfHeading by ekfHeadingFlow.collectAsState(initial = null)

    val trueX = trueXSim
    val trueY = trueYSim
    val trueHeading = trueHeadingSim

    val pinpointXFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("Drive/Odom_X") }
    val pinpointX by pinpointXFlow.collectAsState(initial = null)

    val pinpointYFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("Drive/Odom_Y") }
    val pinpointY by pinpointYFlow.collectAsState(initial = null)

    val pinpointHeadingFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("Drive/Odom_Heading") }
    val pinpointHeading by pinpointHeadingFlow.collectAsState(initial = null)

    val visionXFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("Vision/Pose_X") }
    val visionX by visionXFlow.collectAsState(initial = null)

    val visionYFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("Vision/Pose_Y") }
    val visionY by visionYFlow.collectAsState(initial = null)

    val visionHeadingFlow = remember<Flow<Double>>(nt4ClientService) { nt4ClientService.subscribeDouble("Vision/Pose_Heading") }
    val visionHeading by visionHeadingFlow.collectAsState(initial = null)
    var lastUpdateMs by remember { mutableStateOf<Long?>(null) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(nt4ClientService) {
        nt4ClientService.uiTelemetryFlow.collect { frame ->
            // Refresh on every received sample, even when a stationary robot repeatedly
            // publishes the same numeric value and Compose suppresses equal state writes.
            if (frame.key in POSE_STATUS_TOPICS) {
                lastUpdateMs = System.currentTimeMillis()
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(250)
        }
    }

    val elapsed = lastUpdateMs?.let { nowMs - it }
    val (statusText, statusColor) = when {
        elapsed == null -> "No Data" to AresTextTertiary
        elapsed < 500 -> "Active" to AresGreen
        elapsed < 2000 -> "Stale" to AresAmber
        else -> "Offline" to AresError
    }

    GlassCard(
        modifier = modifier
    ) {
        CardHeader(
            title = "Robot Pose Telemetry",
            icon = Icons.Default.MyLocation,
            iconTint = AresCyan,
            statusText = statusText,
            statusColor = statusColor
        )

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

private val POSE_STATUS_TOPICS = setOf(
    "ARES/EstimatedPose/0",
    "ARES/EstimatedPose/1",
    "ARES/EstimatedPose/2",
    "Drive/Pose_X",
    "Drive/Pose_Y",
    "Drive/Pose_Heading",
    "Drive/Drive_Heading",
    "Drive/Odom_X",
    "Drive/Odom_Y",
    "Drive/Odom_Heading",
    "Vision/Pose_X",
    "Vision/Pose_Y",
    "Vision/Pose_Heading"
)

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
            MetricValueBadge(
                label = "X",
                value = x?.let { String.format("%.3f", it) } ?: "---",
                unit = "m",
                statusColor = if (x != null) color else AresTextTertiary,
                modifier = Modifier.weight(1f)
            )
            MetricValueBadge(
                label = "Y",
                value = y?.let { String.format("%.3f", it) } ?: "---",
                unit = "m",
                statusColor = if (y != null) color else AresTextTertiary,
                modifier = Modifier.weight(1f)
            )
            MetricValueBadge(
                label = "Heading",
                value = heading?.let { String.format("%.3f", Math.toDegrees(it)) } ?: "---",
                unit = "°",
                statusColor = if (heading != null) color else AresTextTertiary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
