package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import com.ares.analytics.shared.RobotActionRecord
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.serialization.json.*
import java.io.File

/**
 * Metadata extracted from an action log file's envelope fields.
 */
data class ActionLogMetadata(
    val durationMs: Long,
    val matchNumber: Int,
    val alliance: String
)

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class JsonlLogDecoder(private val databaseService: DatabaseService) {

    suspend fun parseJsonlLog(file: File, sessionId: String, batcher: FrameBatcher) {
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        val obj = Json.parseToJsonElement(trimmed).jsonObject
                        // Look for timestamp
                        val timestampMs = obj["timestampMs"]?.jsonPrimitive?.longOrNull
                            ?: obj["time"]?.jsonPrimitive?.longOrNull
                            ?: obj["timestamp"]?.jsonPrimitive?.longOrNull

                        if (timestampMs != null) {
                            for ((key, value) in obj) {
                                if (key == "timestampMs" || key == "time" || key == "timestamp") continue
                                val doubleVal = value.jsonPrimitive.doubleOrNull
                                if (doubleVal != null) {
                                    batcher.add(TelemetryFrame(timestampMs, sessionId, key, doubleVal))
                                } else if (value.jsonPrimitive.isString || value.jsonPrimitive.booleanOrNull != null) {
                                    val strVal = value.jsonPrimitive.content
                                    batcher.add(TelemetryFrame(timestampMs, sessionId, key, 0.0, strVal))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore bad lines
                    }
                }
                line = reader.readLine()
            }
        }
    }

    suspend fun parseActionLogJsonl(file: File, sessionId: String): ActionLogMetadata? {
        val actions = mutableListOf<RobotActionRecord>()
        var minTimestamp = Long.MAX_VALUE
        var maxTimestamp = Long.MIN_VALUE
        var firstMatchNumber = 0
        var firstAlliance = "UNKNOWN"
        var isFirstLine = true

        file.bufferedReader(Charsets.UTF_8).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        val obj = Json.parseToJsonElement(trimmed).jsonObject
                        val runId = obj["run_id"]?.jsonPrimitive?.contentOrNull ?: ""
                        val robotId = obj["robot_id"]?.jsonPrimitive?.contentOrNull ?: ""
                        val matchNumber = obj["match_number"]?.jsonPrimitive?.intOrNull ?: 0
                        val alliance = obj["alliance"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
                        val actionType = obj["type"]?.jsonPrimitive?.contentOrNull ?: "Unknown"

                        // Capture envelope metadata from the first line
                        if (isFirstLine) {
                            firstMatchNumber = matchNumber
                            firstAlliance = alliance
                            isFirstLine = false
                        }
                        val payload = obj["payload"]?.jsonObject
                        val timestampMs = payload?.get("timestampMs")?.jsonPrimitive?.longOrNull ?: 0L
                        val payloadJson = payload?.toString() ?: "{}"

                        if (timestampMs > 0L) {
                            if (timestampMs < minTimestamp) minTimestamp = timestampMs
                            if (timestampMs > maxTimestamp) maxTimestamp = timestampMs

                            actions.add(RobotActionRecord(
                                timestampMs = timestampMs,
                                sessionId = sessionId,
                                runId = runId,
                                robotId = robotId,
                                matchNumber = matchNumber,
                                alliance = alliance,
                                actionType = actionType,
                                payloadJson = payloadJson
                            ))
                        }
                    } catch (e: Exception) {
                        // Skip malformed lines
                    }
                }
                line = reader.readLine()
            }
        }

        if (actions.isEmpty()) return null

        databaseService.insertRobotActionsBulk(actions)

        return ActionLogMetadata(
            durationMs = if (maxTimestamp > minTimestamp) maxTimestamp - minTimestamp else 0L,
            matchNumber = firstMatchNumber,
            alliance = firstAlliance
        )
    }
}
