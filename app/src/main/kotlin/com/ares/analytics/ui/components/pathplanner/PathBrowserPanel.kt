package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.PathPlannerIntent
import com.ares.analytics.viewmodel.PathPlannerState

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun PathBrowserPanel(
    state: PathPlannerState,
    league: League,
    projectPath: String?,
    onIntent: (PathPlannerIntent) -> Unit
) {
    val items = if (state.activeEditorMode == "Path") state.availablePathPreviews else state.availableAutoPreviews

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).background(AresBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${state.activeEditorMode} Browser",
                color = AresTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { onIntent(PathPlannerIntent.ToggleBrowser) },
                colors = ButtonDefaults.buttonColors(containerColor = AresSurfaceElevated)
            ) {
                Text("Close Browser", color = AresTextPrimary)
            }
        }
        
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { preview ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clickable {
                            onIntent(PathPlannerIntent.UpdatePathName(preview.name))
                            if (state.activeEditorMode == "Path") {
                                onIntent(PathPlannerIntent.LoadPath(projectPath, league))
                            } else {
                                onIntent(PathPlannerIntent.LoadAuto(projectPath, league))
                            }
                            onIntent(PathPlannerIntent.ToggleBrowser)
                        },
                    colors = CardDefaults.cardColors(containerColor = AresSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AresBorder)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AresSurfaceElevated)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = preview.name,
                                color = AresTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, AresBorder, RoundedCornerShape(8.dp))) {
                            // Mini FieldCanvas
                            FieldCanvas(
                                league = league,
                                waypoints = emptyList(), // Don't show bezier controls
                                actualPath = preview.trajectory?.states?.map { Waypoint(it.x, it.y, it.headingRad) } ?: emptyList(),
                                contextPath = null,
                                onWaypointsChanged = {},
                                projectPath = null,
                                showPathControls = false,
                                showObstacleControls = false,
                                playbackPose = null,
                                aprilTags = null,
                                onAprilTagsChanged = null,
                                eventMarkers = emptyList(),
                                onEventMarkersChanged = {},
                                initialViewRotation = 0f,
                                onViewRotationChanged = {},
                                rotationTargets = emptyList(),
                                onRotationTargetsChanged = {},
                                idealStartingState = null,
                                onStartingStateChanged = {},
                                goalEndState = null,
                                onGoalEndStateChanged = {},
                                constraintZones = emptyList(),
                                pointTowardsZones = emptyList(),
                                globalConstraints = state.globalConstraints
                            )
                            // Invisible overlay to swallow pointer events so the user can't interact with the mini-canvas
                            Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Transparent))
                        }
                    }
                }
            }
        }
    }
}
