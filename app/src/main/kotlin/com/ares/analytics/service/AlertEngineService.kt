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

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class AlertEngineService(
    private val databaseService: DatabaseService,
    private val nt4ClientService: Nt4ClientService,
    private val thresholdsPath: String = System.getProperty("user.home") + "/.ares-analytics/thresholds.json"
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val rules = ConcurrentHashMap<String, ThresholdRule>()

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
            // Write defaults
            file.parentFile?.mkdirs()
            val defaults = listOf(
                ThresholdRule("/Drive/Voltage", "Low Battery Voltage", minValue = 11.5, audibleAlert = true),
                ThresholdRule("/Drive/EkfDrift", "High EKF Position Drift", maxValue = 0.20, audibleAlert = true),
                ThresholdRule("/LoopTimeMs", "Robot Loop Time Spike", maxValue = 25.0, audibleAlert = false),
                ThresholdRule("/Drive/MotorCurrentMax", "Motor Current Draw Spike", maxValue = 25.0, audibleAlert = true)
            )
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
                evaluateFrame(frame)
            }
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun stop() {
        engineJob?.cancel()
    }

    private suspend fun evaluateFrame(frame: TelemetryFrame) {
        val rule = rules[frame.key] ?: return
        val value = frame.value

        // Check if value violates rules
        val minVal = rule.minValue
        val maxVal = rule.maxValue
        val violatesMin = minVal != null && value < minVal
        val violatesMax = maxVal != null && value > maxVal
        val isViolating = violatesMin || violatesMax
        val currentMap = _alerts.value
        // Find if we already have an active alert for this rule key
        val existingAlert = currentMap.values.firstOrNull { it.ruleKey == rule.key && !it.triaged }

        if (isViolating) {
            when {
                existingAlert == null -> {
                    // Trigger new active alert
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
                }
                existingAlert.resolveTimestampMs != null -> {
                    // It was resolved but not triaged, and now it's active again -> re-activate
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
                else -> {
                    // Update peak value of current active alert
                    val updated = existingAlert.copy(
                        peakValue = if (rule.maxValue != null) maxOf(existingAlert.peakValue, value) else minOf(existingAlert.peakValue, value)
                    )
                    updateAlertState(updated)
                }
            }
        } else {
            // Value is normal. If there is an active alert that isn't resolved yet -> mark resolved (but still latched)
            if (existingAlert != null && existingAlert.resolveTimestampMs == null) {
                val resolved = existingAlert.copy(
                    resolveTimestampMs = frame.timestampMs,
                    durationMs = frame.timestampMs - existingAlert.triggerTimestampMs
                )
                updateAlertState(resolved)
            }
        }
    }

    private suspend fun updateAlertState(alert: AlertRecord) {
        // Write to DB if there's a recording session
        if (alert.sessionId != "live-telemetry") {
            databaseService.insertAlert(alert)
        }

        // Update in-memory Map
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
        if (now - lastBeepTime > 2000) {
            lastBeepTime = now
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    playBeepTone(880f, 150)
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
}
