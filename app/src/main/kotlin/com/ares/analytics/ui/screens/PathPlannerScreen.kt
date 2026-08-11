package com.ares.analytics.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.shared.League
import com.ares.analytics.ui.components.pathplanner.FieldCanvas
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.ui.components.routine.RoutineEditorPanel
import com.ares.analytics.ui.components.core.chooseProjectDirectory
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.viewmodel.PathPlannerIntent
import com.ares.analytics.viewmodel.PathPlannerViewModel
import com.ares.analytics.viewmodel.pathing.RobotDimensions
import com.areslib.routine.RoutineStep

/**
 * Unified, offline-first routine builder.
 *
 * Drive geometry is embedded directly in the routine. Autonomous start metadata appears only when
 * a student explicitly makes the routine a match choice; the same document can otherwise be called
 * from controller bindings or another routine.
 */
@Composable
fun PathPlannerScreen(
    viewModel: PathPlannerViewModel,
    league: League,
    projectPath: String? = null,
    robotDimensions: RobotDimensions = RobotDimensions.defaultFor(league),
    onProjectPathChanged: (String) -> Unit = {},
    onRobotDimensionsChanged: (RobotDimensions) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(projectPath, league) {
        viewModel.onIntent(PathPlannerIntent.FetchAvailablePaths(projectPath, league))
    }
    LaunchedEffect(league, robotDimensions) {
        viewModel.onIntent(PathPlannerIntent.ConfigureAresField(league, robotDimensions))
    }

    val routineWaypoints = remember(state.routine, state.autonomousEntry) {
        buildList {
            state.autonomousEntry?.startingPose?.let { start ->
                add(
                    Waypoint(
                        x = start.xMeters,
                        y = start.yMeters,
                        headingRad = start.headingRadians,
                        rotationDeg = Math.toDegrees(start.headingRadians)
                    )
                )
            }
            addRoutineDriveTargets(state.routine.steps)
        }
    }
    val previewPath = remember(state.trajectory) {
        state.trajectory?.states?.map { Waypoint(it.x, it.y, it.headingRad) }.orEmpty()
    }
    val playbackPose = remember(state.trajectory, state.playbackTime) {
        val trajectory = state.trajectory
        if (trajectory == null || trajectory.states.isEmpty()) {
            null
        } else {
            val sample = trajectory.states.firstOrNull { it.timeSeconds >= state.playbackTime }
                ?: trajectory.states.last()
            Waypoint(sample.x, sample.y, sample.headingRad)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Routine Builder",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AresTextPrimary
                )
                Text(
                    "Build autonomous routines, controller macros, and reusable robot behaviors in one place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AresTextSecondary
                )
            }
            Surface(
                color = AresSurfaceElevated,
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (projectPath == null) "Select a project" else "Offline project catalog · ${league.name}",
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (projectPath == null) AresTextSecondary else AresCyan
                    )
                    androidx.compose.material3.TextButton(
                        onClick = {
                            chooseProjectDirectory(projectPath)?.let { onProjectPathChanged(it.path) }
                        }
                    ) {
                        Text("Change folder")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RoutineEditorPanel(
                state = state,
                projectPath = projectPath,
                league = state.activeLeague,
                onRobotDimensionsChanged = { dimensions ->
                    viewModel.onIntent(PathPlannerIntent.UpdateCanonicalRobotDimensions(projectPath, dimensions))
                    onRobotDimensionsChanged(dimensions)
                },
                onIntent = viewModel::onIntent
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                FieldCanvas(
                    league = state.activeLeague,
                    waypoints = routineWaypoints,
                    actualPath = previewPath,
                    contextPath = null,
                    contextWaypoints = null,
                    onWaypointsChanged = {
                        viewModel.onIntent(PathPlannerIntent.UpdateRoutineFieldWaypoints(it, league))
                    },
                    projectPath = projectPath,
                    showPathControls = false,
                    showObstacleControls = false,
                    playbackPose = playbackPose,
                    aprilTags = null,
                    onAprilTagsChanged = null,
                    eventMarkers = emptyList(),
                    onEventMarkersChanged = {},
                    initialViewRotation = state.viewRotation,
                    onViewRotationChanged = {
                        viewModel.onIntent(PathPlannerIntent.UpdateViewRotation(it))
                    },
                    rotationTargets = emptyList(),
                    onRotationTargetsChanged = {},
                    idealStartingState = null,
                    onStartingStateChanged = {},
                    goalEndState = null,
                    onGoalEndStateChanged = {},
                    constraintZones = emptyList(),
                    pointTowardsZones = emptyList(),
                    globalConstraints = state.globalConstraints,
                    autoGoalMode = true,
                    robotDimensions = state.robotDimensions,
                    showToolbar = false
                )
            }
        }
    }
}

private fun MutableList<Waypoint>.addRoutineDriveTargets(steps: List<RoutineStep>) {
    steps.forEach { step ->
        step.drive?.target?.let { target ->
            add(
                Waypoint(
                    x = target.xMeters,
                    y = target.yMeters,
                    headingRad = target.headingRadians,
                    rotationDeg = Math.toDegrees(target.headingRadians)
                )
            )
        }
        step.deadline?.let { addRoutineDriveTargets(listOf(it)) }
        addRoutineDriveTargets(step.children)
        addRoutineDriveTargets(step.elseChildren)
    }
}
