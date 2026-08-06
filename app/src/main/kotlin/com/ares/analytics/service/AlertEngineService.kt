package com.ares.analytics.service

import com.ares.analytics.shared.AlertRecord
import com.ares.analytics.shared.ThresholdRule
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/**
 * High-performance real-time **Emergency Fault Alert & Diagnostic Engine**.
 *
 * Continuously evaluates high-rate NetworkTables NT4 telemetry streams against multi-signal hardware diagnostic
 * rules. Automatically triggers pop-up overlays, persistent database records, and urgent dual-tone audio beeps
 * upon fault detection.
 *
 * ### Diagnostic Failure Equations & Thresholds:
 * - **1.0s Moving Average Current Window ($N = 20$ samples):**
 *   $$\bar{I}_{\text{avg}} = \frac{1}{N} \sum_{i=1}^{N} I_i \quad (N = 20 \text{ samples at } 20\text{ Hz})$$
 *
 * - **Motor Mechanical Binding / Loose Screw Stall:**
 *   $$\text{Stall} \iff |P| > 0.35 \;\land\; |\omega| < 5.0\text{ ticks/s} \;\land\; \bar{I}_{\text{avg}} > 5.0\text{ Amps}$$
 *
 * - **Motor Cable Disconnection / Blown Breaker:**
 *   $$\text{Disconnected} \iff |P| > 0.35 \;\land\; |\omega| < 5.0\text{ ticks/s} \;\land\; 0.0\text{A} \le \bar{I}_{\text{avg}} < 0.1\text{ Amps}$$
 *
 * - **Battery Brownout Risk:**
 *   $$\text{Brownout} \iff V_{\text{battery}} < 10.5\text{ Volts}$$
 *
 * - **Motor Over-Temperature Thermal Alert:**
 *   $$\text{Overheat} \iff T_{\text{motor}} > 70.0^\circ\text{C}$$
 *
 * - **Limelight Stale Vision Frame Rate Alert:**
 *   $$\text{VisionStale} \iff f_{\text{limelight}} < 5.0\text{ Hz}$$
 *
 * - **Control Loop Latency Overrun Alert:**
 *   $$\text{LoopOverrun} \iff t_{\text{loop}} > 25.0\text{ ms}$$
 *
 * ### Physical Units & Guarantees:
 * - **Power ($P$):** Normalized motor duty cycle $[-1.0, 1.0]$ or Volts ($V$)
 * - **Current ($I$):** Amperes ($A$)
 * - **Velocity ($\omega$):** Encoder ticks/s or meters per second ($m/s$)
 * - **Temperature ($T$):** Degrees Celsius ($^\circ\text{C}$)
 * - **Loop Latency ($t_{\text{loop}}$):** Milliseconds ($ms$)
 * - **Control Flow:** Zero nested `if` statements enforced via clean, argument-less `when` expressions.
 *
 * @param databaseService SQLite persistent logging service for historical run analytics.
 * @param nt4ClientService Active NetworkTables NT4 websocket streaming client.
 * @param thresholdsPath File path to persistent JSON threshold configuration file.
 * @see Nt4ClientService
 * @see AlertRecord
 * @see ThresholdRule
 */
