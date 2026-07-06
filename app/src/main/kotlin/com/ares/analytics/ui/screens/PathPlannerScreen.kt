package com.ares.analytics.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ares.analytics.shared.*
import com.ares.analytics.ui.components.pathplanner.*
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.PathPlannerIntent
import com.ares.analytics.viewmodel.PathPlannerViewModel

@Composable
fun PathPlannerScreen(
    viewModel: PathPlannerViewModel,
    league: League,
    projectPath: String? = null
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.pathName, projectPath) {
        viewModel.onIntent(PathPlannerIntent.LoadPath(projectPath, league))
    }

    LaunchedEffect(projectPath, state.saveStatus) {
        viewModel.onIntent(PathPlannerIntent.FetchAvailablePaths(projectPath, league))
    }

    val playbackPose = remember(state.trajectory, state.playbackTime) {
        val traj = state.trajectory
        if (traj != null && traj.states.isNotEmpty()) {
            val idx = traj.states.indexOfFirst { it.timeSeconds >= state.playbackTime }
            val stateAtTime = if (idx == -1) traj.states.last() else traj.states[idx]
            Waypoint(stateAtTime.x, stateAtTime.y, stateAtTime.headingRad)
        } else {
            null
        }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WaypointEditorPanel(
            state = state,
            projectPath = projectPath,
            league = league,
            onIntent = { viewModel.onIntent(it) }
        )

        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, AresBorder, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
        ) {
            FieldCanvas(
                league = league,
                waypoints = state.waypoints,
                actualPath = emptyList(),
                onWaypointsChanged = {
                    viewModel.onIntent(PathPlannerIntent.UpdateWaypoints(it))
                },
                projectPath = projectPath,
                showPathControls = false,
                showObstacleControls = false,
                playbackPose = playbackPose,
                aprilTags = null,
                onAprilTagsChanged = null,
                eventMarkers = state.eventMarkers,
                onEventMarkersChanged = {
                    viewModel.onIntent(PathPlannerIntent.UpdateEventMarkers(it))
                },
                initialViewRotation = state.viewRotation,
                onViewRotationChanged = { newRot ->
                    viewModel.onIntent(PathPlannerIntent.UpdateViewRotation(newRot))
                },
                rotationTargets = state.rotationTargets,
                onRotationTargetsChanged = {
                    viewModel.onIntent(PathPlannerIntent.UpdateRotationTargets(it))
                },
                constraintZones = state.constraintZones,
                pointTowardsZones = state.pointTowardsZones,
                globalConstraints = state.globalConstraints
            )
        }
    }
}
