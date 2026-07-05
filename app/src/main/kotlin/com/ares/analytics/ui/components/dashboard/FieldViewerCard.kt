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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.League
import com.ares.analytics.ui.components.pathplanner.FieldCanvas
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.FieldViewerViewModel

@Composable
fun FieldViewerCard(
    nt4ClientService: Nt4ClientService,
    league: League,
    projectPath: String? = null,
    properties: Map<String, String> = emptyMap(),
    onPropertiesChanged: (Map<String, String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(nt4ClientService) { FieldViewerViewModel(nt4ClientService, scope) }
    val state by viewModel.state.collectAsState()

    val estimatedPose = if (state.ekfX != null && state.ekfY != null && state.ekfHeading != null) {
        Waypoint(state.ekfX!!, state.ekfY!!, state.ekfHeading!!)
    } else null

    val activeVisionPoses = remember(state.visionPoses.size, state.visionX, state.visionY, state.visionHeading) {
        val list = mutableListOf<Waypoint>()
        val maxIndex = state.visionPoses.keys.maxOrNull() ?: -1
        for (i in 0..maxIndex step 3) {
            val vx = state.visionPoses[i]
            val vy = state.visionPoses[i + 1]
            val vh = state.visionPoses[i + 2]
            if (vx != null && vy != null && vh != null) {
                list.add(Waypoint(vx, vy, vh))
            }
        }
        if (list.isEmpty() && state.visionX != null && state.visionY != null && state.visionHeading != null) {
            list.add(Waypoint(state.visionX!!, state.visionY!!, state.visionHeading!!))
        }
        list
    }

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
                    if (state.isConnected) "Connected" else "Offline",
                    color = if (state.isConnected) AresGreen else AresTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                
                val showTracer = properties["show_tracer"]?.toBoolean() ?: false
                IconButton(
                    onClick = { onPropertiesChanged(properties + ("show_tracer" to (!showTracer).toString())) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Toggle Tracer",
                        tint = if (showTracer) AresCyan else AresTextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
                    actualPath = if (properties["show_tracer"]?.toBoolean() == true) state.poseHistory else emptyList(),
                    onWaypointsChanged = {},
                    projectPath = projectPath,
                    estimatedPose = estimatedPose,
                    visionPoses = activeVisionPoses,
                    showPathControls = false,
                    showObstacleControls = false,
                    initialViewRotation = properties["rotation"]?.toFloatOrNull() ?: 0f,
                    onViewRotationChanged = { newRot -> onPropertiesChanged(properties + ("rotation" to newRot.toString())) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
