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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.ejml.simple.SimpleMatrix
import java.util.concurrent.ConcurrentHashMap

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
    data class LoadSession(val sessionId: String?) : SysIdIntent()
    data class ApplyToRobotCode(
        val recommendedExponent: Double,
        val recommendedSlewRate: Double,
        val projectPath: String
    ) : SysIdIntent()
    object ClearExportStatus : SysIdIntent()
    
    // Live routine controls
    data class SetMechanism(val mechanism: SysIdMechanism) : SysIdIntent()
    data class StartRoutine(val routine: SysIdRoutine) : SysIdIntent()
    object StopRoutine : SysIdIntent()
    
    // Standalone log analysis
    data class LoadLocalLogFile(val fileContent: String) : SysIdIntent()
    object ClearLocalAnalysis : SysIdIntent()

    // New Auto-Tuning/Calibration intents
    data class StartCalibration(val calibrationType: String) : SysIdIntent()
    object StopCalibration : SysIdIntent()
    data class SetLinearDriveDistance(val distance: Double) : SysIdIntent()
    data class ApplyCalibration(val calibrationType: String) : SysIdIntent()
}

class SysIdViewModel(
    private val databaseService: DatabaseService,
    private val sysIdService: SysIdService,
    private val driverAnalysisService: DriverAnalysisService,
    val nt4ClientService: Nt4ClientService,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(SysIdState())
    val state: StateFlow<SysIdState> = _state.asStateFlow()
    private val dataBuffer = ConcurrentHashMap<Long, DoubleArray>()

    init {
        // Collect connection status
        scope.launch {
            nt4ClientService.isConnected.collect { connected ->
                _state.update { it.copy(isRobotConnected = connected) }
            }
        }

        // Collect live streaming data from the robot

        scope.launch {
            nt4ClientService.telemetryFlow.collect { frame ->
                when {
                    frame.key == "SysId/Status" -> {
                        val status = frame.stringValue ?: ""
                        val wasRunning = _state.value.isRoutineRunning
                        val isRunning = status.isNotEmpty() && status != "NONE"
                        
                        val prevCalibration = _state.value.activeCalibration
                        
                        _state.update { 
                            it.copy(
                                isRoutineRunning = isRunning,
                                activeCalibration = if (isRunning) status else it.activeCalibration
                            ) 
                        }
                        
                        if (wasRunning && !isRunning) {
                            // Routine/Calibration just completed!
                            val finalCalibration = prevCalibration
                            _state.update { it.copy(isLoading = false) }
                            if (finalCalibration == "PINPOINT_SPIN" || finalCalibration == "TRACK_WIDTH_SPIN" ||
                                finalCalibration == "VISION_CALIBRATION" || finalCalibration == "LINEAR_DRIVE") {
                                runCalibrationAnalysis(finalCalibration, _state.value.liveCalibrationData)
                            } else {
                                val samples = _state.value.liveSamples
                                if (samples.isNotEmpty()) {
                                    val summary = sysIdService.analyzeRawData(samples)
                                    _state.update { it.copy(summary = summary) }
                                }
                            }
                        }
                    }
                    frame.key.startsWith("SysId/Data/") -> {
                        val idx = frame.key.removePrefix("SysId/Data/").toIntOrNull()
                        if (idx != null) {
                            val t = frame.timestampMs
                            val arr = dataBuffer.getOrPut(t) { DoubleArray(6) }
                            if (idx < arr.size) {
                                arr[idx] = frame.value
                            }
                            
                            val expectedMaxIdx = when (_state.value.activeCalibration) {
                                "PINPOINT_SPIN", "VISION_CALIBRATION" -> 3
                                "LINEAR_DRIVE" -> 4
                                "TRACK_WIDTH_SPIN" -> 5
                                else -> 4
                            }
                            
                            if (idx == expectedMaxIdx) {
                                val completedArr = dataBuffer[t]
                                if (completedArr != null) {
                                    val sample = AlignedDataRow(
                                        timestampMs = completedArr[0].toLong(),
                                        voltage = completedArr[1],
                                        velocity = completedArr[3],
                                        accel = completedArr[4]
                                    )
                                    _state.update {
                                        it.copy(
                                            liveSamples = it.liveSamples + sample,
                                            liveCalibrationData = it.liveCalibrationData + listOf(completedArr.clone())
                                        )
                                    }
                                    if (dataBuffer.size > 500) {
                                        val minT = dataBuffer.keys.minOrNull() ?: 0L
                                        dataBuffer.remove(minT)
                                    }
                                }
                            }
                        }
                    }
                    frame.key == "SysId/Data" -> {
                        val stringVal = frame.stringValue
                        if (stringVal != null) {
                            val parts = stringVal.split("|").mapNotNull { it.toDoubleOrNull() }
                            if (parts.size >= 5) {
                                val sample = AlignedDataRow(
                                    timestampMs = parts[0].toLong(),
                                    voltage = parts[1],
                                    velocity = parts[3],
                                    accel = parts[4]
                                )
                                _state.update { it.copy(liveSamples = it.liveSamples + sample) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun runCalibrationAnalysis(calibrationType: String, data: List<DoubleArray>) {
        if (data.size < 10) {
            _state.update { it.copy(errorMessage = "Not enough calibration data collected (minimum 10 points)") }
            return
        }
        
        try {
            when (calibrationType) {
                "PINPOINT_SPIN" -> {
                    val n = data.size
                    val A = SimpleMatrix(2 * n, 4)
                    val b = SimpleMatrix(2 * n, 1)
                    
                    for (i in 0 until n) {
                        val row = data[i]
                        val x = row[1]
                        val y = row[2]
                        val heading = row[3]
                        val cosT = kotlin.math.cos(heading)
                        val sinT = kotlin.math.sin(heading)
                        
                        A.setRow(2 * i, 0, 1.0, 0.0, cosT, -sinT)
                        A.setRow(2 * i + 1, 0, 0.0, 1.0, sinT, cosT)
                        
                        b.set(2 * i, 0, x)
                        b.set(2 * i + 1, 0, y)
                    }
                    
                    val beta = A.solve(b)
                    val dxMeters = beta.get(2, 0)
                    val dyMeters = beta.get(3, 0)
                    
                    val deltaXOffsetMm = dxMeters * 1000.0
                    val deltaYOffsetMm = dyMeters * 1000.0
                    
                    _state.update {
                        it.copy(
                            recommendedPinpointXOffsetMm = deltaXOffsetMm,
                            recommendedPinpointYOffsetMm = deltaYOffsetMm,
                            errorMessage = null
                        )
                    }
                }
                "TRACK_WIDTH_SPIN" -> {
                    val n = data.size
                    var accumHeading = 0.0
                    var lastHeading = data[0][5]
                    val unwrappedHeadings = DoubleArray(n)
                    unwrappedHeadings[0] = 0.0
                    
                    for (i in 1 until n) {
                        val currentHeading = data[i][5]
                        var diff = currentHeading - lastHeading
                        while (diff < -kotlin.math.PI) diff += 2 * kotlin.math.PI
                        while (diff > kotlin.math.PI) diff -= 2 * kotlin.math.PI
                        accumHeading += diff
                        unwrappedHeadings[i] = accumHeading
                        lastHeading = currentHeading
                    }
                    
                    var sumXY = 0.0
                    var sumX2 = 0.0
                    
                    val fl0 = data[0][1]
                    val fr0 = data[0][2]
                    val rl0 = data[0][3]
                    val rr0 = data[0][4]
                    
                    for (i in 0 until n) {
                        val fl = data[i][1] - fl0
                        val fr = data[i][2] - fr0
                        val rl = data[i][3] - rl0
                        val rr = data[i][4] - rr0
                        
                        val y = -fl + fr - rl + rr
                        val x = 4.0 * unwrappedHeadings[i]
                        
                        sumXY += x * y
                        sumX2 += x * x
                    }
                    
                    val k = if (sumX2 > 1e-6) sumXY / sumX2 else 0.45
                    
                    val wheelBase = nt4ClientService.latestValues["Tuning/wheelBaseMeters"]?.value ?: 0.45
                    val recTrackWidth = 2.0 * k - wheelBase
                    
                    _state.update {
                        it.copy(
                            recommendedTrackWidthMeters = recTrackWidth,
                            errorMessage = null
                        )
                    }
                }
                "VISION_CALIBRATION" -> {
                    val n = data.size
                    val meanX = data.map { it[1] }.average()
                    val meanY = data.map { it[2] }.average()
                    val meanHeading = data.map { it[3] }.average()
                    
                    var varX = 0.0
                    var varY = 0.0
                    var varHeading = 0.0
                    
                    for (row in data) {
                        val dx = row[1] - meanX
                        val dy = row[2] - meanY
                        
                        var dHeading = row[3] - meanHeading
                        while (dHeading < -kotlin.math.PI) dHeading += 2 * kotlin.math.PI
                        while (dHeading > kotlin.math.PI) dHeading -= 2 * kotlin.math.PI
                        
                        varX += dx * dx
                        varY += dy * dy
                        varHeading += dHeading * dHeading
                    }
                    
                    val stdX = kotlin.math.sqrt(varX / (n - 1))
                    val stdY = kotlin.math.sqrt(varY / (n - 1))
                    val stdHeading = kotlin.math.sqrt(varHeading / (n - 1))
                    
                    _state.update {
                        it.copy(
                            recommendedVisionStdDevsX = stdX,
                            recommendedVisionStdDevsY = stdY,
                            recommendedVisionStdDevsHeading = stdHeading,
                            errorMessage = null
                        )
                    }
                }
                "LINEAR_DRIVE" -> {
                    val firstDisplacement = data.firstOrNull()?.get(1) ?: 0.0
                    val lastDisplacement = data.lastOrNull()?.get(1) ?: 0.0
                    val reportedDisplacement = lastDisplacement - firstDisplacement
                    
                    val actualDistance = _state.value.linearDriveActualDistanceMeters
                    
                    if (actualDistance > 0.1 && reportedDisplacement > 0.05) {
                        val currentTicks = nt4ClientService.latestValues["Tuning/ticksPerMeter"]?.value ?: 2000.0
                        val recTicks = currentTicks * (reportedDisplacement / actualDistance)
                        
                        _state.update {
                            it.copy(
                                recommendedTicksPerMeter = recTicks,
                                errorMessage = null
                            )
                        }
                    } else {
                        _state.update { it.copy(errorMessage = "Insufficient linear displacement or invalid physical distance input.") }
                    }
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "Calibration analysis failed: ${e.message}") }
        }
    }

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
                    _state.update { it.copy(exportStatus = "Applying to robot over NT4...") }
                    try {
                        nt4ClientService.publishDouble("Tuning/driverDeadbandExponent", intent.recommendedExponent)
                        
                        val slewVal = if (intent.recommendedSlewRate == Double.MAX_VALUE) 999.0 else intent.recommendedSlewRate
                        nt4ClientService.publishDouble("Tuning/driverSlewRateLimit", slewVal)
                        
                        _state.update {
                            it.copy(exportStatus = "Successfully applied! 🎉")
                        }
                    } catch (e: Exception) {
                        _state.update { it.copy(exportStatus = "Failed to apply: ${e.message}") }
                    }
                }
                is SysIdIntent.ClearExportStatus -> {
                    _state.update { it.copy(exportStatus = "") }
                }
                is SysIdIntent.SetMechanism -> {
                    _state.update { it.copy(selectedMechanism = intent.mechanism) }
                }
                is SysIdIntent.StartRoutine -> {
                    dataBuffer.clear()
                    _state.update { it.copy(liveSamples = emptyList(), liveCalibrationData = emptyList(), isRoutineRunning = true, summary = null, isLoading = true) }
                    val cmd = "START_${_state.value.selectedMechanism.name}_${intent.routine.name}"
                    // pubuid 1015 corresponds to "SysId/Command" topic
                    nt4ClientService.publishInputString(1015, cmd)
                }
                is SysIdIntent.StopRoutine -> {
                    _state.update { it.copy(isRoutineRunning = false, isLoading = false) }
                    nt4ClientService.publishInputString(1015, "STOP")
                }
                is SysIdIntent.LoadLocalLogFile -> {
                    _state.update { it.copy(isLoading = true, fileAnalysisError = null, localAnalysisResult = null) }
                    try {
                        val rows = withContext(Dispatchers.IO) {
                            parseLogFile(intent.fileContent)
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
                    dataBuffer.clear()
                    _state.update {
                        it.copy(
                            liveSamples = emptyList(),
                            liveCalibrationData = emptyList(),
                            isRoutineRunning = true,
                            activeCalibration = intent.calibrationType,
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
                    nt4ClientService.publishInputString(1015, "START_${intent.calibrationType}")
                }
                is SysIdIntent.StopCalibration -> {
                    _state.update { it.copy(isRoutineRunning = false, activeCalibration = "NONE", isLoading = false) }
                    nt4ClientService.publishInputString(1015, "STOP")
                }
                is SysIdIntent.SetLinearDriveDistance -> {
                    _state.update { it.copy(linearDriveActualDistanceMeters = intent.distance) }
                }
                is SysIdIntent.ApplyCalibration -> {
                    _state.update { it.copy(exportStatus = "Applying calibration to robot...") }
                    try {
                        when (intent.calibrationType) {
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
        }
    }

    private fun parseLogFile(fileContent: String): List<AlignedDataRow> {
        val rows = mutableListOf<AlignedDataRow>()
        val lines = fileContent.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        
        if (lines.none()) return emptyList()
        
        val firstLine = lines.first()
        if (firstLine.startsWith("{")) {
            // JSONL Format
            for (line in lines) {
                try {
                    val element = Json.parseToJsonElement(line).jsonObject
                    
                    // Scenario A: Check if the log contains the flattened SysId/Data array directly
                    val sysIdData = element["SysId/Data"] ?: element["SysId_Data"] ?: element["sysid_data"]
                    if (sysIdData != null) {
                        val arr = sysIdData.jsonArray.mapNotNull { it.jsonPrimitive.doubleOrNull }
                        if (arr.size >= 5) {
                            rows.add(AlignedDataRow(
                                timestampMs = arr[0].toLong(),
                                voltage = arr[1],
                                velocity = arr[3],
                                accel = arr[4]
                            ))
                            continue
                        }
                    }
                    
                    // Scenario B: Check for individual fields
                    val t = element["TimestampMs"]?.jsonPrimitive?.longOrNull 
                        ?: element["timestamp"]?.jsonPrimitive?.longOrNull 
                        ?: element["time"]?.jsonPrimitive?.longOrNull 
                        ?: continue
                    
                    val volt = element["voltage"]?.jsonPrimitive?.doubleOrNull 
                        ?: element["Voltage"]?.jsonPrimitive?.doubleOrNull 
                        ?: element["Drive/Voltage"]?.jsonPrimitive?.doubleOrNull 
                        ?: continue
                        
                    val vel = element["velocity"]?.jsonPrimitive?.doubleOrNull 
                        ?: element["Velocity"]?.jsonPrimitive?.doubleOrNull 
                        ?: element["Drive/Velocity"]?.jsonPrimitive?.doubleOrNull 
                        ?: continue
                        
                    val accel = element["acceleration"]?.jsonPrimitive?.doubleOrNull 
                        ?: element["Acceleration"]?.jsonPrimitive?.doubleOrNull 
                        ?: element["Drive/Acceleration"]?.jsonPrimitive?.doubleOrNull 
                        ?: 0.0
                        
                    rows.add(AlignedDataRow(t, volt, vel, accel))
                } catch (_: Exception) {}
            }
        } else {
            // CSV Format
            val header = firstLine.split(",").map { it.trim().removeSurrounding("\"").lowercase() }
            val tCol = header.indexOfFirst { it.contains("time") || it == "t" || it == "ts" }
            val voltCol = header.indexOfFirst { it.contains("volt") || it == "v" || it == "u" }
            val velCol = header.indexOfFirst { it.contains("vel") || it.contains("speed") || it == "omega" }
            val accelCol = header.indexOfFirst { it.contains("accel") || it == "a" }
            
            if (voltCol != -1 && velCol != -1) {
                var index = 0
                for (line in lines.drop(1)) {
                    try {
                        val cols = line.split(",").map { it.trim().removeSurrounding("\"") }
                        val t = if (tCol != -1 && tCol < cols.size) cols[tCol].toLongOrNull() ?: index.toLong() else index.toLong()
                        val volt = cols[voltCol].toDoubleOrNull() ?: continue
                        val vel = cols[velCol].toDoubleOrNull() ?: continue
                        val accel = if (accelCol != -1 && accelCol < cols.size) cols[accelCol].toDoubleOrNull() ?: 0.0 else 0.0
                        
                        rows.add(AlignedDataRow(t, volt, vel, accel))
                        index++
                    } catch (_: Exception) {}
                }
            }
        }
        
        // If acceleration is missing (all 0.0), approximate as numerical derivative
        if (rows.isNotEmpty() && rows.all { it.accel == 0.0 }) {
            val approxRows = mutableListOf<AlignedDataRow>()
            val sorted = rows.sortedBy { it.timestampMs }
            for (i in 0 until sorted.size) {
                val current = sorted[i]
                val accel = if (i == 0) 0.0 else {
                    val prev = sorted[i - 1]
                    val dt = (current.timestampMs - prev.timestampMs) / 1000.0
                    if (dt > 1e-4) (current.velocity - prev.velocity) / dt else 0.0
                }
                approxRows.add(current.copy(accel = accel))
            }
            return approxRows
        }
        
        return rows
    }
}
