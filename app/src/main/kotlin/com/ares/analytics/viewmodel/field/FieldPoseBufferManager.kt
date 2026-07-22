package com.ares.analytics.viewmodel.field

import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.viewmodel.FieldViewerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class FieldPoseBufferManager(
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<FieldViewerState>
) {
    init {
        scope.launch {
            while (true) {
                delay(50)
                val currentState = stateFlow.value
                if (currentState.trueX != 0.0 || currentState.trueY != 0.0) {
                    val newWp = Waypoint(currentState.trueX, currentState.trueY, currentState.trueHeading)
                    val lastWp = currentState.poseHistory.lastOrNull()
                    if (lastWp == null || kotlin.math.abs(lastWp.x - newWp.x) > 0.01 || kotlin.math.abs(lastWp.y - newWp.y) > 0.01) {
                        val newHistory = currentState.poseHistory.toMutableList()
                        newHistory.add(newWp)
                        if (newHistory.size > 2000) {
                            newHistory.subList(0, 500).clear()
                        }
                        stateFlow.update { it.copy(poseHistory = newHistory) }
                    } else if (lastWp.headingRad != newWp.headingRad) {
                        val newHistory = currentState.poseHistory.toMutableList()
                        newHistory[newHistory.size - 1] = newWp
                        stateFlow.update { it.copy(poseHistory = newHistory) }
                    }
                }
            }
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun clearTrace() {
        stateFlow.update { it.copy(poseHistory = emptyList()) }
    }
}
