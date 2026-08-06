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
 * High-performance real-time alert evaluation engine.
 * Detects motor mechanical binding/stalls, disconnected motor cables, low battery brownouts,
 * EKF position drifts, and sensor freezes, triggering audible alerts and high-priority UI overlays.
 */
class AlertEngineService(
    private val databaseService: DatabaseService,
    private val nt4ClientService: Nt4ClientService,
    private val thresholdsPath: String = System.getProperty("user.home") + "/.ares-analytics/thresholds.json"
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val rules = ConcurrentHashMap<String, ThresholdRule>()
    private val recentValues = ConcurrentHashMap<String, Double>()
    private val motorNames = listOf("fl", "fr", "rl", "rr", "bl", "br")

    // Active alert state: AlertId -> AlertRecord
    private val _alerts = MutableStateFlow<Map<String, AlertRecord>>(emptyMap())
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
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val defaults = mutableListOf(
                ThresholdRule("/Drive/Voltage", "Low Battery Voltage (<10.5V)", minValue = 10.5, audibleAlert = true),
                ThresholdRule("/Drive/EkfDrift", "High EKF Position Drift (>0.20m)", maxValue = 0.20, audibleAlert = true),
                ThresholdRule("/LoopTimeMs", "Robot Loop Time Spike (>25ms)", maxValue = 25.0, audibleAlert = false),
                ThresholdRule("/Drive/MotorCurrentMax", "Motor Current Spike (>15A)", maxValue = 15.0, audibleAlert = true)
            )

            // Dynamic rules for all 6 possible motor channels
            motorNames.forEach { motor ->
                defaults.add(ThresholdRule("Hardware/Motors/$motor/Stall", "CRITICAL: Motor '$motor' Mechanical Binding / Stall!", maxValue = 0.5, audibleAlert = true))
                defaults.add(ThresholdRule("Hardware/Motors/$motor/Disconnected", "WARNING: Motor '$motor' Cable Disconnected!", maxValue = 0.5, audibleAlert = true))
            }

            file.writeText(json.encodeToString(defaults))
        }

        try {
            val loaded = json.decodeFromString<List<ThresholdRule>>(file.readText())
            loaded.forEach { rules[it.key] = it }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startEngine() {
        engineJob = CoroutineScope(Dispatchers.Default).launch {
            nt4ClientService.telemetryFlow.collect { frame ->
                recentValues[frame.key] = frame.value
                evaluateFrame(frame)
                evaluateCompositeRules(frame)
            }
        }
    }

    fun stop() {
        engineJob?.cancel()
    }

    /**
     * Standard single-key threshold rule evaluation.
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

        if (isViolating) {
            if (existingAlert == null) {
                val newAlert = AlertRecord(
                    alertId = UUID.randomUUID().toString(),
                    sessionId = frame.sessionId,
                    ruleKey = rule.key,
                    triggerTimestampMs = frame.timestampMs,
                    peakValue = value,
                    triaged = false
                )
                updateAlertState(newAlert)
                if (rule.audibleAlert) {
                    triggerAudibleAlert()
                }
            } else if (existingAlert.resolveTimestampMs != null) {
                val reActive = existingAlert.copy(
                    resolveTimestampMs = null,
                    durationMs = 0L,
                    peakValue = maxOf(existingAlert.peakValue, value)
                )
                updateAlertState(reActive)
                if (rule.audibleAlert) {
                    triggerAudibleAlert()
                }
            } else {
                val updated = existingAlert.copy(
                    peakValue = if (rule.maxValue != null) maxOf(existingAlert.peakValue, value) else minOf(existingAlert.peakValue, value)
                )
                updateAlertState(updated)
            }
        } else {
            if (existingAlert != null && existingAlert.resolveTimestampMs == null) {
                val resolved = existingAlert.copy(
                    resolveTimestampMs = frame.timestampMs,
                    durationMs = frame.timestampMs - existingAlert.triggerTimestampMs
                )
                updateAlertState(resolved)
            }
        }
    }

    /**
     * Evaluates multi-signal composite diagnostic rules (Motor Stalling, Cable Disconnection, etc.).
     */
    private suspend fun evaluateCompositeRules(frame: TelemetryFrame) {
        val ts = frame.timestampMs
        val sessionId = frame.sessionId

        for (m in motorNames) {
            val pwr = kotlin.math.abs(recentValues["Hardware/Motors/$m/Power"] ?: recentValues["Hardware/Motors/$m/Voltage"] ?: 0.0)
            val vel = kotlin.math.abs(recentValues["Hardware/Motors/$m/Velocity"] ?: 0.0)
            val current = recentValues["Hardware/Motors/$m/CurrentAmps"] ?: 0.0

            val stallKey = "Hardware/Motors/$m/Stall"
            val disconnectKey = "Hardware/Motors/$m/Disconnected"

            // 1. Mechanical Binding / Motor Stall Check:
            // High power commanded (>0.35), zero velocity (<5.0 ticks/rad/s), and high current draw (>5.0A)
            val isStalled = pwr > 0.35 && vel < 5.0 && current > 5.0
            val stallRule = rules[stallKey] ?: ThresholdRule(stallKey, "CRITICAL: Motor '$m' Mechanical Binding / Stall!", maxValue = 0.5, audibleAlert = true)
            rules.putIfAbsent(stallKey, stallRule)
            evaluateCustomRule(stallKey, isStalled, if (isStalled) 1.0 else 0.0, ts, sessionId, stallRule)

            // 2. Disconnected Motor Cable / Blown Fuse Check:
            // High power commanded (>0.35), zero velocity (<5.0), but low current draw (<0.1A)
            val isDisconnected = pwr > 0.35 && vel < 5.0 && current < 0.1 && current >= 0.0
            val disconnectRule = rules[disconnectKey] ?: ThresholdRule(disconnectKey, "WARNING: Motor '$m' Cable Disconnected!", maxValue = 0.5, audibleAlert = true)
            rules.putIfAbsent(disconnectKey, disconnectRule)
            evaluateCustomRule(disconnectKey, isDisconnected, if (isDisconnected) 1.0 else 0.0, ts, sessionId, disconnectRule)
        }
    }

    private suspend fun evaluateCustomRule(
        key: String,
        isViolating: Boolean,
        value: Double,
        ts: Long,
        sessionId: String,
        rule: ThresholdRule
    ) {
        val currentMap = _alerts.value
        val existingAlert = currentMap.values.firstOrNull { it.ruleKey == key && !it.triaged }

        if (isViolating) {
            if (existingAlert == null) {
                val newAlert = AlertRecord(
                    alertId = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    ruleKey = key,
                    triggerTimestampMs = ts,
                    peakValue = value,
                    triaged = false
                )
                updateAlertState(newAlert)
                if (rule.audibleAlert) {
                    triggerAudibleAlert()
                }
            } else if (existingAlert.resolveTimestampMs != null) {
                val reActive = existingAlert.copy(
                    resolveTimestampMs = null,
                    durationMs = 0L,
                    peakValue = maxOf(existingAlert.peakValue, value)
                )
                updateAlertState(reActive)
                if (rule.audibleAlert) {
                    triggerAudibleAlert()
                }
            }
        } else {
            if (existingAlert != null && existingAlert.resolveTimestampMs == null) {
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

    suspend fun triageAlert(alertId: String) {
        val alert = _alerts.value[alertId] ?: return
        val triaged = alert.copy(triaged = true)
        updateAlertState(triaged)
    }

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
                try {
                    // Urgent dual-tone beep
                    playBeepTone(1000f, 100)
                    delay(50)
                    playBeepTone(1200f, 150)
                } catch (e: Exception) {
                    e.printStackTrace()
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

    fun getRuleDisplayName(key: String): String {
        return rules[key]?.displayName ?: key
    }
}
