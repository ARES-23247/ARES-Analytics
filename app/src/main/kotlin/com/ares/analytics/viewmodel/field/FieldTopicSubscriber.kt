package com.ares.analytics.viewmodel.field

import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.shared.GamePiece
import com.ares.analytics.viewmodel.FieldViewerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FieldTopicSubscriber(
    private val nt4ClientService: Nt4ClientService,
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<FieldViewerState>
) {
    init {
        scope.launch {
            nt4ClientService.isConnected.collect { connected ->
                stateFlow.update { currentState ->
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
            var lastEmit = System.currentTimeMillis()
            var currentBuilder = FieldViewerStateBuilder(stateFlow.value)
            
            nt4ClientService.telemetryFlow.collect { frame ->
                val key = frame.key
                val value = frame.value
                
                when (key) {
                    "ARES/EstimatedPose/0", "Drive/Pose_X" -> { currentBuilder.trueX = value; if (key == "Drive/Pose_X") currentBuilder.ekfX = value }
                    "ARES/EstimatedPose/1", "Drive/Pose_Y" -> { currentBuilder.trueY = value; if (key == "Drive/Pose_Y") currentBuilder.ekfY = value }
                    "ARES/EstimatedPose/2" -> { 
                        currentBuilder.simHeading = value
                        if (currentBuilder.ekfHeading == null) currentBuilder.trueHeading = value 
                    }
                    "Drive/Pose_Heading", "Drive/Drive_Heading" -> { 
                        currentBuilder.ekfHeading = value
                        currentBuilder.trueHeading = value
                    }
                    "Drive/Odom_X", "pinpoint_x", "pinpoint/x" -> currentBuilder.odomX = value
                    "Drive/Odom_Y", "pinpoint_y", "pinpoint/y" -> currentBuilder.odomY = value
                    "Drive/Odom_Heading", "pinpoint_heading", "pinpoint/heading" -> currentBuilder.odomHeading = value
                    "Vision/HasTarget" -> {
                        val hasTarget = value > 0.5
                        currentBuilder.visionHasTarget = hasTarget
                        if (!hasTarget) {
                            currentBuilder.visionX = null
                            currentBuilder.visionY = null
                            currentBuilder.visionHeading = null
                            currentBuilder.visionPoses.clear()
                        }
                    }
                    "Vision/Pose_X", "Vision/Pose/X" -> if (currentBuilder.visionHasTarget) currentBuilder.visionX = value
                    "Vision/Pose_Y", "Vision/Pose/Y" -> if (currentBuilder.visionHasTarget) currentBuilder.visionY = value
                    "Vision/Pose_Heading", "Vision/Pose/Heading" -> if (currentBuilder.visionHasTarget) currentBuilder.visionHeading = value
                    "ARES/Input/isRedAlliance" -> currentBuilder.isRedAlliance = value > 0.5
                }

                if (key.startsWith("Superstructure/IndicatorLight/")) {
                    val lightName = key.substringAfterLast("/")
                    currentBuilder.indicatorLights[lightName] = value
                }

                if (key.startsWith("Vision/PoseArray/") || key.startsWith("AdvantageScope/VisionPose/")) {
                    if (currentBuilder.visionHasTarget) {
                        val idx = key.substringAfterLast("/").toIntOrNull()
                        if (idx != null) {
                            currentBuilder.visionPoses[idx] = value
                        }
                    } else {
                        currentBuilder.visionPoses.clear()
                    }
                }

                if (key.startsWith("ARES/GamePieces/")) {
                    val arrayIdx = key.substringAfterLast("/").toIntOrNull()
                    if (arrayIdx != null) {
                        val pieceIdx = arrayIdx / 7
                        val attributeIdx = arrayIdx % 7
                        val currentPiece = currentBuilder.liveGamePieces[pieceIdx] ?: GamePiece(
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
                        
                        currentBuilder.liveGamePieces[pieceIdx] = updatedPiece
                    }
                }
                
                val now = System.currentTimeMillis()
                if (now - lastEmit > 16) {
                    stateFlow.update { currentBuilder.build(it) }
                    lastEmit = now
                    currentBuilder = FieldViewerStateBuilder(stateFlow.value)
                }
            }
        }
    }
}

class FieldViewerStateBuilder(state: FieldViewerState) {
    var trueX: Double = state.trueX
    var trueY: Double = state.trueY
    var trueHeading: Double = state.trueHeading
    var simHeading: Double? = state.simHeading
    var ekfX: Double? = state.ekfX
    var ekfY: Double? = state.ekfY
    var ekfHeading: Double? = state.ekfHeading
    var odomX: Double? = state.odomX
    var odomY: Double? = state.odomY
    var odomHeading: Double? = state.odomHeading
    var visionX: Double? = state.visionX
    var visionY: Double? = state.visionY
    var visionHeading: Double? = state.visionHeading
    var visionPoses: MutableMap<Int, Double> = state.visionPoses.toMutableMap()
    var visionHasTarget: Boolean = state.visionHasTarget
    var liveGamePieces: MutableMap<Int, GamePiece> = state.liveGamePieces.toMutableMap()
    var isRedAlliance: Boolean = state.isRedAlliance
    var indicatorLights: MutableMap<String, Double> = state.indicatorLights.toMutableMap()

    fun build(original: FieldViewerState): FieldViewerState {
        return original.copy(
            trueX = trueX,
            trueY = trueY,
            trueHeading = trueHeading,
            simHeading = simHeading,
            ekfX = ekfX,
            ekfY = ekfY,
            ekfHeading = ekfHeading,
            odomX = odomX,
            odomY = odomY,
            odomHeading = odomHeading,
            visionX = visionX,
            visionY = visionY,
            visionHeading = visionHeading,
            visionPoses = visionPoses.toMap(),
            visionHasTarget = visionHasTarget,
            liveGamePieces = liveGamePieces.toMap(),
            isRedAlliance = isRedAlliance,
            indicatorLights = indicatorLights.toMap()
        )
    }
}
