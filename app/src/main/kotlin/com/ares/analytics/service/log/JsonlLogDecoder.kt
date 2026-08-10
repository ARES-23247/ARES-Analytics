package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import com.ares.analytics.shared.RobotActionRecord
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.serialization.json.*
import java.io.File

/**
 * Metadata extracted from a Redux robot action log envelope header.
 *
 * @property durationMs Total session execution duration in milliseconds ($ms$).
 * @property matchNumber Tournament match sequence number.
 * @property alliance Alliance station color string (`"RED"`, `"BLUE"`, or `"UNKNOWN"`).
 */
data class ActionLogMetadata(
    val durationMs: Long,
    val matchNumber: Int,
    val alliance: String
)

/**
 * Service for parsing line-delimited JSON (`.jsonl`) telemetry log files and Redux robot action event streams.
 *
 * Parses streaming JSON objects containing frame key-value pairs or structured [RobotActionRecord] objects emitted by
 * `ARESLib-Kotlin` Redux reducers.
 *
 * ### Schema & Data Formats:
 * - Telemetry JSON: `{"timestampMs": 12345, "Drive/Pose_X": 1.25, "Hardware/Motors/fl/Power": 0.85}`
 * - Action JSON: Redux dispatch action payloads `(actionType, payloadJson, alliance, matchNumber)`
 *
 * ### Thread Safety & Performance Guarantees:
 * Suspend functions process JSON line streams sequentially on `Dispatchers.IO`, buffering parsed items into [FrameBatcher] channel without loading complete files into memory.
 *
 * @param databaseService Primary DuckDB persistence interface.
 *
 * @see CsvLogDecoder
 * @see FrameBatcher
 */
class JsonlLogDecoder(private val databaseService: DatabaseService) {

    /**
     * Parses a line-delimited JSON telemetry file line by line into [batcher].
     *
     * @param file Source `.jsonl` file.
     * @param sessionId Target session ID string.
     * @param batcher Destination telemetry frame batch buffer.
     */
    suspend fun parseJsonlLog(file: File, sessionId: String, batcher: FrameBatcher): Int {
        var acceptedFrames = 0
        var rejectedLines = 0
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        val obj = Json.parseToJsonElement(trimmed) as? JsonObject
                        if (obj == null) {
                            rejectedLines++
                            continue
                        }
                        // Look for timestamp
                        val timestampMs = obj["timestampMs"]?.jsonPrimitive?.longOrNull
                            ?: obj["time"]?.jsonPrimitive?.longOrNull
                            ?: obj["timestamp"]?.jsonPrimitive?.longOrNull

                        if (timestampMs == null) {
                            rejectedLines++
                            continue
                        }
                        for ((key, value) in obj) {
                            if (key == "timestampMs" || key == "time" || key == "timestamp") continue
                            val primitive = value as? JsonPrimitive ?: continue
                            val doubleVal = primitive.doubleOrNull
                            val booleanVal = primitive.booleanOrNull
                            when {
                                doubleVal != null -> {
                                    batcher.add(TelemetryFrame(timestampMs, sessionId, key.trimStart('/'), doubleVal))
                                    acceptedFrames++
                                }
                                booleanVal != null -> {
                                    batcher.add(TelemetryFrame(timestampMs, sessionId, key.trimStart('/'), if (booleanVal) 1.0 else 0.0))
                                    acceptedFrames++
                                }
                                primitive.isString -> {
                                    batcher.add(TelemetryFrame(timestampMs, sessionId, key.trimStart('/'), 0.0, primitive.content))
                                    acceptedFrames++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        rejectedLines++
                    }
                }
            }
        }
        require(acceptedFrames > 0) {
            "JSONL log ${file.name} contained no usable telemetry frames ($rejectedLines rejected lines)"
        }
        return acceptedFrames
    }

    suspend fun parseActionLogJsonl(file: File, sessionId: String): ActionLogMetadata? {
        val actions = mutableListOf<RobotActionRecord>()
        var minTimestamp = Long.MAX_VALUE
        var maxTimestamp = Long.MIN_VALUE
        var firstMatchNumber = 0
        var firstAlliance = "UNKNOWN"
        var isFirstLine = true

        file.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        val obj = Json.parseToJsonElement(trimmed) as? JsonObject ?: continue
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
                        val payload = obj["payload"] as? JsonObject
                        val timestampMs = payload?.get("timestampMs")?.jsonPrimitive?.longOrNull ?: 0L
                        val payloadJson = payload?.toString() ?: "{}"

                        if (timestampMs > 0L) {
                            minTimestamp = minOf(minTimestamp, timestampMs)
                            maxTimestamp = maxOf(maxTimestamp, timestampMs)

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
            }
        }

        require(actions.isNotEmpty()) { "Action log ${file.name} contained no usable actions" }

        databaseService.insertRobotActionsBulk(actions)

        return ActionLogMetadata(
            durationMs = if (maxTimestamp > minTimestamp) maxTimestamp - minTimestamp else 0L,
            matchNumber = firstMatchNumber,
            alliance = firstAlliance
        )
    }
}
