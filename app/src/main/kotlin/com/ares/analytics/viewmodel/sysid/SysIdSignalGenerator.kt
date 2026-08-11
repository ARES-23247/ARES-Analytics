package com.ares.analytics.viewmodel.sysid

import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.viewmodel.SysIdState
import com.areslib.control.assist.SysIdMechanism
import com.areslib.control.assist.SysIdRoutine
import com.areslib.tuning.TuningTopics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Publishes SysId routine commands and calibration controls through NT4. */
class SysIdSignalGenerator(
    private val nt4ClientService: Nt4ClientService,
    private val _state: MutableStateFlow<SysIdState>
) {
    suspend fun applyToRobotCode(recommendedExponent: Double, recommendedSlewRate: Double) {
        _state.update { it.copy(exportStatus = "Applying to robot over NT4...") }
        try {
            nt4ClientService.publishDouble(TuningTopics.DRIVER_DEADBAND_EXPONENT, recommendedExponent)
            val slewVal = if (recommendedSlewRate == Double.MAX_VALUE) 999.0 else recommendedSlewRate
            nt4ClientService.publishDouble(TuningTopics.DRIVER_SLEW_RATE_LIMIT, slewVal)

            _state.update {
                it.copy(exportStatus = "Successfully applied! 🎉")
            }
        } catch (e: Exception) {
            _state.update { it.copy(exportStatus = "Failed to apply: ${e.message}") }
        }
    }

    suspend fun startRoutine(mechanism: SysIdMechanism, routine: SysIdRoutine) {
        _state.update {
            it.copy(
                liveSamples = emptyList(),
                liveCalibrationData = emptyList(),
                isRoutineRunning = false,
                summary = null,
                isLoading = true,
                errorMessage = null,
            )
        }
        val cmd = "START_${mechanism.name}_${routine.name}"
        try {
            nt4ClientService.publishInputString(1015, cmd)
            _state.update { it.copy(isRoutineRunning = true) }
        } catch (error: CancellationException) {
            _state.update { it.copy(isRoutineRunning = false, isLoading = false) }
            throw error
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isRoutineRunning = false,
                    isLoading = false,
                    errorMessage = "Could not start SysId: ${error.message ?: "robot did not accept the command"}",
                )
            }
        }
    }

    suspend fun stopRoutine() {
        try {
            nt4ClientService.publishInputString(1015, "STOP")
            _state.update { it.copy(isRoutineRunning = false, isLoading = false) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(errorMessage = "Could not stop SysId: ${error.message ?: "robot did not acknowledge stop"}") }
        }
    }

    suspend fun startCalibration(calibrationType: String) {
        _state.update {
            it.copy(
                liveSamples = emptyList(),
                liveCalibrationData = emptyList(),
                isRoutineRunning = false,
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
        try {
            nt4ClientService.publishInputString(1015, "START_${calibrationType}")
            _state.update { it.copy(isRoutineRunning = true) }
        } catch (error: CancellationException) {
            _state.update { it.copy(isRoutineRunning = false, isLoading = false) }
            throw error
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    isRoutineRunning = false,
                    isLoading = false,
                    activeCalibration = "NONE",
                    errorMessage = "Could not start calibration: ${error.message ?: "robot did not accept the command"}",
                )
            }
        }
    }

    suspend fun stopCalibration() {
        try {
            nt4ClientService.publishInputString(1015, "STOP")
            _state.update { it.copy(isRoutineRunning = false, activeCalibration = "NONE", isLoading = false) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(errorMessage = "Could not stop calibration: ${error.message ?: "robot did not acknowledge stop"}") }
        }
    }

    suspend fun applyCalibration(calibrationType: String) {
        _state.update { it.copy(exportStatus = "Applying calibration to robot...") }
        try {
            when (calibrationType) {
                "PINPOINT_SPIN" -> {
                    val x = _state.value.recommendedPinpointXOffsetMm
                    val y = _state.value.recommendedPinpointYOffsetMm
                    if (x != null && y != null) {
                        nt4ClientService.publishDouble(TuningTopics.PINPOINT_X_OFFSET, x)
                        nt4ClientService.publishDouble(TuningTopics.PINPOINT_Y_OFFSET, y)
                        _state.update { it.copy(exportStatus = "Applied Pinpoint Offsets! 🎉") }
                    }
                }
                "TRACK_WIDTH_SPIN" -> {
                    val tw = _state.value.recommendedTrackWidthMeters
                    if (tw != null) {
                        nt4ClientService.publishDouble(TuningTopics.DRIVE_TRACK_WIDTH, tw)
                        _state.update { it.copy(exportStatus = "Applied Track Width! 🎉") }
                    }
                }
                "VISION_CALIBRATION" -> {
                    val sx = _state.value.recommendedVisionStdDevsX
                    val sy = _state.value.recommendedVisionStdDevsY
                    val sh = _state.value.recommendedVisionStdDevsHeading
                    if (sx != null && sy != null && sh != null) {
                        nt4ClientService.publishDouble(TuningTopics.VISION_STD_DEVS_X, sx)
                        nt4ClientService.publishDouble(TuningTopics.VISION_STD_DEVS_Y, sy)
                        nt4ClientService.publishDouble(TuningTopics.VISION_STD_DEVS_HEADING, sh)
                        _state.update { it.copy(exportStatus = "Applied Vision Std Devs! 🎉") }
                    }
                }
                "LINEAR_DRIVE" -> {
                    val ticks = _state.value.recommendedTicksPerMeter
                    if (ticks != null) {
                        nt4ClientService.publishDouble(TuningTopics.FTC_TICKS_PER_METER, ticks)
                        _state.update { it.copy(exportStatus = "Applied Ticks Per Meter! 🎉") }
                    }
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(exportStatus = "Failed to apply calibration: ${e.message}") }
        }
    }
}
