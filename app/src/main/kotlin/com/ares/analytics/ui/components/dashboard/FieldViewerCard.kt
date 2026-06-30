package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.League
import com.ares.analytics.ui.components.pathplanner.FieldCanvas
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun FieldViewerCard(
    nt4ClientService: Nt4ClientService,
    league: League,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var robotX by remember { mutableStateOf(0.0) }
    var robotY by remember { mutableStateOf(0.0) }
    var robotHeading by remember { mutableStateOf(0.0) }

    val isConnected by nt4ClientService.isConnected.collectAsState()

    LaunchedEffect(Unit) {
        scope.launch {
            nt4ClientService.telemetryFlow.collect { frame ->
                val key = frame.key
                val value = frame.value
                when (key) {
                    "AdvantageKit/RealOutputs/ARES/EstimatedPose/0", "/AdvantageKit/RealOutputs/ARES/EstimatedPose/0" -> robotX = value
                    "AdvantageKit/RealOutputs/ARES/EstimatedPose/1", "/AdvantageKit/RealOutputs/ARES/EstimatedPose/1" -> robotY = value
                    "AdvantageKit/RealOutputs/ARES/EstimatedPose/2", "/AdvantageKit/RealOutputs/ARES/EstimatedPose/2" -> robotHeading = value
                    "Drive/Pose_X", "/Drive/Pose_X" -> robotX = value
                    "Drive/Pose_Y", "/Drive/Pose_Y" -> robotY = value
                    "Drive/Pose_Heading", "/Drive/Pose_Heading" -> robotHeading = value
                }
            }
        }
    }

    val currentPose = listOf(Waypoint(robotX, robotY, robotHeading))

    Card(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(AresSurface)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = AresSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    "Field 2D Live Tracker",
                    color = AresTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    if (isConnected) "Connected" else "Offline",
                    color = if (isConnected) AresGreen else AresTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(color = AresBorder)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                FieldCanvas(
                    league = league,
                    waypoints = emptyList(),
                    actualPath = currentPose,
                    onWaypointsChanged = {},
                    showPathControls = false,
                    showObstacleControls = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
