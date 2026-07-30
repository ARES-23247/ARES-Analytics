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
 */
class FieldPoseBufferManager(
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<FieldViewerState>
) {
    private val poseBuffer = ArrayDeque<Waypoint>(150)

    init {
        scope.launch {
            while (true) {
                delay(50)
                val currentState = stateFlow.value
                // Use simulator TruePose if available, otherwise fall back to EKF data from real robot
                val x = if (currentState.hasTruePoseData) currentState.trueX else (currentState.ekfX ?: currentState.trueX)
                val y = if (currentState.hasTruePoseData) currentState.trueY else (currentState.ekfY ?: currentState.trueY)
                val heading = if (currentState.hasTruePoseData) currentState.trueHeading else (currentState.ekfHeading ?: currentState.trueHeading)

                val lastWp = poseBuffer.lastOrNull()
                if (lastWp == null || kotlin.math.abs(lastWp.x - x) > 0.01 || kotlin.math.abs(lastWp.y - y) > 0.01) {
                    if (poseBuffer.size >= 150) {
                        poseBuffer.removeFirst()
                    }
                    val newWp = Waypoint(x, y, heading)
                    poseBuffer.addLast(newWp)
                    val newHistory = poseBuffer.toList()
                    stateFlow.update { it.copy(poseHistory = newHistory) }
                } else if (lastWp.headingRad != heading) {
                    poseBuffer.removeLast()
                    val newWp = Waypoint(x, y, heading)
                    poseBuffer.addLast(newWp)
                    val newHistory = poseBuffer.toList()
                    stateFlow.update { it.copy(poseHistory = newHistory) }
                }
            }
        }
    }

    /**
     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.
     */
    fun clearTrace() {
        poseBuffer.clear()
        stateFlow.update { it.copy(poseHistory = emptyList()) }
    }
}
