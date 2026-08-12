package com.ares.analytics.viewmodel.field

import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.GamePiece
import com.ares.analytics.viewmodel.FieldViewerState
import com.ares.analytics.viewmodel.LivePoseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reduces normalized NT4 topic updates into the field viewer's live-pose state. */
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
                        visionPoses = if (connected && currentState.visionHasTarget) currentState.visionPoses else emptyMap(),
                        liveGamePieces = if (connected) currentState.liveGamePieces else emptyMap()
                    )
                }
            }
        }

        scope.launch {
            var frameCount = 0L
            var lastDiagLog = System.currentTimeMillis()

            nt4ClientService.telemetryFlow.collect { frame ->
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
                        "ARES/EstimatedPose/2", "Drive/Pose_Heading" -> next = next.copy(ekfHeading = value)

                        "Drive/Odom_X" -> next = next.copy(odomX = value)
                        "Drive/Odom_Y" -> next = next.copy(odomY = value)
                        "Drive/Odom_Heading" -> next = next.copy(odomHeading = value)
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
                        "Vision/Pose_X" -> if (next.visionHasTarget) next = next.copy(visionX = value)
                        "Vision/Pose_Y" -> if (next.visionHasTarget) next = next.copy(visionY = value)
                        "Vision/Pose_Heading" -> if (next.visionHasTarget) next = next.copy(visionHeading = value)
                    }

                    if (key.startsWith("Superstructure/IndicatorLight/")) {
                        val lightName = key.substringAfterLast("/")
                        if (next.indicatorLights[lightName] != value) {
                            val newLights = next.indicatorLights.toMutableMap()
                            newLights[lightName] = value
                            next = next.copy(indicatorLights = newLights)
                        }
                    }

                    val isVisionPoseElement = key.startsWith("Vision/PoseArray/") ||
                        key.startsWith("AdvantageScope/VisionPose/")
                    when {
                        !isVisionPoseElement -> Unit
                        !next.visionHasTarget -> {
                            if (next.visionPoses.isNotEmpty()) next = next.copy(visionPoses = emptyMap())
                        }

                        else -> key.substringAfterLast("/").toIntOrNull()
                            ?.takeIf { next.visionPoses[it] != value }
                            ?.let { index ->
                                next = next.copy(visionPoses = next.visionPoses + (index to value))
                            }
                    }

                    if (key == "ARES/GamePieces/Count") {
                        val count = value.toInt().coerceAtLeast(0)
                        val retained = next.liveGamePieces.filterKeys { it in 0 until count }
                        if (retained.size != next.liveGamePieces.size) {
                            next = next.copy(liveGamePieces = retained)
                        }
                    } else if (key.startsWith("ARES/GamePieces/")) {
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

            }
        }
    }
}
