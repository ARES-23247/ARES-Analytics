package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.ares.analytics.shared.ConstraintsZone
import com.ares.analytics.shared.PathPlannerEventMarker
import com.ares.analytics.shared.PointTowardsZone
import com.ares.analytics.shared.RotationTarget
import com.ares.analytics.ui.components.pathplanner.cards.*

@Composable
fun WaypointCard(
    idx: Int,
    wp: Waypoint,
    onChanged: (Waypoint) -> Unit,
    onDelete: () -> Unit
) = com.ares.analytics.ui.components.pathplanner.cards.WaypointCard(idx, wp, onChanged, onDelete)

@Composable
fun EventMarkerCard(
    idx: Int,
    marker: PathPlannerEventMarker,
    maxPos: Double,
    onChanged: (PathPlannerEventMarker) -> Unit,
    onDelete: () -> Unit
) = com.ares.analytics.ui.components.pathplanner.cards.EventMarkerCard(idx, marker, maxPos, onChanged, onDelete)

@Composable
fun RotationTargetCard(
    idx: Int,
    target: RotationTarget,
    maxPos: Double,
    onChanged: (RotationTarget) -> Unit,
    onDelete: () -> Unit
) = com.ares.analytics.ui.components.pathplanner.cards.RotationTargetCard(idx, target, maxPos, onChanged, onDelete)

@Composable
fun ConstraintsZoneCard(
    idx: Int,
    zone: ConstraintsZone,
    maxPos: Double,
    onChanged: (ConstraintsZone) -> Unit,
    onDelete: () -> Unit
) = com.ares.analytics.ui.components.pathplanner.cards.ConstraintsZoneCard(idx, zone, maxPos, onChanged, onDelete)

@Composable
fun PointTowardsZoneCard(
    idx: Int,
    zone: PointTowardsZone,
    maxPos: Double,
    onChanged: (PointTowardsZone) -> Unit,
    onDelete: () -> Unit
) = com.ares.analytics.ui.components.pathplanner.cards.PointTowardsZoneCard(idx, zone, maxPos, onChanged, onDelete)

@Composable
fun CollapsibleSection(
    title: String,
    badgeCount: Int? = null,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) = com.ares.analytics.ui.components.pathplanner.cards.CollapsibleSection(title, badgeCount, badgeText, content)
