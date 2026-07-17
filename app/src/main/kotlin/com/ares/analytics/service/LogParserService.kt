package com.ares.analytics.service

import com.ares.analytics.shared.Session
import com.ares.analytics.shared.RobotActionRecord
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class LogParserService(
    private val databaseService: DatabaseService,
    private val summaryEngineService: SummaryEngineService
) {

    suspend fun parseLogFile(
        file: File,
        teamId: String,
        seasonId: String,
        robotId: String,
        matchNumber: Int? = null,
        allianceColor: String? = null,
        tags: List<String> = emptyList()
    ): Session = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        val createdAt = file.lastModified()
        val session = Session(
            sessionId = sessionId,
            teamId = teamId,
            seasonId = seasonId,
            robotId = robotId,
            createdAt = createdAt,
            matchNumber = matchNumber,
            allianceColor = allianceColor,
            tags = tags
        )

        val batcher = FrameBatcher(databaseService)

        val lowerName = file.name.lowercase()
        when {
            lowerName.endsWith(".wpilog") -> {
                parseWpiLog(file, sessionId, batcher)
            }
            lowerName.endsWith(".wpilogxz") -> {
                val tempWpiFile = File.createTempFile("wpilog_", ".wpilog")
                try {
                    FileInputStream(file).use { fis ->
                        org.tukaani.xz.XZInputStream(fis).use { xzis ->
                            tempWpiFile.outputStream().use { fos ->
                                xzis.copyTo(fos)
                            }
                        }
                    }
                    parseWpiLog(tempWpiFile, sessionId, batcher)
                } finally {
                    tempWpiFile.delete()
                }
            }
            lowerName.endsWith(".jsonl") -> {
                if (lowerName.startsWith("action_log_")) {
                    val actionMeta = parseActionLogJsonl(file, sessionId)
                    // Enrich the session with metadata extracted from the action log envelope
                    if (actionMeta != null) {
                        val enrichedSession = session.copy(
                            durationMs = actionMeta.durationMs,
                            matchNumber = matchNumber ?: actionMeta.matchNumber,
                            allianceColor = allianceColor ?: actionMeta.alliance,
                            tags = tags + "action-log"
                        )
                        databaseService.insertSession(enrichedSession)
                        val summary = summaryEngineService.generateSummary(enrichedSession)
                        databaseService.insertSessionSummary(summary)
                        return@withContext enrichedSession
                    }
                } else {
                    parseJsonlLog(file, sessionId, batcher)
                }
            }
            lowerName.endsWith(".csv") -> {
                // Save session first so DuckDB CSV import can reference it
                databaseService.insertSession(session)
                parseCsvLogNative(file, sessionId)
                // Query DuckDB for timestamp range since native import bypasses the batcher
                val range = databaseService.getSessionTimestampRange(sessionId)
                if (range != null) {
                    val finalSession = session.copy(durationMs = range.second - range.first)
                    databaseService.insertSession(finalSession)
                    val summary = summaryEngineService.generateSummary(finalSession)
                    databaseService.insertSessionSummary(summary)
                    return@withContext finalSession
                } else {
                    val summary = summaryEngineService.generateSummary(session)
                    databaseService.insertSessionSummary(summary)
                    return@withContext session
                }
            }
            lowerName.endsWith(".dslog") || lowerName.endsWith(".dsevents") -> {
                val targetFile = if (lowerName.endsWith(".dsevents")) {
                    File(file.parentFile, file.nameWithoutExtension + ".dslog")
                } else {
                    file
                }
                DSLogDecoderService(databaseService).parseDsLog(targetFile, sessionId, batcher)
            }
            lowerName.endsWith(".log") -> {
                RoadRunnerDecoderService().parseRoadRunnerLog(file, sessionId, batcher)
            }
            lowerName.endsWith(".rlog") -> {
                RlogDecoderService().parseRlog(file, sessionId, batcher)
            }
            lowerName.endsWith(".revlog") -> {
                RevlogDecoderService(databaseService).parseRevlog(file, sessionId, batcher, this@LogParserService)
            }
            else -> {
                throw IllegalArgumentException("Unsupported log file format: ${file.name}")
            }
        }

        // Flush any remaining frames in the batcher
        batcher.flush()

        // Save session and compute duration from batcher's tracked timestamps
        databaseService.insertSession(session)

        val finalSession = if (batcher.frameCount > 0) {
            val duration = batcher.maxTimestamp - batcher.minTimestamp
            val s = session.copy(durationMs = duration)
            databaseService.insertSession(s)
            s
        } else {
            session
        }

        // Generate and store session summary
        val summary = summaryEngineService.generateSummary(finalSession)
        databaseService.insertSessionSummary(summary)

        return@withContext finalSession
    }

    suspend fun parseLogFiles(
        files: List<File>,
        teamId: String,
        seasonId: String,
        robotId: String,
        matchNumber: Int? = null,
        allianceColor: String? = null,
        tags: List<String> = emptyList()
    ): Session = withContext(Dispatchers.IO) {
        if (files.isEmpty()) throw IllegalArgumentException("No log files provided")
        if (files.size == 1) {
            return@withContext parseLogFile(files.first(), teamId, seasonId, robotId, matchNumber, allianceColor, tags)
        }

        val sessionId = UUID.randomUUID().toString()
        val createdAt = files.first().lastModified()
        
        var currentMatchNumber = matchNumber
        var currentAlliance = allianceColor
        var currentTags = tags

        // Use a single batcher across all files; track min/max timestamps globally
        var globalMinTimestamp = Long.MAX_VALUE
        var globalMaxTimestamp = Long.MIN_VALUE

        files.forEachIndexed { index, file ->
            // Create a batcher without prefixing keys, so action logs and csv logs merge seamlessly
            val batcher = FrameBatcher(databaseService, keyTransform = { key ->
                key.removePrefix("/")
            })

            val lowerName = file.name.lowercase()
            when {
                lowerName.endsWith(".wpilog") -> parseWpiLog(file, sessionId, batcher)
                lowerName.endsWith(".wpilogxz") -> {
                    val tempWpiFile = File.createTempFile("wpilog_", ".wpilog")
                    try {
                        FileInputStream(file).use { fis ->
                            org.tukaani.xz.XZInputStream(fis).use { xzis ->
                                tempWpiFile.outputStream().use { fos ->
                                    xzis.copyTo(fos)
                                }
                            }
                        }
                        parseWpiLog(tempWpiFile, sessionId, batcher)
                    } finally {
                        tempWpiFile.delete()
                    }
                }
                lowerName.endsWith(".jsonl") -> {
                    if (lowerName.startsWith("action_log_")) {
                        val actionMeta = parseActionLogJsonl(file, sessionId)
                        if (actionMeta != null) {
                            currentMatchNumber = currentMatchNumber ?: actionMeta.matchNumber
                            currentAlliance = currentAlliance ?: actionMeta.alliance
                            if (!currentTags.contains("action-log")) {
                                currentTags = currentTags + "action-log"
                            }
                        }
                    } else {
                        parseJsonlLog(file, sessionId, batcher)
                    }
                }
                lowerName.endsWith(".csv") -> {
                    parseCsvLogNative(file, sessionId)
                }
                lowerName.endsWith(".dslog") || lowerName.endsWith(".dsevents") -> {
                    val targetFile = if (lowerName.endsWith(".dsevents")) {
                        File(file.parentFile, file.nameWithoutExtension + ".dslog")
                    } else {
                        file
                    }
                    DSLogDecoderService(databaseService).parseDsLog(targetFile, sessionId, batcher)
                }
                lowerName.endsWith(".log") -> RoadRunnerDecoderService().parseRoadRunnerLog(file, sessionId, batcher)
                lowerName.endsWith(".rlog") -> RlogDecoderService().parseRlog(file, sessionId, batcher)
                lowerName.endsWith(".revlog") -> RevlogDecoderService(databaseService).parseRevlog(file, sessionId, batcher, this@LogParserService)
                else -> throw IllegalArgumentException("Unsupported log file format: ${file.name}")
            }

            batcher.flush()

            if (batcher.frameCount > 0) {
                if (batcher.minTimestamp < globalMinTimestamp) globalMinTimestamp = batcher.minTimestamp
                if (batcher.maxTimestamp > globalMaxTimestamp) globalMaxTimestamp = batcher.maxTimestamp
            }
        }

        val baseSession = Session(
            sessionId = sessionId,
            teamId = teamId,
            seasonId = seasonId,
            robotId = robotId,
            createdAt = createdAt,
            matchNumber = currentMatchNumber,
            allianceColor = currentAlliance,
            tags = currentTags
        )

        // Save session and telemetry
        databaseService.insertSession(baseSession)

        // Update session with actual duration if frames exist
        val range = databaseService.getSessionTimestampRange(sessionId)
        val finalSession = if (range != null) {
            val duration = range.second - range.first
            val s = baseSession.copy(durationMs = duration)
            databaseService.insertSession(s)
            s
        } else {
            baseSession
        }
        
        val summary = summaryEngineService.generateSummary(finalSession)
        databaseService.insertSessionSummary(summary)
        
        return@withContext finalSession
    }

    /**
     * Imports a CSV file directly into DuckDB using native `read_csv_auto` + `UNPIVOT`,
     * bypassing all Kotlin-side string parsing and TelemetryFrame object allocation.
     * This is ~10-50× faster than the streaming Kotlin parser for large CSV files.
     *
     * The CSV is expected to have a header row with one timestamp column (containing
     * "time" or "timestamp" in its name) and remaining columns as telemetry keys.
     */
    private suspend fun parseCsvLogNative(file: File, sessionId: String) {
        val absolutePath = file.absolutePath.replace("\\", "/")

        // Detect the timestamp column name from the header
        val headerLine = file.bufferedReader(Charsets.UTF_8).use { it.readLine() }
            ?: return
        val headers = headerLine.split(",").map { it.trim() }
        val timeColumnName = headers.firstOrNull {
            it.contains("time", ignoreCase = true) || it.contains("timestamp", ignoreCase = true)
        } ?: return

        // Use DuckDB's native CSV reader with UNPIVOT to convert wide-format CSV
        // directly into the long-format telemetry_frames schema in a single SQL pass.
        val escapedSessionId = sessionId.replace("'", "''")
        val escapedTimeCol = timeColumnName.replace("'", "''").replace("\"", "\"\"")

        databaseService.executeRaw("""
            INSERT INTO telemetry_frames (timestamp_ms, session_id, key, value, string_value)
            SELECT
                CAST("$escapedTimeCol" AS BIGINT) AS timestamp_ms,
                '$escapedSessionId' AS session_id,
                key,
                COALESCE(
                    CASE 
                        WHEN LOWER(CAST(value AS VARCHAR)) = 'true' THEN 1.0
                        WHEN LOWER(CAST(value AS VARCHAR)) = 'false' THEN 0.0
                        ELSE TRY_CAST(value AS DOUBLE) 
                    END,
                    0.0
                ) AS value,
                CASE 
                    WHEN LOWER(CAST(value AS VARCHAR)) IN ('true', 'false') THEN NULL
                    WHEN TRY_CAST(value AS DOUBLE) IS NULL THEN CAST(value AS VARCHAR) 
                END AS string_value
            FROM (
                SELECT * FROM read_csv_auto('$absolutePath', header=true, ignore_errors=true, all_varchar=true)
            ) UNPIVOT (
                value FOR key IN (* EXCLUDE ("$escapedTimeCol"))
            )
            WHERE value IS NOT NULL AND CAST(value AS VARCHAR) != ''
        """.trimIndent())
    }

    /**
     * Streaming CSV parser used as a fallback for multi-file imports where
     * DuckDB native import can't apply per-file key prefixes. Uses a
     * [FrameBatcher] to maintain bounded memory usage.
     */
    private suspend fun parseCsvLogStreaming(file: File, sessionId: String, batcher: FrameBatcher) {
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            val headerLine = reader.readLine() ?: return
            val headers = headerLine.split(",").map { it.trim() }
            val timeIndex = headers.indexOfFirst {
                it.contains("time", ignoreCase = true) || it.contains("timestamp", ignoreCase = true)
            }
            if (timeIndex == -1) return

            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val tokens = trimmed.split(",").map { it.trim() }
                    if (tokens.size == headers.size) {
                        val timestampMs = tokens[timeIndex].toLongOrNull()
                        if (timestampMs != null) {
                            for (j in tokens.indices) {
                                if (j == timeIndex) continue
                                val strValue = tokens[j]
                                val key = headers[j]
                                val doubleVal = strValue.toDoubleOrNull()
                                when {
                                    doubleVal != null -> {
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, key, doubleVal))
                                    }
                                    strValue.equals("true", ignoreCase = true) -> {
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, key, 1.0))
                                    }
                                    strValue.equals("false", ignoreCase = true) -> {
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, key, 0.0))
                                    }
                                    strValue.isNotEmpty() -> {
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, key, 0.0, strValue))
                                    }
                                }
                            }
                        }
                    }
                }
                line = reader.readLine()
            }
        }
    }

    internal suspend fun parseWpiLog(file: File, sessionId: String, batcher: FrameBatcher) {
        FileInputStream(file).use { fis ->
            val headerBytes = ByteArray(12)
            if (fis.read(headerBytes) != 12) return
            val magic = String(headerBytes, 0, 6)
            if (magic != "WPILOG") return

            // Map from entryId to (name, type)
            val entryMap = mutableMapOf<Int, Pair<String, String>>()

            val fileLength = file.length()
            var bytesRead = 12L

            while (bytesRead < fileLength) {
                val headerByte = fis.read()
                if (headerByte == -1) break
                bytesRead++

                val entryIdSize = (headerByte and 0x03) + 1
                val payloadSizeSize = ((headerByte ushr 2) and 0x03) + 1
                val timestampSize = ((headerByte ushr 4) and 0x03) + 1

                val recordHeaderSize = entryIdSize + payloadSizeSize + timestampSize
                val recordHeaderBytes = ByteArray(recordHeaderSize)
                if (fis.read(recordHeaderBytes) != recordHeaderSize) break
                bytesRead += recordHeaderSize

                val buffer = ByteBuffer.wrap(recordHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)

                val entryId = readVariableInt(buffer, entryIdSize)
                val payloadSize = readVariableInt(buffer, payloadSizeSize)
                val timestampMicro = readVariableLong(buffer, timestampSize)
                val timestampMs = timestampMicro / 1000

                val payload = ByteArray(payloadSize)
                if (fis.read(payload) != payloadSize) break
                bytesRead += payloadSize

                if (entryId == 0) {
                    // Control Record
                    if (payload.isNotEmpty()) {
                        val controlType = payload[0].toInt()
                        val controlBuffer = ByteBuffer.wrap(payload, 1, payload.size - 1).order(ByteOrder.LITTLE_ENDIAN)
                        when (controlType) {
                            0 -> { // Start
                                if (controlBuffer.remaining() >= 4) {
                                    val newEntryId = controlBuffer.int
                                    val name = readNullTerminatedString(controlBuffer)
                                    val type = readNullTerminatedString(controlBuffer)
                                    entryMap[newEntryId] = Pair(name, type)
                                }
                            }
                            1 -> { // Finish
                                if (controlBuffer.remaining() >= 4) {
                                    val finishEntryId = controlBuffer.int
                                    entryMap.remove(finishEntryId)
                                }
                            }
                        }
                    }
                } else {
                    // Data Record
                    val entry = entryMap[entryId] ?: continue
                    val name = entry.first
                    val type = entry.second

                    val valBuffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                    when (type) {
                        "double" -> {
                            if (payload.size == 8) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.double))
                            }
                        }
                        "float" -> {
                            if (payload.size == 4) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.float.toDouble()))
                            }
                        }
                        "int64" -> {
                            if (payload.size == 8) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.long.toDouble()))
                            }
                        }
                        "int32" -> {
                            if (payload.size == 4) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.int.toDouble()))
                            }
                        }
                        "boolean" -> {
                            if (payload.isNotEmpty()) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, if (payload[0].toInt() != 0) 1.0 else 0.0))
                            }
                        }
                        "double[]" -> {
                            val count = payload.size / 8
                            for (i in 0 until count) {
                                if (valBuffer.remaining() >= 8) {
                                    batcher.add(TelemetryFrame(timestampMs, sessionId, "$name[$i]", valBuffer.double))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun parseJsonlLog(file: File, sessionId: String, batcher: FrameBatcher) {
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

    /**
     * Metadata extracted from an action log file's envelope fields.
     */
    private data class ActionLogMetadata(
        val durationMs: Long,
        val matchNumber: Int,
        val alliance: String
    )

    /**
     * Parses action_log JSONL files produced by ARESLib's ActionLogger.
     * Each line is a JSON envelope containing:
     *   - run_id, robot_id, match_number, alliance (top-level metadata)
     *   - type (action class name, e.g. "PoseUpdate", "SetAlliance")
     *   - payload (nested JSON object with action-specific fields + timestampMs)
     *
     * Actions are batched and bulk-inserted into the robot_actions table.
     * Returns metadata extracted from the log for session enrichment, or null if the file is empty.
     */
    private suspend fun parseActionLogJsonl(file: File, sessionId: String): ActionLogMetadata? {
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

    private fun readVariableInt(buffer: ByteBuffer, size: Int): Int {
        var value = 0
        for (i in 0 until size) {
            value = value or ((buffer.get().toInt() and 0xFF) shl (i * 8))
        }
        return value
    }

    private fun readVariableLong(buffer: ByteBuffer, size: Int): Long {
        var value = 0L
        for (i in 0 until size) {
            value = value or ((buffer.get().toLong() and 0xFFL) shl (i * 8))
        }
        return value
    }

    private fun readNullTerminatedString(buffer: ByteBuffer): String {
        val bytes = mutableListOf<Byte>()
        while (buffer.hasRemaining()) {
            val b = buffer.get()
            if (b == 0.toByte()) break
            bytes.add(b)
        }
        return String(bytes.toByteArray())
    }
}
