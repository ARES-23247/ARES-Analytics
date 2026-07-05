package com.ares.analytics.service

import com.ares.analytics.shared.Session
import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileInputStream
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

        val frames = mutableListOf<TelemetryFrame>()

        val lowerName = file.name.lowercase()
        when {
            lowerName.endsWith(".wpilog") -> {
                parseWpiLog(file, sessionId, frames)
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
                    parseWpiLog(tempWpiFile, sessionId, frames)
                } finally {
                    tempWpiFile.delete()
                }
            }
            lowerName.endsWith(".jsonl") -> {
                parseJsonlLog(file, sessionId, frames)
            }
            lowerName.endsWith(".csv") -> {
                parseCsvLog(file, sessionId, frames)
            }
            lowerName.endsWith(".dslog") || lowerName.endsWith(".dsevents") -> {
                val targetFile = if (lowerName.endsWith(".dsevents")) {
                    File(file.parentFile, file.nameWithoutExtension + ".dslog")
                } else {
                    file
                }
                DSLogDecoderService(databaseService).parseDsLog(targetFile, sessionId, frames)
            }
            lowerName.endsWith(".log") -> {
                RoadRunnerDecoderService().parseRoadRunnerLog(file, sessionId, frames)
            }
            lowerName.endsWith(".rlog") -> {
                RlogDecoderService().parseRlog(file, sessionId, frames)
            }
            lowerName.endsWith(".revlog") -> {
                RevlogDecoderService(databaseService).parseRevlog(file, sessionId, frames, this@LogParserService)
            }
            else -> {
                throw IllegalArgumentException("Unsupported log file format: ${file.name}")
            }
        }

        // Save session and telemetry
        databaseService.insertSession(session)
        databaseService.insertTelemetryFrames(frames)

        // Update session with actual duration if frames exist
        val finalSession = if (frames.isNotEmpty()) {
            val minTime = frames.minOf { it.timestampMs }
            val maxTime = frames.maxOf { it.timestampMs }
            val duration = maxTime - minTime
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

        val allFrames = mutableListOf<TelemetryFrame>()

        files.forEachIndexed { index, file ->
            val tempFrames = mutableListOf<TelemetryFrame>()
            val lowerName = file.name.lowercase()
            when {
                lowerName.endsWith(".wpilog") -> parseWpiLog(file, sessionId, tempFrames)
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
                        parseWpiLog(tempWpiFile, sessionId, tempFrames)
                    } finally {
                        tempWpiFile.delete()
                    }
                }
                lowerName.endsWith(".jsonl") -> parseJsonlLog(file, sessionId, tempFrames)
                lowerName.endsWith(".csv") -> parseCsvLog(file, sessionId, tempFrames)
                lowerName.endsWith(".dslog") || lowerName.endsWith(".dsevents") -> {
                    val targetFile = if (lowerName.endsWith(".dsevents")) {
                        File(file.parentFile, file.nameWithoutExtension + ".dslog")
                    } else {
                        file
                    }
                    DSLogDecoderService(databaseService).parseDsLog(targetFile, sessionId, tempFrames)
                }
                lowerName.endsWith(".log") -> RoadRunnerDecoderService().parseRoadRunnerLog(file, sessionId, tempFrames)
                lowerName.endsWith(".rlog") -> RlogDecoderService().parseRlog(file, sessionId, tempFrames)
                lowerName.endsWith(".revlog") -> RevlogDecoderService(databaseService).parseRevlog(file, sessionId, tempFrames, this@LogParserService)
                else -> throw IllegalArgumentException("Unsupported log file format: ${file.name}")
            }

            tempFrames.forEach { frame ->
                val cleanKey = frame.key.removePrefix("/")
                allFrames.add(frame.copy(key = "/Log$index/$cleanKey"))
            }
        }

        // Save session and telemetry
        databaseService.insertSession(session)
        databaseService.insertTelemetryFrames(allFrames)

        // Update session with actual duration if frames exist
        if (allFrames.isNotEmpty()) {
            val minTime = allFrames.minOf { it.timestampMs }
            val maxTime = allFrames.maxOf { it.timestampMs }
            val duration = maxTime - minTime
            val finalSession = session.copy(durationMs = duration)
            databaseService.insertSession(finalSession)
            finalSession
        } else {
            session
        }
    }

    internal fun parseWpiLog(file: File, sessionId: String, outFrames: MutableList<TelemetryFrame>) {
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
                                outFrames.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.double))
                            }
                        }
                        "float" -> {
                            if (payload.size == 4) {
                                outFrames.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.float.toDouble()))
                            }
                        }
                        "int64" -> {
                            if (payload.size == 8) {
                                outFrames.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.long.toDouble()))
                            }
                        }
                        "int32" -> {
                            if (payload.size == 4) {
                                outFrames.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.int.toDouble()))
                            }
                        }
                        "boolean" -> {
                            if (payload.isNotEmpty()) {
                                outFrames.add(TelemetryFrame(timestampMs, sessionId, name, if (payload[0].toInt() != 0) 1.0 else 0.0))
                            }
                        }
                        "double[]" -> {
                            val count = payload.size / 8
                            for (i in 0 until count) {
                                if (valBuffer.remaining() >= 8) {
                                    outFrames.add(TelemetryFrame(timestampMs, sessionId, "$name[$i]", valBuffer.double))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseJsonlLog(file: File, sessionId: String, outFrames: MutableList<TelemetryFrame>) {
        file.forEachLine { line ->
            if (line.trim().isEmpty()) return@forEachLine
            try {
                val obj = Json.parseToJsonElement(line).jsonObject
                // Look for timestamp
                val timestampMs = obj["timestampMs"]?.jsonPrimitive?.longOrNull
                    ?: obj["time"]?.jsonPrimitive?.longOrNull
                    ?: obj["timestamp"]?.jsonPrimitive?.longOrNull
                    ?: return@forEachLine

                for ((key, value) in obj) {
                    if (key == "timestampMs" || key == "time" || key == "timestamp") continue
                    val doubleVal = value.jsonPrimitive.doubleOrNull
                    if (doubleVal != null) {
                        outFrames.add(TelemetryFrame(timestampMs, sessionId, key, doubleVal))
                    } else if (value.jsonPrimitive.isString || value.jsonPrimitive.booleanOrNull != null) {
                        val strVal = value.jsonPrimitive.content
                        outFrames.add(TelemetryFrame(timestampMs, sessionId, key, 0.0, strVal))
                    }
                }
            } catch (e: Exception) {
                // Ignore bad lines
            }
        }
    }

    private fun parseCsvLog(file: File, sessionId: String, outFrames: MutableList<TelemetryFrame>) {
        val lines = file.readLines()
        if (lines.size < 2) return
        val headers = lines[0].split(",").map { it.trim() }
        val timeIndex = headers.indexOfFirst { it.contains("time", ignoreCase = true) || it.contains("timestamp", ignoreCase = true) }
        if (timeIndex == -1) return

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val tokens = line.split(",").map { it.trim() }
            if (tokens.size != headers.size) continue

            val timestampMs = tokens[timeIndex].toLongOrNull() ?: continue
            for (j in tokens.indices) {
                if (j == timeIndex) continue
                val strValue = tokens[j]
                val key = headers[j]
                val doubleVal = strValue.toDoubleOrNull()
                if (doubleVal != null) {
                    outFrames.add(TelemetryFrame(timestampMs, sessionId, key, doubleVal))
                } else if (strValue.isNotEmpty()) {
                    outFrames.add(TelemetryFrame(timestampMs, sessionId, key, 0.0, strValue))
                }
            }
        }
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
