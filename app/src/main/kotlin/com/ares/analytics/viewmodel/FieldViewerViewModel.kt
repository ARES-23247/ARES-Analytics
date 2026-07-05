package com.ares.analytics.viewmodel

import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.ui.components.pathplanner.Waypoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FieldViewerState(
    val robotX: Double = 0.0,
    val robotY: Double = 0.0,
    val robotHeading: Double = 0.0,
    val ekfX: Double? = null,
    val ekfY: Double? = null,
    val ekfHeading: Double? = null,
    val visionX: Double? = null,
    val visionY: Double? = null,
    val visionHeading: Double? = null,
    val visionPoses: Map<Int, Double> = emptyMap(),
    val poseHistory: List<Waypoint> = emptyList(),
    val isConnected: Boolean = false
)

class FieldViewerViewModel(
    private val nt4ClientService: Nt4ClientService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(FieldViewerState())
    val state: StateFlow<FieldViewerState> = _state.asStateFlow()

    init {
        scope.launch {
            nt4ClientService.isConnected.collect { connected ->
                _state.update { it.copy(isConnected = connected) }
            }
        }
        scope.launch {
            nt4ClientService.telemetryFlow.collect { frame ->
                val key = frame.key
                val value = frame.value
                
                _state.update { currentState ->
                    var newState = currentState
                    when (key) {
                        "ARES/EstimatedPose/0" -> newState = newState.copy(robotX = value)
                        "ARES/EstimatedPose/1" -> newState = newState.copy(robotY = value)
                        "ARES/EstimatedPose/2" -> newState = newState.copy(robotHeading = value)
                        "Drive/Pose_X" -> newState = newState.copy(robotX = value)
                        "Drive/Pose_Y" -> newState = newState.copy(robotY = value)
                        "Drive/Pose_Heading", "Drive/Drive_Heading" -> newState = newState.copy(robotHeading = value)

                        "Drive/Odom_X", "pinpoint_x", "pinpoint/x" -> newState = newState.copy(ekfX = value)
                        "Drive/Odom_Y", "pinpoint_y", "pinpoint/y" -> newState = newState.copy(ekfY = value)
                        "Drive/Odom_Heading", "pinpoint_heading", "pinpoint/heading" -> newState = newState.copy(ekfHeading = value)

                        "Vision/Pose_X", "Vision/Pose/X" -> newState = newState.copy(visionX = value)
                        "Vision/Pose_Y", "Vision/Pose/Y" -> newState = newState.copy(visionY = value)
                        "Vision/Pose_Heading", "Vision/Pose/Heading" -> newState = newState.copy(visionHeading = value)
                    }

                    if (key.startsWith("AdvantageScope/VisionPose/")) {
                        val idx = key.substringAfterLast("/").toIntOrNull()
                        if (idx != null) {
                            val newMap = newState.visionPoses.toMutableMap()
                            newMap[idx] = value
                            newState = newState.copy(visionPoses = newMap)
                        }
                    }
                    
                    if (newState.robotX != currentState.robotX || newState.robotY != currentState.robotY || newState.robotHeading != currentState.robotHeading) {
                        if (newState.robotX != 0.0 || newState.robotY != 0.0) {
                            val newWp = Waypoint(newState.robotX, newState.robotY, newState.robotHeading)
                            val lastWp = newState.poseHistory.lastOrNull()
                            if (lastWp == null || kotlin.math.abs(lastWp.x - newWp.x) > 0.01 || kotlin.math.abs(lastWp.y - newWp.y) > 0.01) {
                                val newHistory = newState.poseHistory.toMutableList()
                                newHistory.add(newWp)
                                if (newHistory.size > 2000) {
                                    newHistory.subList(0, 500).clear()
                                }
                                newState = newState.copy(poseHistory = newHistory)
                            }
                        }
                    }
                    
                    newState
                }
            }
        }
    }
}

