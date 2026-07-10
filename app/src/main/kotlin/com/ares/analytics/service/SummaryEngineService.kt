package com.ares.analytics.service

import com.ares.analytics.shared.Session
import com.ares.analytics.shared.SessionSummary
import com.ares.analytics.shared.TelemetryFrame
import com.ares.analytics.service.AlignedDataRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sign

class SummaryEngineService(
    private val databaseService: DatabaseService,
    private val sysIdService: SysIdService,
    private val driverAnalysisService: DriverAnalysisService
) {

    suspend fun generateSummary(session: Session): SessionSummary = withContext(Dispatchers.Default) {
        val totalFrames = databaseService.countTelemetryFrames(session.sessionId)
        val batchSize = 10000L

        var minBattery = Double.MAX_VALUE
        var maxDrift = 0.0
        val loopTimes = mutableListOf<Double>()
        val motorCurrents = mutableMapOf<String, MutableList<Double>>()
        val visionAcceptanceValues = mutableListOf<Double>()
        
        val crossTrackErrors = mutableListOf<Double>()
        val visionLatencies = mutableListOf<Double>()
        var lastVoltage = 12.0
        val currentDrawsByTime = mutableMapOf<Long, Double>()
        val resistanceEstimates = mutableListOf<Double>()
        val motorTemps = mutableMapOf<String, Double>()
        val maxMotorTemps = mutableMapOf<String, Double>()
        val lastTimeMs = mutableMapOf<String, Long>()

        for (offset in 0L until totalFrames step batchSize) {
            val frames = databaseService.getTelemetryRangeBatched(session.sessionId, 0L, Long.MAX_VALUE, batchSize, offset)
            for (frame in frames) {
                val keyLower = frame.key.lowercase()
                val t = frame.timestampMs

                // 1. Battery Voltage
                if (keyLower.contains("voltage") || keyLower.contains("battery")) {
                    if (frame.value > 1.0) { // filter out zero noise
                        minBattery = minOf(minBattery, frame.value)
                        val drop = lastVoltage - frame.value
                        if (drop > 0.5) {
                            val totalAmps = currentDrawsByTime[t] ?: 0.0
                            if (totalAmps > 5.0) {
                                resistanceEstimates.add(drop / totalAmps)
                            }
                        }
                        lastVoltage = maxOf(lastVoltage, frame.value)
                    }
                }

                // 2. EKF Drift & Path Deviation
                if (keyLower.contains("drift") || keyLower.contains("ekf") || keyLower.contains("poseerror")) {
                    maxDrift = maxOf(maxDrift, frame.value)
                }
                if (keyLower.contains("crosstrack") || keyLower.contains("xte")) {
                    crossTrackErrors.add(frame.value)
                }

                // 3. Loop Time
                if (keyLower.contains("loop") || keyLower.contains("period")) {
                    loopTimes.add(frame.value)
                }

                // 4. Motor Currents & Thermal Prediction
                if (keyLower.contains("current") && !keyLower.contains("battery")) {
                    val cleanName = cleanKeyToDeviceName(frame.key)
                    motorCurrents.getOrPut(cleanName) { mutableListOf() }.add(frame.value)
                    currentDrawsByTime[t] = (currentDrawsByTime[t] ?: 0.0) + frame.value

                    val lastTime = lastTimeMs.getOrDefault(cleanName, t - 20L)
                    val dt = (t - lastTime) / 1000.0
                    if (dt > 0 && dt < 1.0) {
                        val currentTemp = motorTemps.getOrDefault(cleanName, 25.0)
                        val i = frame.value
                        val rWind = 0.05
                        val rTherm = 100.0
                        val newTemp = currentTemp + (i * i * rWind * dt) - ((currentTemp - 25.0) / rTherm * dt)
                        motorTemps[cleanName] = newTemp
                        maxMotorTemps[cleanName] = maxOf(maxMotorTemps.getOrDefault(cleanName, 25.0), newTemp)
                    }
                    lastTimeMs[cleanName] = t
                }

                // 5. Vision Acceptance & Latency
                if (keyLower.contains("vision")) {
                    if (keyLower.contains("acceptance") || keyLower.contains("valid") || keyLower.contains("quality")) {
                        visionAcceptanceValues.add(frame.value)
                    }
                    if (keyLower.contains("latency")) {
                        visionLatencies.add(frame.value)
                    }
                }
            }
        }

        // Finalize stats
        val finalMinBattery = if (minBattery == Double.MAX_VALUE) 12.0 else minBattery
        val avgLoop = if (loopTimes.isNotEmpty()) loopTimes.average() else 0.0
        val p95Loop = if (loopTimes.isNotEmpty()) {
            val sorted = loopTimes.sorted()
            val index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
            sorted[index]
        } else 0.0

        val motorCurrentAverages = motorCurrents.mapValues { (_, values) ->
            if (values.isNotEmpty()) values.average() else 0.0
        }

        val visionRate = if (visionAcceptanceValues.isNotEmpty()) visionAcceptanceValues.average() else 1.0
        val avgCrossTrack = if (crossTrackErrors.isNotEmpty()) crossTrackErrors.map { kotlin.math.abs(it) }.average() else 0.0
        val avgResistance = if (resistanceEstimates.isNotEmpty()) resistanceEstimates.average() else 0.0
        val avgVisionLat = if (visionLatencies.isNotEmpty()) visionLatencies.average() else 0.0

        val summary = SessionSummary(
            sessionId = session.sessionId,
            teamId = session.teamId,
            seasonId = session.seasonId,
            robotId = session.robotId,
            createdAt = session.createdAt,
            durationMs = session.durationMs,
            minBatteryVoltage = finalMinBattery,
            maxEkfDrift = maxDrift,
            avgLoopTimeMs = avgLoop,
            p95LoopTimeMs = p95Loop,
            motorCurrentAverages = motorCurrentAverages,
            visionAcceptanceRate = visionRate,
            avgCrossTrackError = avgCrossTrack,
            avgBatteryResistance = avgResistance,
            maxMotorTemps = maxMotorTemps,
            avgVisionLatencyMs = avgVisionLat,
            tags = session.tags,
            matchNumber = session.matchNumber,
            allianceColor = session.allianceColor
        )

        databaseService.insertSessionSummary(summary)
        calculateAndSaveDiagnostics(session)
        summary
    }

    private suspend fun calculateAndSaveDiagnostics(session: Session) {
        try {
            val allFrames = databaseService.getTelemetryRange(session.sessionId, 0L, Long.MAX_VALUE)
            if (allFrames.isEmpty()) return

            val framesToInsert = mutableListOf<TelemetryFrame>()

            // Loop Overruns and Comms Losses calculation
            val loopTimes = allFrames.filter { it.key.lowercase().contains("loop") || it.key.lowercase().contains("period") }.map { it.value }
            val loopOverruns = loopTimes.count { it > 40.0 }

            val sortedTimes = allFrames.map { it.timestampMs }.distinct().sorted()
            var commsLosses = 0
            if (sortedTimes.size > 1) {
                for (i in 0 until sortedTimes.size - 1) {
                    val gap = sortedTimes[i + 1] - sortedTimes[i]
                    if (gap > 1000) {
                        commsLosses++
                    }
                }
            }

            val minVoltage = allFrames.filter { it.key.lowercase().contains("battery") && it.key.lowercase().contains("volt") }
                .map { it.value }
                .minOrNull() ?: 12.0

            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/System/LoopOverruns", loopOverruns.toDouble()))
            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/System/CommsLosses", commsLosses.toDouble()))

            // CANbus status, motor faults, and brownout calculations
            val canFrames = allFrames.filter { it.key.startsWith("Diagnostics/CAN/") }
            val maxBusUtil = canFrames.filter { it.key.endsWith("BusUtilization") }.maxOfOrNull { it.value } ?: 0.0
            val totalErrorCount = canFrames.filter { it.key.endsWith("ErrorCount") }.maxOfOrNull { it.value } ?: 0.0
            val totalBusOffs = canFrames.filter { it.key.endsWith("BusOffs") }.maxOfOrNull { it.value } ?: 0.0
            val maxSignalLatency = canFrames.filter { it.key.endsWith("SignalLatencyMs") }.maxOfOrNull { it.value } ?: 0.0

            val brownoutCount = allFrames.filter { it.key == "Diagnostics/Power/BrownoutCount" }.maxOfOrNull { it.value } ?: 0.0

            val motorFaultFrames = allFrames.filter { it.key.startsWith("Diagnostics/Motor/") && it.key.endsWith("/Faults") }
            val hasMotorFaults = motorFaultFrames.any { it.value > 0.0 }

            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/System/MaxCANBusUtilization", maxBusUtil))
            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/System/TotalCANBusErrors", totalErrorCount))
            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/System/CANBusOffs", totalBusOffs))
            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/System/MaxCANBusLatencyMs", maxSignalLatency))
            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/System/BrownoutCount", brownoutCount))

            val newTags = session.tags.toMutableList()
            if (commsLosses > 0) newTags.add("CommsLoss")
            if (loopOverruns > 5) newTags.add("LoopOverruns")
            if (minVoltage < 9.5 && minVoltage > 0.0) newTags.add("LowBattery")
            if (totalErrorCount > 0.0 || totalBusOffs > 0.0) newTags.add("CANBusFault")
            if (maxBusUtil >= 0.90) newTags.add("CANBusSaturated")
            if (brownoutCount > 0.0) newTags.add("Brownout")
            if (hasMotorFaults) newTags.add("MotorFault")

            val uniqueTags = newTags.distinct()
            if (uniqueTags.size != session.tags.size) {
                databaseService.updateSessionTags(session.sessionId, uniqueTags)
            }

            // 1. Drivetrain SysId Characterization
            val voltages = allFrames.filter { it.key == "/Drive/Voltage" || it.key == "Drive/Voltage" }
            val velocities = allFrames.filter { it.key == "/Drive/Velocity" || it.key == "Drive/Velocity" }
            val accelerations = allFrames.filter { it.key == "/Drive/Acceleration" || it.key == "Drive/Acceleration" }

            if (voltages.isNotEmpty() && velocities.isNotEmpty()) {
                val alignedData = mutableListOf<AlignedDataRow>()
                val timeMap = voltages.associateBy { it.timestampMs }

                val directionChanges = mutableListOf<Long>()
                var lastSign = 0.0
                val sortedVelocities = velocities.sortedBy { it.timestampMs }
                for (v in sortedVelocities) {
                    val currentSign = sign(v.value)
                    if (currentSign != 0.0 && currentSign != lastSign) {
                        directionChanges.add(v.timestampMs)
                        lastSign = currentSign
                    }
                }

                val sortedAccels = accelerations.sortedBy { it.timestampMs }
                var accelIdx = 0

                for (v in sortedVelocities) {
                    val t = v.timestampMs
                    val isNearDirectionChange = directionChanges.any { abs(it - t) <= 50 }
                    if (isNearDirectionChange) continue

                    val volt = timeMap[t]?.value ?: continue

                    val accel = if (sortedAccels.isNotEmpty()) {
                        while (accelIdx < sortedAccels.size - 1 &&
                            abs(sortedAccels[accelIdx + 1].timestampMs - t) <= abs(sortedAccels[accelIdx].timestampMs - t)
                        ) {
                            accelIdx++
                        }
                        sortedAccels[accelIdx].value
                    } else 0.0

                    alignedData.add(AlignedDataRow(t, volt, v.value, accel))
                }

                val finalAlignedData = if (alignedData.isNotEmpty() && alignedData.all { it.accel == 0.0 }) {
                    val approxRows = mutableListOf<AlignedDataRow>()
                    val sorted = alignedData.sortedBy { it.timestampMs }
                    for (i in 0 until sorted.size) {
                        val current = sorted[i]
                        val accel = if (i == 0) 0.0 else {
                            val prev = sorted[i - 1]
                            val dt = (current.timestampMs - prev.timestampMs) / 1000.0
                            if (dt > 1e-4) (current.velocity - prev.velocity) / dt else 0.0
                        }
                        approxRows.add(current.copy(accel = accel))
                    }
                    approxRows
                } else {
                    alignedData
                }

                if (finalAlignedData.size >= 10) {
                    val summary = sysIdService.analyzeRawData(finalAlignedData)
                    if (summary.rSquared > 0.1) {
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/kS", summary.kS))
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/kV", summary.kV))
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/kA", summary.kA))
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/R2", summary.rSquared))

                        if (summary.kA > 1e-6) {
                            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/ADRC_b0", 1.0 / summary.kA))
                        }
                    }
                }
            }

            // 2. Individual Subsystem & Motor SysId Characterization
            val motorVoltages = mutableMapOf<String, MutableMap<Long, Double>>()
            val motorVelocities = mutableMapOf<String, MutableMap<Long, Double>>()

            for (frame in allFrames) {
                val cleanKey = frame.key.removePrefix("/")
                if (cleanKey.startsWith("Hardware/Motors/")) {
                    val parts = cleanKey.split("/")
                    if (parts.size >= 4) {
                        val motorName = parts[2]
                        val metric = parts[3].lowercase()
                        val t = frame.timestampMs
                        when {
                            metric.contains("volt") || metric.contains("power") -> {
                                val voltVal = if (metric.contains("power") && abs(frame.value) <= 1.0) frame.value * 12.0 else frame.value
                                motorVoltages.getOrPut(motorName) { mutableMapOf() }[t] = voltVal
                            }
                            metric.contains("vel") || metric.contains("speed") -> {
                                motorVelocities.getOrPut(motorName) { mutableMapOf() }[t] = frame.value
                            }
                        }
                    }
                }
            }

            for ((motorName, velocitiesMap) in motorVelocities) {
                val voltagesMap = motorVoltages[motorName] ?: continue
                if (velocitiesMap.size < 10 || voltagesMap.size < 10) continue

                val alignedRows = mutableListOf<AlignedDataRow>()
                val sortedTimes = velocitiesMap.keys.sorted()

                var lastTime = 0L
                var lastVel = 0.0

                for (t in sortedTimes) {
                    val vel = velocitiesMap[t] ?: continue
                    val volt = voltagesMap[t] ?: continue

                    val accel = if (lastTime == 0L) 0.0 else {
                        val dt = (t - lastTime) / 1000.0
                        if (dt > 1e-4) (vel - lastVel) / dt else 0.0
                    }

                    alignedRows.add(AlignedDataRow(t, volt, vel, accel))
                    lastTime = t
                    lastVel = vel
                }

                if (alignedRows.size >= 10) {
                    val summary = sysIdService.analyzeRawData(alignedRows)
                    if (summary.rSquared > 0.5) {
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Motors/$motorName/kS", summary.kS))
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Motors/$motorName/kV", summary.kV))
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Motors/$motorName/kA", summary.kA))
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Motors/$motorName/R2", summary.rSquared))

                        if (summary.kA > 1e-6) {
                            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Motors/$motorName/ADRC_b0", 1.0 / summary.kA))
                        }
                    }

                    // Estimate kG (Gravity Feedforward) for vertical elevators/arms (non-drivetrain)
                    val isDrivetrain = motorName.lowercase() in listOf("fl", "fr", "rl", "rr", "bl", "br", "frontleft", "frontright", "rearleft", "rearright")
                    if (!isDrivetrain) {
                        val holdingVoltages = alignedRows.filter { row ->
                            val absV = if (row.velocity < 0.0) -row.velocity else row.velocity
                            val absA = if (row.accel < 0.0) -row.accel else row.accel
                            absV < 0.05 && absA < 0.1
                        }.map { it.voltage }
                        if (holdingVoltages.size >= 10) {
                            val kgEstimate = holdingVoltages.average()
                            val absKg = if (kgEstimate < 0.0) -kgEstimate else kgEstimate
                            if (absKg > 0.1) {
                                framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Motors/$motorName/kG", kgEstimate))
                            }
                        }
                    }
                }
            }

            // 3. Drivetrain Angular Characterization
            val flVolts = motorVoltages["fl"] ?: motorVoltages["FL"] ?: motorVoltages["frontleft"]
            val rlVolts = motorVoltages["rl"] ?: motorVoltages["RL"] ?: motorVoltages["bl"] ?: motorVoltages["BL"] ?: motorVoltages["rearleft"]
            val frVolts = motorVoltages["fr"] ?: motorVoltages["FR"] ?: motorVoltages["frontright"]
            val rrVolts = motorVoltages["rr"] ?: motorVoltages["RR"] ?: motorVoltages["br"] ?: motorVoltages["BR"] ?: motorVoltages["rearright"]

            val leftSideVolts = mutableMapOf<Long, Double>()
            val rightSideVolts = mutableMapOf<Long, Double>()

            if (flVolts != null) {
                for ((t, v) in flVolts) leftSideVolts[t] = (leftSideVolts[t] ?: 0.0) + v * 0.5
            }
            if (rlVolts != null) {
                for ((t, v) in rlVolts) leftSideVolts[t] = (leftSideVolts[t] ?: 0.0) + v * 0.5
            }
            if (frVolts != null) {
                for ((t, v) in frVolts) rightSideVolts[t] = (rightSideVolts[t] ?: 0.0) + v * 0.5
            }
            if (rrVolts != null) {
                for ((t, v) in rrVolts) rightSideVolts[t] = (rightSideVolts[t] ?: 0.0) + v * 0.5
            }

            val angularVoltages = mutableMapOf<Long, Double>()
            for (t in leftSideVolts.keys) {
                val lv = leftSideVolts[t] ?: continue
                val rv = rightSideVolts[t] ?: continue
                angularVoltages[t] = lv - rv
            }

            val omegas = allFrames.filter { it.key == "Drive/Velocity_Omega" || it.key == "/Drive/Velocity_Omega" }
            if (angularVoltages.isNotEmpty() && omegas.isNotEmpty()) {
                val alignedAngData = mutableListOf<AlignedDataRow>()
                val sortedOmegas = omegas.sortedBy { it.timestampMs }

                var lastTime = 0L
                var lastOmega = 0.0

                for (o in sortedOmegas) {
                    val t = o.timestampMs
                    val volt = angularVoltages[t] ?: continue

                    val accel = if (lastTime == 0L) 0.0 else {
                        val dt = (t - lastTime) / 1000.0
                        if (dt > 1e-4) (o.value - lastOmega) / dt else 0.0
                    }

                    alignedAngData.add(AlignedDataRow(t, volt, o.value, accel))
                    lastTime = t
                    lastOmega = o.value
                }

                if (alignedAngData.size >= 10) {
                    val angSummary = sysIdService.analyzeRawData(alignedAngData)
                    if (angSummary.rSquared > 0.1) {
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Angular/kS", angSummary.kS))
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Angular/kV", angSummary.kV))
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Angular/kA", angSummary.kA))
                        framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Angular/R2", angSummary.rSquared))
                        if (angSummary.kA > 1e-6) {
                            framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/SysId/Angular/ADRC_b0", 1.0 / angSummary.kA))
                        }
                    }
                }
            }

            // 4. Wheel Slippage / Traction Loss Calculation
            val ekfVels = allFrames.filter { it.key == "Drive/Velocity" || it.key == "/Drive/Velocity" }
            if (ekfVels.isNotEmpty() && motorVelocities.isNotEmpty()) {
                val slippages = mutableListOf<Double>()
                for (ev in ekfVels) {
                    val t = ev.timestampMs
                    val ekfV = abs(ev.value)

                    var wheelSum = 0.0
                    var wheelCount = 0
                    for (motorName in listOf("fl", "fr", "rl", "rr", "bl", "br")) {
                        val mVel = motorVelocities[motorName]?.get(t) ?: continue
                        wheelSum += abs(mVel)
                        wheelCount++
                    }

                    if (wheelCount > 0) {
                        val avgWheelV = wheelSum / wheelCount
                        val diff = abs(avgWheelV - ekfV)
                        val denominator = maxOf(ekfV, 0.1)
                        slippages.add(diff / denominator)
                    }
                }
                if (slippages.isNotEmpty()) {
                    framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/Drive/TractionLoss", slippages.average()))
                }
            }

            // 5. Driver Jitter Analysis
            val j = driverAnalysisService.analyzeDriverJitter(session.sessionId)
            if (j.peakFrequencyHz > 0.1) {
                framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/Driver/RecommendedExponent", j.recommendedExponent))
                framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/Driver/RecommendedSlewRate", if (j.recommendedSlewRate == Double.MAX_VALUE) 999.0 else j.recommendedSlewRate))
                framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/Driver/PeakJitterFrequency", j.peakFrequencyHz))
                framesToInsert.add(TelemetryFrame(session.createdAt, session.sessionId, "Diagnostics/Driver/JitterPresent", if (j.hasJitter) 1.0 else 0.0))
            }

            if (framesToInsert.isNotEmpty()) {
                databaseService.insertTelemetryFrames(framesToInsert)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanKeyToDeviceName(key: String): String {
        // e.g. "/Drive/MotorFL/Current" -> "MotorFL"
        // e.g. "Drive/Motors/FrontLeftCurrent" -> "FrontLeft"
        val parts = key.split("/")
        if (parts.size >= 2) {
            val last = parts.last()
            if (last.lowercase() == "current" || last.lowercase() == "amps") {
                return parts[parts.size - 2]
            }
        }
        val lastPart = parts.last()
        return lastPart
            .replace("current", "", ignoreCase = true)
            .replace("amps", "", ignoreCase = true)
            .replace("/", "")
            .ifEmpty { "Motor" }
    }
}