class AlertEngineService(
    private val databaseService: DatabaseService,
    private val nt4ClientService: Nt4ClientService,
    private val thresholdsPath: String = System.getProperty("user.home") + "/.ares-analytics/thresholds.json"
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val rules = ConcurrentHashMap<String, ThresholdRule>()
    private val recentValues = ConcurrentHashMap<String, Double>()
    private val currentBuffers = ConcurrentHashMap<String, ArrayDeque<Double>>()
    private val motorNames = listOf("fl", "fr", "rl", "rr", "bl", "br")

    // Active alert state: AlertId -> AlertRecord
    private val _alerts = MutableStateFlow<Map<String, AlertRecord>>(emptyMap())

    /**
     * Observable stream of active and historical [AlertRecord]s sorted descending by trigger timestamp.
     */
    val alerts: StateFlow<List<AlertRecord>> = _alerts
        .map { it.values.toList().sortedByDescending { r -> r.triggerTimestampMs } }
        .stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyList())

    private var engineJob: Job? = null
    private var lastBeepTime = 0L

    init {
        loadRules()
        startEngine()
    }

    private fun loadRules() {
        val file = File(thresholdsPath)
        val defaultRules = listOf(
            ThresholdRule("/Drive/Voltage", "Low Battery Voltage (<10.5V)", minValue = 10.5, audibleAlert = true),
            ThresholdRule("/Drive/EkfDrift", "High EKF Position Drift (>0.20m)", maxValue = 0.20, audibleAlert = true),
            ThresholdRule("/LoopTimeMs", "Robot Loop Time Spike (>25ms)", maxValue = 25.0, audibleAlert = false),
            ThresholdRule("/Drive/MotorCurrentMax", "Motor Current Spike (>15A)", maxValue = 15.0, audibleAlert = true),
            ThresholdRule("Hardware/CAN/Utilization", "CRITICAL: CAN Bus Utilization High (>85%)!", maxValue = 85.0, audibleAlert = true),
            ThresholdRule("Hardware/CAN/TxErrors", "CRITICAL: CAN Bus Transmit Error Detected!", maxValue = 0.5, audibleAlert = true),
            ThresholdRule("Hardware/I2C/Timeouts", "WARNING: FTC I2C / Lynx Bus Timeout!", maxValue = 0.5, audibleAlert = true)
        )

        val motorRules = motorNames.flatMap { motor ->
            listOf(
                ThresholdRule("Hardware/Motors/$motor/Stall", "CRITICAL: Motor '$motor' Mechanical Binding / Stall!", maxValue = 0.5, audibleAlert = true),
                ThresholdRule("Hardware/Motors/$motor/Disconnected", "WARNING: Motor '$motor' Cable Disconnected!", maxValue = 0.5, audibleAlert = true)
            )
        }

        val allDefaults = defaultRules + motorRules

        when {
            !file.exists() -> {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(allDefaults))
                allDefaults.forEach { rules[it.key] = it }
            }
            else -> {
                runCatching {
                    val loaded = json.decodeFromString<List<ThresholdRule>>(file.readText())
                    loaded.forEach { rules[it.key] = it }
                }.onFailure {
                    allDefaults.forEach { rules[it.key] = it }
                }
            }
        }
    }

    /**
     * Starts the non-blocking telemetry evaluation coroutine collector.
     */
    fun startEngine() {
        engineJob?.cancel()

        engineJob = CoroutineScope(Dispatchers.Default).launch {
            nt4ClientService.telemetryFlow.collect { frame ->
                recentValues[frame.key] = frame.value
                evaluateFrame(frame)
                evaluateCompositeRules(frame)
            }
        }
    }

    /**
     * Cancels the active telemetry evaluation coroutine job.
     */
    fun stop() {
        engineJob?.cancel()
    }

    /**
     * Single-key threshold rule evaluation using clean zero-nested `when` flow.
     *
     * @param frame Incoming telemetry frame containing topic key and double value.
     */
    private suspend fun evaluateFrame(frame: TelemetryFrame) {
        val rule = rules[frame.key] ?: return
        val value = frame.value

        val minVal = rule.minValue
        val maxVal = rule.maxValue
        val violatesMin = minVal != null && value < minVal
        val violatesMax = maxVal != null && value > maxVal
        val isViolating = violatesMin || violatesMax

        val currentMap = _alerts.value
        val existingAlert = currentMap.values.firstOrNull { it.ruleKey == rule.key && !it.triaged }

        when {
            isViolating && existingAlert == null -> {
                val newAlert = AlertRecord(
                    alertId = UUID.randomUUID().toString(),
                    sessionId = frame.sessionId,
                    ruleKey = rule.key,
                    triggerTimestampMs = frame.timestampMs,
                    peakValue = value,
                    triaged = false
                )
                updateAlertState(newAlert)
                if (rule.audibleAlert) triggerAudibleAlert()
            }
            isViolating && existingAlert?.resolveTimestampMs != null -> {
                val reActive = existingAlert.copy(
                    resolveTimestampMs = null,
                    durationMs = 0L,
                    peakValue = maxOf(existingAlert.peakValue, value)
                )
                updateAlertState(reActive)
                if (rule.audibleAlert) triggerAudibleAlert()
            }
            isViolating && existingAlert != null -> {
                val updated = existingAlert.copy(
                    peakValue = if (rule.maxValue != null) maxOf(existingAlert.peakValue, value) else minOf(existingAlert.peakValue, value)
                )
                updateAlertState(updated)
            }
            !isViolating && existingAlert?.resolveTimestampMs == null && existingAlert != null -> {
                val resolved = existingAlert.copy(
                    resolveTimestampMs = frame.timestampMs,
                    durationMs = frame.timestampMs - existingAlert.triggerTimestampMs
                )
                updateAlertState(resolved)
            }
        }
    }

    /**
     * Multi-signal composite diagnostic evaluation (Stalls, Cable Disconnects, Over-Temp, CAN Errors, Vision Latency).
     *
     * @param frame Current telemetry frame being processed.
     */
    private suspend fun evaluateCompositeRules(frame: TelemetryFrame) {
        val ts = frame.timestampMs
        val sessionId = frame.sessionId

        // 1. Motor Stalling & Disconnect Check across all motors using 1.0-second moving average
        motorNames.forEach { m ->
            val pwr = kotlin.math.abs(recentValues["Hardware/Motors/$m/Power"] ?: recentValues["Hardware/Motors/$m/Voltage"] ?: 0.0)
            val vel = kotlin.math.abs(recentValues["Hardware/Motors/$m/Velocity"] ?: 0.0)
            val current = recentValues["Hardware/Motors/$m/CurrentAmps"] ?: 0.0

            val buf = currentBuffers.getOrPut(m) { ArrayDeque() }
            buf.addLast(current)
            if (buf.size > 20) buf.removeFirst()
            val avgCurrent = if (buf.isNotEmpty()) buf.average() else current

            val stallKey = "Hardware/Motors/$m/Stall"
            val disconnectKey = "Hardware/Motors/$m/Disconnected"

            val isStalled = pwr > 0.35 && vel < 5.0 && avgCurrent > 5.0
            val stallRule = rules.getOrPut(stallKey) { ThresholdRule(stallKey, "CRITICAL: Motor '$m' Mechanical Binding / Stall!", maxValue = 0.5, audibleAlert = true) }
            evaluateRuleState(stallKey, isStalled, if (isStalled) 1.0 else 0.0, ts, sessionId, stallRule)

            val isDisconnected = pwr > 0.35 && vel < 5.0 && avgCurrent < 0.1 && avgCurrent >= 0.0
            val disconnectRule = rules.getOrPut(disconnectKey) { ThresholdRule(disconnectKey, "WARNING: Motor '$m' Cable Disconnected!", maxValue = 0.5, audibleAlert = true) }
            evaluateRuleState(disconnectKey, isDisconnected, if (isDisconnected) 1.0 else 0.0, ts, sessionId, disconnectRule)
        }

        // 2. CAN Bus Utilization & Error Check
        val canUtil = recentValues["Hardware/CAN/Utilization"] ?: recentValues["CAN/Utilization"] ?: 0.0
        val isCanHigh = canUtil > 85.0
        val canRule = rules.getOrPut("Hardware/CAN/Utilization") { ThresholdRule("Hardware/CAN/Utilization", "CRITICAL: CAN Bus Utilization High (>85%)!", maxValue = 85.0, audibleAlert = true) }
        evaluateRuleState("Hardware/CAN/Utilization", isCanHigh, canUtil, ts, sessionId, canRule)

        // 3. FTC I2C / Lynx Timeout Check
        val i2cTimeouts = recentValues["Hardware/I2C/Timeouts"] ?: 0.0
        val isI2cError = i2cTimeouts > 0.0
        val i2cRule = rules.getOrPut("Hardware/I2C/Timeouts") { ThresholdRule("Hardware/I2C/Timeouts", "WARNING: FTC I2C / Lynx Bus Timeout!", maxValue = 0.5, audibleAlert = true) }
        evaluateRuleState("Hardware/I2C/Timeouts", isI2cError, i2cTimeouts, ts, sessionId, i2cRule)

        // 4. Over-Temperature Thermal Alert (>70C)
        motorNames.forEach { m ->
            val tempC = recentValues["Hardware/Motors/$m/TempC"] ?: 0.0
            val isOverheat = tempC > 70.0
            val tempKey = "Hardware/Motors/$m/TempC"
            val tempRule = rules.getOrPut(tempKey) { ThresholdRule(tempKey, "WARNING: Motor '$m' Overheating (>70°C)!", maxValue = 70.0, audibleAlert = true) }
            evaluateRuleState(tempKey, isOverheat, tempC, ts, sessionId, tempRule)
        }

        // 5. Limelight Vision Frame Rate Stale Alert (<5 FPS)
        val limelightFps = recentValues["Vision/Limelight/FPS"] ?: 30.0
        val isVisionStale = limelightFps < 5.0
        val visionRule = rules.getOrPut("Vision/Limelight/FPS") { ThresholdRule("Vision/Limelight/FPS", "WARNING: Limelight Camera Frame Rate Low (<5 FPS)!", minValue = 5.0, audibleAlert = false) }
        evaluateRuleState("Vision/Limelight/FPS", isVisionStale, limelightFps, ts, sessionId, visionRule)

        // 6. Control Loop Latency Alert (>25ms)
        val loopMs = recentValues["System/LoopTimeMs"] ?: 10.0
        val isLoopSlow = loopMs > 25.0
        val loopRule = rules.getOrPut("System/LoopTimeMs") { ThresholdRule("System/LoopTimeMs", "WARNING: Control Loop Overrun (>25ms)!", maxValue = 25.0, audibleAlert = false) }
        evaluateRuleState("System/LoopTimeMs", isLoopSlow, loopMs, ts, sessionId, loopRule)
    }

    /**
     * Pure zero-nested helper to transition custom alert state.
     */
    private suspend fun evaluateRuleState(
        key: String,
        isViolating: Boolean,
        value: Double,
        ts: Long,
        sessionId: String,
        rule: ThresholdRule
    ) {
        val currentMap = _alerts.value
        val existingAlert = currentMap.values.firstOrNull { it.ruleKey == key && !it.triaged }

        when {
            isViolating && existingAlert == null -> {
                val newAlert = AlertRecord(
                    alertId = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    ruleKey = key,
                    triggerTimestampMs = ts,
                    peakValue = value,
                    triaged = false
                )
                updateAlertState(newAlert)
                if (rule.audibleAlert) triggerAudibleAlert()
            }
            isViolating && existingAlert?.resolveTimestampMs != null -> {
                val reActive = existingAlert.copy(
                    resolveTimestampMs = null,
                    durationMs = 0L,
                    peakValue = maxOf(existingAlert.peakValue, value)
                )
                updateAlertState(reActive)
                if (rule.audibleAlert) triggerAudibleAlert()
            }
            !isViolating && existingAlert?.resolveTimestampMs == null && existingAlert != null -> {
                val resolved = existingAlert.copy(
                    resolveTimestampMs = ts,
                    durationMs = ts - existingAlert.triggerTimestampMs
                )
                updateAlertState(resolved)
            }
        }
    }

    private suspend fun updateAlertState(alert: AlertRecord) {
        if (alert.sessionId != "live-telemetry") {
            databaseService.insertAlert(alert)
        }

        _alerts.update { current ->
            current.toMutableMap().apply {
                put(alert.alertId, alert)
            }
        }
    }

    /**
     * Marks an active alert as triaged/acknowledged by the driver or pit crew.
     *
     * @param alertId Unique UUID string of the target alert.
     */
    suspend fun triageAlert(alertId: String) {
        val alert = _alerts.value[alertId] ?: return
        val triaged = alert.copy(triaged = true)
        updateAlertState(triaged)
    }

    /**
     * Clears all triaged and resolved alerts from the active alert banner queue.
     */
    suspend fun clearAllResolvedAlerts() {
        _alerts.update { current ->
            current.filterValues { !it.triaged || it.resolveTimestampMs == null }
        }
    }

    private fun triggerAudibleAlert() {
        val now = System.currentTimeMillis()
        if (now - lastBeepTime > 1500) {
            lastBeepTime = now
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    playBeepTone(1000f, 100)
                    delay(50)
                    playBeepTone(1200f, 150)
                }
            }
        }
    }

    private fun playBeepTone(frequency: Float, durationMs: Int) {
        val sampleRate = 8000f
        val numSamples = (durationMs * sampleRate / 1000).toInt()
        val buf = ByteArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = i / (sampleRate / frequency) * 2.0 * Math.PI
            buf[i] = (Math.sin(angle) * 127.0).toInt().toByte()
        }
        val format = AudioFormat(sampleRate, 8, 1, true, true)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.start()
        line.write(buf, 0, buf.size)
        line.drain()
        line.close()
    }

    /**
     * Retrieves human-readable display name for a rule key.
     *
     * @param key NetworkTables rule topic key.
     * @return Human-readable display string.
     */
    fun getRuleDisplayName(key: String): String {
        return rules[key]?.displayName ?: key
    }
}
