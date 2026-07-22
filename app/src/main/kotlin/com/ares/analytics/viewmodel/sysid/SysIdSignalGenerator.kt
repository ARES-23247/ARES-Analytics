package com.ares.analytics.viewmodel.sysid

import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.viewmodel.SysIdState
import com.areslib.control.assist.SysIdMechanism
import com.areslib.control.assist.SysIdRoutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class SysIdSignalGenerator(
    private val nt4ClientService: Nt4ClientService,
    private val _state: MutableStateFlow<SysIdState>
) {
    suspend fun applyToRobotCode(recommendedExponent: Double, recommendedSlewRate: Double) {
        _state.update { it.copy(exportStatus = "Applying to robot over NT4...") }
        try {
            nt4ClientService.publishDouble("Tuning/driverDeadbandExponent", recommendedExponent)
            val slewVal = if (recommendedSlewRate == Double.MAX_VALUE) 999.0 else recommendedSlewRate
            nt4ClientService.publishDouble("Tuning/driverSlewRateLimit", slewVal)
            
            _state.update {
                it.copy(exportStatus = "Successfully applied! 🎉")
            }
        } catch (e: Exception) {
            _state.update { it.copy(exportStatus = "Failed to apply: ${e.message}") }
        }
    }

    suspend fun startRoutine(mechanism: SysIdMechanism, routine: SysIdRoutine) {
        _state.update { it.copy(liveSamples = emptyList(), liveCalibrationData = emptyList(), isRoutineRunning = true, summary = null, isLoading = true) }
        val cmd = "START_${mechanism.name}_${routine.name}"
        nt4ClientService.publishInputString(1015, cmd)
    }

    suspend fun stopRoutine() {
        _state.update { it.copy(isRoutineRunning = false, isLoading = false) }
        nt4ClientService.publishInputString(1015, "STOP")
    }

    suspend fun startCalibration(calibrationType: String) {
        _state.update {
            it.copy(
                liveSamples = emptyList(),
                liveCalibrationData = emptyList(),
                isRoutineRunning = true,
                activeCalibration = calibrationType,
                isLoading = true,
                errorMessage = null,
                recommendedPinpointXOffsetMm = null,
                recommendedPinpointYOffsetMm = null,
                recommendedTrackWidthMeters = null,
                recommendedVisionStdDevsX = null,
                recommendedVisionStdDevsY = null,
                recommendedVisionStdDevsHeading = null,
                recommendedTicksPerMeter = null
            )
        }
        nt4ClientService.publishInputString(1015, "START_${calibrationType}")
    }

    suspend fun stopCalibration() {
        _state.update { it.copy(isRoutineRunning = false, activeCalibration = "NONE", isLoading = false) }
        nt4ClientService.publishInputString(1015, "STOP")
    }

    suspend fun applyCalibration(calibrationType: String) {
        _state.update { it.copy(exportStatus = "Applying calibration to robot...") }
        try {
            when (calibrationType) {
                "PINPOINT_SPIN" -> {
                    val x = _state.value.recommendedPinpointXOffsetMm
                    val y = _state.value.recommendedPinpointYOffsetMm
                    if (x != null && y != null) {
                        nt4ClientService.publishDouble("Tuning/pinpointXOffsetMm", x)
                        nt4ClientService.publishDouble("Tuning/pinpointYOffsetMm", y)
                        _state.update { it.copy(exportStatus = "Applied Pinpoint Offsets! 🎉") }
                    }
                }
                "TRACK_WIDTH_SPIN" -> {
                    val tw = _state.value.recommendedTrackWidthMeters
                    if (tw != null) {
                        nt4ClientService.publishDouble("Tuning/trackWidthMeters", tw)
                        _state.update { it.copy(exportStatus = "Applied Track Width! 🎉") }
                    }
                }
                "VISION_CALIBRATION" -> {
                    val sx = _state.value.recommendedVisionStdDevsX
                    val sy = _state.value.recommendedVisionStdDevsY
                    val sh = _state.value.recommendedVisionStdDevsHeading
                    if (sx != null && sy != null && sh != null) {
                        nt4ClientService.publishDouble("Tuning/visionStdDevsX", sx)
                        nt4ClientService.publishDouble("Tuning/visionStdDevsY", sy)
                        nt4ClientService.publishDouble("Tuning/visionStdDevsHeading", sh)
                        _state.update { it.copy(exportStatus = "Applied Vision Std Devs! 🎉") }
                    }
                }
                "LINEAR_DRIVE" -> {
                    val ticks = _state.value.recommendedTicksPerMeter
                    if (ticks != null) {
                        nt4ClientService.publishDouble("Tuning/ticksPerMeter", ticks)
                        _state.update { it.copy(exportStatus = "Applied Ticks Per Meter! 🎉") }
                    }
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(exportStatus = "Failed to apply calibration: ${e.message}") }
        }
    }
}
