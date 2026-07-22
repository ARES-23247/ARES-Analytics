package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.viewmodel.PathPlannerIntent
import com.ares.analytics.viewmodel.PathPlannerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.hypot

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class WaypointController(
    private val stateFlow: MutableStateFlow<PathPlannerState>,
    private val onWaypointsChanged: () -> Unit
) {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun handleIntent(intent: PathPlannerIntent): Boolean {
        when (intent) {
            is PathPlannerIntent.UpdateWaypoints -> {
                stateFlow.update { it.copy(waypoints = intent.newWaypoints) }
                onWaypointsChanged()
                return true
            }
            is PathPlannerIntent.UpdateWaypoint -> {
                val updated = stateFlow.value.waypoints.toMutableList().apply { set(intent.index, intent.waypoint) }
                stateFlow.update { it.copy(waypoints = updated) }
                onWaypointsChanged()
                return true
            }
            is PathPlannerIntent.AddWaypoint -> {
                val updated = stateFlow.value.waypoints.toMutableList().apply { add(intent.waypoint) }
                stateFlow.update { it.copy(waypoints = updated) }
                onWaypointsChanged()
                return true
            }
            is PathPlannerIntent.DeleteWaypoint -> {
                val updated = stateFlow.value.waypoints.toMutableList().apply { removeAt(intent.index) }
                stateFlow.update { it.copy(waypoints = updated) }
                onWaypointsChanged()
                return true
            }
            is PathPlannerIntent.SelectWaypoint -> {
                stateFlow.update { it.copy(selectedWaypointIndex = intent.index) }
                return true
            }
            is PathPlannerIntent.OptimizePath -> {
                val wps = stateFlow.value.waypoints
                if (wps.size >= 2) {
                    val optimized = wps.mapIndexed { i, wp ->
                        var prevDist = 0.5
                        var nextDist = 0.5
                        if (i > 0) {
                            val prevWp = wps[i - 1]
                            prevDist = hypot(wp.x - prevWp.x, wp.y - prevWp.y) * 0.4
                        }
                        if (i < wps.size - 1) {
                            val nextWp = wps[i + 1]
                            nextDist = hypot(nextWp.x - wp.x, nextWp.y - wp.y) * 0.4
                        }
                        wp.copy(prevControlLength = prevDist, nextControlLength = nextDist)
                    }
                    stateFlow.update { it.copy(waypoints = optimized) }
                    onWaypointsChanged()
                }
                return true
            }
            else -> return false
        }
    }
}
