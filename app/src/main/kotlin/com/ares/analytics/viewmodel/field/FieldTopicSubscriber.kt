package com.ares.analytics.viewmodel.field

import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.GamePiece
import com.ares.analytics.viewmodel.FieldViewerState
import com.ares.analytics.viewmodel.LivePoseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.
 */
class FieldTopicSubscriber(
    private val nt4ClientService: Nt4ClientService,
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<FieldViewerState>,
    private val livePoseFlow: MutableStateFlow<LivePoseState>
) {
    init {
        scope.launch {
            nt4ClientService.isConnected.collect { connected ->
                livePoseFlow.update { currentState ->
                    currentState.copy(
                        isConnected = connected,
                        visionHasTarget = if (connected) currentState.visionHasTarget else false,
                        visionX = if (connected && currentState.visionHasTarget) currentState.visionX else null,
                        visionY = if (connected && currentState.visionHasTarget) currentState.visionY else null,
                        visionHeading = if (connected && currentState.visionHasTarget) currentState.visionHeading else null,
                        visionPoses = if (connected && currentState.visionHasTarget) currentState.visionPoses else emptyMap()
                    )
                }
            }
        }

        scope.launch {
            var frameCount = 0L
            var lastDiagLog = System.currentTimeMillis()
            
            nt4ClientService.telemetryFlow.conflate().collect { frame ->
                val key = frame.key
                val value = frame.value
                frameCount++
                
                // Diagnostic: log every 2 seconds
                val now2 = System.currentTimeMillis()
                if (now2 - lastDiagLog > 2000) {
                    val s = livePoseFlow.value
                    println("[FieldTopicSubscriber] DIAG: $frameCount frames received, ekfX=${s.ekfX}, ekfY=${s.ekfY}, trueX=${s.trueX}, trueY=${s.trueY}")
                    lastDiagLog = now2
                    frameCount = 0
                }
                
                livePoseFlow.update { current ->
                    var next = current
                    
                    when (key) {
                        "ARES/TruePose/0" -> next = next.copy(trueX = value, hasTruePoseData = true)
                        "ARES/TruePose/1" -> next = next.copy(trueY = value, hasTruePoseData = true)
                        "ARES/TruePose/2" -> next = next.copy(simHeading = value, trueHeading = value, hasTruePoseData = true)
                        "ARES/EstimatedPose/0", "Drive/Pose_X" -> next = next.copy(ekfX = value)
                        "ARES/EstimatedPose/1", "Drive/Pose_Y" -> next = next.copy(ekfY = value)
                        "ARES/EstimatedPose/2", "Drive/Pose_Heading", "Drive/Drive_Heading" -> next = next.copy(ekfHeading = value)

                        "Drive/Odom_X", "pinpoint_x", "pinpoint/x" -> next = next.copy(odomX = value)
                        "Drive/Odom_Y", "pinpoint_y", "pinpoint/y" -> next = next.copy(odomY = value)
                        "Drive/Odom_Heading", "pinpoint_heading", "pinpoint/heading" -> next = next.copy(odomHeading = value)
                        "Vision/HasTarget" -> {
                            val hasTarget = value > 0.5
                            next = next.copy(visionHasTarget = hasTarget)
                            if (!hasTarget) {
                                next = next.copy(
                                    visionX = null,
                                    visionY = null,
                                    visionHeading = null,
                                    visionPoses = if (next.visionPoses.isNotEmpty()) emptyMap() else next.visionPoses
                                )
                            }
                        }
                        "Vision/Pose_X", "Vision/Pose/X" -> if (next.visionHasTarget) next = next.copy(visionX = value)
                        "Vision/Pose_Y", "Vision/Pose/Y" -> if (next.visionHasTarget) next = next.copy(visionY = value)
                        "Vision/Pose_Heading", "Vision/Pose/Heading" -> if (next.visionHasTarget) next = next.copy(visionHeading = value)
                    }

                    if (key.startsWith("Superstructure/IndicatorLight/")) {
                        val lightName = key.substringAfterLast("/")
                        if (next.indicatorLights[lightName] != value) {
                            val newLights = next.indicatorLights.toMutableMap()
                            newLights[lightName] = value
                            next = next.copy(indicatorLights = newLights)
                        }
                    }

                    if (key.startsWith("Vision/PoseArray/") || key.startsWith("AdvantageScope/VisionPose/")) {
                        if (next.visionHasTarget) {
                            val idx = key.substringAfterLast("/").toIntOrNull()
                            if (idx != null) {
                                if (next.visionPoses[idx] != value) {
                                    val newPoses = next.visionPoses.toMutableMap()
                                    newPoses[idx] = value
                                    next = next.copy(visionPoses = newPoses)
                                }
                            }
                        } else if (next.visionPoses.isNotEmpty()) {
                            next = next.copy(visionPoses = emptyMap())
                        }
                    }

                    if (key.startsWith("ARES/GamePieces/")) {
                        val arrayIdx = key.substringAfterLast("/").toIntOrNull()
                        if (arrayIdx != null) {
                            val pieceIdx = arrayIdx / 7
                            val attributeIdx = arrayIdx % 7
                            val currentPiece = next.liveGamePieces[pieceIdx] ?: GamePiece(
                                id = pieceIdx.toString(),
                                name = "Piece $pieceIdx",
                                x = 0.0,
                                y = 0.0,
                                type = "Decode (Ball)"
                            )
                            val updatedPiece = when (attributeIdx) {
                                0 -> currentPiece.copy(x = value)
                                1 -> currentPiece.copy(y = value)
                                else -> currentPiece
                            }
                            
                            val newPieces = next.liveGamePieces.toMutableMap()
                            newPieces[pieceIdx] = updatedPiece
                            next = next.copy(liveGamePieces = newPieces)
                        }
                    }
                    
                    next
                }

                if (key == "ARES/Input/isRedAlliance") {
                    stateFlow.update { it.copy(isRedAlliance = value > 0.5) }
                }
            }
        }
    }
}

