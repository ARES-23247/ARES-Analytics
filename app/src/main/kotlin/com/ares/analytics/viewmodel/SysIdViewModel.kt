package com.ares.analytics.viewmodel

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.DriverAnalysisService
import com.ares.analytics.service.DriverProfileAnalysisResult
import com.ares.analytics.service.SysIdService
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.service.AlignedDataRow
import com.ares.analytics.shared.CalculatedSummary
import com.areslib.control.assist.SysIdMechanism
import com.areslib.control.assist.SysIdRoutine
import com.ares.analytics.viewmodel.sysid.SysIdDataCollector
import com.ares.analytics.viewmodel.sysid.SysIdRegressionSolver
import com.ares.analytics.viewmodel.sysid.SysIdSignalGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
data class SysIdState(
    val sessionId: String? = null,
    val summary: CalculatedSummary? = null,
    val jitterResult: DriverProfileAnalysisResult? = null,
    val exportStatus: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    
    // Robot connection and live routines
    val isRobotConnected: Boolean = false,
    val isRoutineRunning: Boolean = false,
    val selectedMechanism: SysIdMechanism = SysIdMechanism.LINEAR,
    val liveSamples: List<AlignedDataRow> = emptyList(),
    
    // Standalone file upload analysis
    val localAnalysisResult: CalculatedSummary? = null,
    val fileAnalysisError: String? = null,

    // New Auto-Tuning/Calibration features
    val activeCalibration: String = "NONE", // "NONE", "PINPOINT_SPIN", "TRACK_WIDTH_SPIN", "VISION_CALIBRATION", "LINEAR_DRIVE"
    val liveCalibrationData: List<DoubleArray> = emptyList(),
    val recommendedPinpointXOffsetMm: Double? = null,
    val recommendedPinpointYOffsetMm: Double? = null,
    val recommendedTrackWidthMeters: Double? = null,
    val recommendedVisionStdDevsX: Double? = null,
    val recommendedVisionStdDevsY: Double? = null,
    val recommendedVisionStdDevsHeading: Double? = null,
    val recommendedTicksPerMeter: Double? = null,

    // For linear drive calibration distance input
    val linearDriveActualDistanceMeters: Double = 2.0
)

sealed class SysIdIntent {
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class LoadSession(val sessionId: String?) : SysIdIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class ApplyToRobotCode(
        val recommendedExponent: Double,
        val recommendedSlewRate: Double,
        val projectPath: String
    ) : SysIdIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object ClearExportStatus : SysIdIntent()
    
    // Live routine controls
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetMechanism(val mechanism: SysIdMechanism) : SysIdIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class StartRoutine(val routine: SysIdRoutine) : SysIdIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object StopRoutine : SysIdIntent()
    
    // Standalone log analysis
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class LoadLocalLogFile(val fileContent: String) : SysIdIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object ClearLocalAnalysis : SysIdIntent()

    // New Auto-Tuning/Calibration intents
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class StartCalibration(val calibrationType: String) : SysIdIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    object StopCalibration : SysIdIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class SetLinearDriveDistance(val distance: Double) : SysIdIntent()
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    data class ApplyCalibration(val calibrationType: String) : SysIdIntent()
}

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class SysIdViewModel(
    private val databaseService: DatabaseService,
    private val sysIdService: SysIdService,
    private val driverAnalysisService: DriverAnalysisService,
    val nt4ClientService: Nt4ClientService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(SysIdState())
    val state: StateFlow<SysIdState> = _state.asStateFlow()

    private val regressionSolver = SysIdRegressionSolver(nt4ClientService, _state)
    private val dataCollector = SysIdDataCollector(nt4ClientService, sysIdService, _state, scope, regressionSolver)
    private val signalGenerator = SysIdSignalGenerator(nt4ClientService, _state)

    init {
        dataCollector.startCollecting()
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun onIntent(intent: SysIdIntent) {
        scope.launch {
            when (intent) {
                is SysIdIntent.LoadSession -> {
                    val sessionId = intent.sessionId
                    _state.update { it.copy(sessionId = sessionId, isLoading = true, summary = null, jitterResult = null, errorMessage = null) }
                    if (sessionId != null) {
                        try {
                            withContext(Dispatchers.IO) {
                                val summaryResult = sysIdService.analyzeMotorData(
                                    sessionId = sessionId,
                                    voltageKey = "/Drive/Voltage",
                                    velocityKey = "/Drive/Velocity",
                                    accelerationKey = "/Drive/Acceleration"
                                )
                                val jitterResult = driverAnalysisService.analyzeDriverJitter(
                                    sessionId = sessionId
                                )
                                _state.update {
                                    it.copy(
                                        summary = summaryResult,
                                        jitterResult = jitterResult,
                                        isLoading = false
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to perform analysis") }
                        }
                    } else {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
                is SysIdIntent.ApplyToRobotCode -> {
                    signalGenerator.applyToRobotCode(intent.recommendedExponent, intent.recommendedSlewRate)
                }
                is SysIdIntent.ClearExportStatus -> {
                    _state.update { it.copy(exportStatus = "") }
                }
                is SysIdIntent.SetMechanism -> {
                    _state.update { it.copy(selectedMechanism = intent.mechanism) }
                }
                is SysIdIntent.StartRoutine -> {
                    dataCollector.clearBuffer()
                    signalGenerator.startRoutine(_state.value.selectedMechanism, intent.routine)
                }
                is SysIdIntent.StopRoutine -> {
                    signalGenerator.stopRoutine()
                }
                is SysIdIntent.LoadLocalLogFile -> {
                    _state.update { it.copy(isLoading = true, fileAnalysisError = null, localAnalysisResult = null) }
                    try {
                        val rows = withContext(Dispatchers.IO) {
                            dataCollector.parseLogFile(intent.fileContent)
                        }
                        if (rows.size < 10) {
                            _state.update { it.copy(isLoading = false, fileAnalysisError = "Not enough valid data rows found in file (minimum 10 required)") }
                        } else {
                            val summary = withContext(Dispatchers.IO) {
                                sysIdService.analyzeRawData(rows)
                            }
                            _state.update { it.copy(isLoading = false, localAnalysisResult = summary) }
                        }
                    } catch (e: Exception) {
                        _state.update { it.copy(isLoading = false, fileAnalysisError = "Failed to parse file: ${e.message}") }
                    }
                }
                is SysIdIntent.ClearLocalAnalysis -> {
                    _state.update { it.copy(localAnalysisResult = null, fileAnalysisError = null) }
                }
                is SysIdIntent.StartCalibration -> {
                    dataCollector.clearBuffer()
                    signalGenerator.startCalibration(intent.calibrationType)
                }
                is SysIdIntent.StopCalibration -> {
                    signalGenerator.stopCalibration()
                }
                is SysIdIntent.SetLinearDriveDistance -> {
                    _state.update { it.copy(linearDriveActualDistanceMeters = intent.distance) }
                }
                is SysIdIntent.ApplyCalibration -> {
                    signalGenerator.applyCalibration(intent.calibrationType)
                }
            }
        }
    }
}
