package com.ares.analytics.service

import com.ares.analytics.service.log.JsonlLogDecoder
import com.ares.analytics.service.log.WpiLogDecoder
import com.ares.analytics.service.log.CsvLogDecoder
import com.ares.analytics.service.log.ParquetLogDecoder
import com.ares.analytics.shared.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * High-level unified log parser and session ingestion service.
 *
 * Serves as the primary entry point for importing diverse robot log file formats into DuckDB telemetry storage.
 * Auto-detects log file types based on extension or magic bytes, dispatching parsing to specialized decoders
 * ([WpiLogDecoder], [JsonlLogDecoder], [CsvLogDecoder]), buffering frames through [FrameBatcher], and calculating
 * session summary KPIs via [SummaryEngineService].
 *
 * ### Supported Formats:
 * - `.wpilog` / `.rlog` / `.revlog`: WPILib, AdvantageKit, REV binary logs
 * - `.jsonl`: Line-delimited JSON Redux action and telemetry streams
 * - `.csv` / `.csv.gz`: Wide or long tabular CSV log recordings
 * - `.parquet`: Native columnar telemetry backups with timestamp/key/value columns
 *
 * ### Thread Safety & Performance Guarantees:
 * All parsing operations execute asynchronously on `Dispatchers.IO`. Utilizes bounded [FrameBatcher] memory buffers
 * to guarantee zero heap exhaustion during large file imports.
 *
 * @param databaseService Primary DuckDB database management service.
 * @param summaryEngineService Service for generating aggregate session KPI summaries post-ingest.
 *
 * @see FrameBatcher
 * @see SummaryEngineService
 */
class LogParserService(
    private val databaseService: DatabaseService,
    private val summaryEngineService: SummaryEngineService
) {
    private val jsonlDecoder = JsonlLogDecoder(databaseService)
    private val wpiLogDecoder = WpiLogDecoder()
    private val csvLogDecoder = CsvLogDecoder(databaseService)
    private val parquetLogDecoder = ParquetLogDecoder(databaseService)

    suspend fun parseLogFile(
        file: File,
        teamId: String,
        seasonId: String,
        robotId: String,
        matchNumber: Int? = null,
        allianceColor: String? = null,
        tags: List<String> = emptyList()
    ): Session = parseLogFileWithReport(
        file, teamId, seasonId, robotId, matchNumber, allianceColor, tags
    ).session

    suspend fun parseLogFileWithReport(
        file: File,
        teamId: String,
        seasonId: String,
        robotId: String,
        matchNumber: Int? = null,
        allianceColor: String? = null,
        tags: List<String> = emptyList()
    ): LogImportResult = withContext(Dispatchers.IO) {
        require(file.isFile) { "Log file does not exist: ${file.absolutePath}" }
        val sourceSize = file.length()
        val sourceSha256 = sha256(file)
        val session = parseLogFileInternal(
            file, teamId, seasonId, robotId, matchNumber, allianceColor, tags
        )
        val report = buildImportReport(file, session.sessionId, sourceSize, sourceSha256)
        if (report.acceptedRecords == 0L) {
            val failure = IllegalArgumentException("Log contained no importable records: ${file.name}")
            cleanupFailedImport(session.sessionId, failure)
            throw failure
        }
        LogImportResult(session, report)
    }

    private suspend fun parseLogFileInternal(
        file: File,
        teamId: String,
        seasonId: String,
        robotId: String,
        matchNumber: Int?,
        allianceColor: String?,
        tags: List<String>
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

        try {
            when {
            lowerName.endsWith(".wpilog") -> {
                wpiLogDecoder.parseWpiLog(file, sessionId, batcher)
            }
            lowerName.endsWith(".wpilogxz") -> {
                val tempWpiFile = File.createTempFile("wpilog_", ".wpilog")
                try {
                    FileInputStream(file).use { fis ->
                        expandWpiLogXz(file, fis, tempWpiFile)
                    }
                    wpiLogDecoder.parseWpiLog(tempWpiFile, sessionId, batcher)
                } finally {
                    tempWpiFile.delete()
                }
            }
            lowerName.endsWith(".jsonl") -> {
                if (lowerName.startsWith("action_log_")) {
                    val actionMeta = jsonlDecoder.parseActionLogJsonl(file, sessionId)
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
                    jsonlDecoder.parseJsonlLog(file, sessionId, batcher)
                }
            }
            lowerName.endsWith(".csv.gz") -> {
                csvLogDecoder.parseCsvLogStreaming(file, sessionId, batcher)
            }
            lowerName.endsWith(".csv") -> {
                databaseService.insertSession(session)
                csvLogDecoder.parseCsvLogNative(file, sessionId)
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
            lowerName.endsWith(".parquet") -> {
                databaseService.insertSession(session)
                val imported = parquetLogDecoder.parseParquetLog(file, sessionId)
                val duration = if (imported.minTimestampMs != null && imported.maxTimestampMs != null) {
                    imported.maxTimestampMs - imported.minTimestampMs
                } else {
                    0L
                }
                val finalSession = session.copy(durationMs = duration)
                databaseService.insertSession(finalSession)
                val summary = summaryEngineService.generateSummary(finalSession)
                databaseService.insertSessionSummary(summary)
                return@withContext finalSession
            }
            lowerName.endsWith(".dslog") || lowerName.endsWith(".dsevents") -> {
                val targetFile = if (lowerName.endsWith(".dsevents")) {
                    File(file.parentFile, file.nameWithoutExtension + ".dslog")
                } else {
                    file
                }
                com.ares.analytics.service.log.DSLogDecoderService(databaseService).decode(targetFile, sessionId, batcher)
            }
            lowerName.endsWith(".log") -> {
                com.ares.analytics.service.log.RoadRunnerDecoderService().decode(file, sessionId, batcher)
            }
            lowerName.endsWith(".rlog") -> {
                com.ares.analytics.service.log.RlogDecoderService().decode(file, sessionId, batcher)
            }
            lowerName.endsWith(".revlog") -> {
                com.ares.analytics.service.log.RevlogDecoderService(this@LogParserService).decode(file, sessionId, batcher)
            }
            else -> {
                throw IllegalArgumentException("Unsupported log file format: ${file.name}")
            }
        }

            batcher.flush()
            databaseService.insertSession(session)
            val finalSession = if (batcher.frameCount > 0) {
                val duration = batcher.maxTimestamp - batcher.minTimestamp
                val s = session.copy(durationMs = duration)
                databaseService.insertSession(s)
                s
            } else {
                session
            }
            val summary = summaryEngineService.generateSummary(finalSession)
            databaseService.insertSessionSummary(summary)
            return@withContext finalSession
        } catch (failure: Throwable) {
            cleanupFailedImport(sessionId, failure)
            throw failure
        }
    }

    internal suspend fun buildImportReport(
        file: File,
        sessionId: String,
        sourceSizeBytes: Long = file.length(),
        sourceSha256: String = sha256(file),
        decoderOverride: String? = null
    ): ImportReport {
        val telemetryRecords = databaseService.countTelemetryFrames(sessionId)
        val actions = databaseService.getActionsForSession(sessionId)
        val actionRecords = actions.size.toLong()
        val acceptedRecords = telemetryRecords + actionRecords
        val telemetryRange = databaseService.getSessionTimestampRange(sessionId)
        val minTimestampMs = telemetryRange?.first ?: actions.minOfOrNull { it.timestampMs }
        val maxTimestampMs = telemetryRange?.second ?: actions.maxOfOrNull { it.timestampMs }
        val warnings = buildList {
            if (telemetryRecords == 0L && actionRecords > 0L) add("Action log contains no telemetry frames")
            add("Rejected-record count is unavailable for the ${decoderOverride ?: decoderName(file)} decoder")
        }
        return ImportReport(
            sourceName = file.name,
            sourceSha256 = sourceSha256,
            sourceSizeBytes = sourceSizeBytes,
            decoder = decoderOverride ?: decoderName(file),
            status = if (acceptedRecords > 0L) ImportStatus.SUCCESS else ImportStatus.REJECTED,
            sessionId = sessionId,
            acceptedRecords = acceptedRecords,
            detectedTopics = databaseService.getDistinctTelemetryKeys(sessionId),
            minTimestampMs = minTimestampMs,
            maxTimestampMs = maxTimestampMs,
            warnings = warnings
        )
    }

    internal fun buildRejectedImportReport(
        file: File,
        error: Throwable,
        decoderOverride: String? = null
    ): ImportReport = ImportReport(
        sourceName = file.name,
        sourceSha256 = sha256(file),
        sourceSizeBytes = file.length(),
        decoder = decoderOverride ?: decoderName(file),
        status = ImportStatus.REJECTED,
        error = error.message ?: error::class.simpleName ?: "Import failed"
    )

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
        val batcher = FrameBatcher(databaseService, keyTransform = { key ->
            key.removePrefix("/")
        })
        try {
            files.forEach { file ->
            val lowerName = file.name.lowercase()
            when {
                lowerName.endsWith(".wpilog") -> wpiLogDecoder.parseWpiLog(file, sessionId, batcher)
                lowerName.endsWith(".wpilogxz") -> {
                    val tempWpiFile = File.createTempFile("wpilog_", ".wpilog")
                    try {
                        FileInputStream(file).use { fis ->
                            expandWpiLogXz(file, fis, tempWpiFile)
                        }
                        wpiLogDecoder.parseWpiLog(tempWpiFile, sessionId, batcher)
                    } finally {
                        tempWpiFile.delete()
                    }
                }
                lowerName.endsWith(".jsonl") -> {
                    if (lowerName.startsWith("action_log_")) {
                        val actionMeta = jsonlDecoder.parseActionLogJsonl(file, sessionId)
                        if (actionMeta != null) {
                            currentMatchNumber = currentMatchNumber ?: actionMeta.matchNumber
                            currentAlliance = currentAlliance ?: actionMeta.alliance
                            if (!currentTags.contains("action-log")) {
                                currentTags = currentTags + "action-log"
                            }
                        }
                    } else {
                        jsonlDecoder.parseJsonlLog(file, sessionId, batcher)
                    }
                }
                lowerName.endsWith(".csv.gz") || lowerName.endsWith(".csv") -> {
                    // Native CSV import numbers duplicate samples from zero for each file.
                    // Multi-file sessions instead share this streaming batcher so overlapping
                    // timestamp/topic samples receive repository-wide stable storage order.
                    csvLogDecoder.parseCsvLogStreaming(file, sessionId, batcher)
                }
                lowerName.endsWith(".parquet") -> {
                    parquetLogDecoder.parseParquetLog(file, sessionId)
                }
                lowerName.endsWith(".dslog") || lowerName.endsWith(".dsevents") -> {
                    val targetFile = if (lowerName.endsWith(".dsevents")) {
                        File(file.parentFile, file.nameWithoutExtension + ".dslog")
                    } else {
                        file
                    }
                    com.ares.analytics.service.log.DSLogDecoderService(databaseService).decode(targetFile, sessionId, batcher)
                }
                lowerName.endsWith(".log") -> com.ares.analytics.service.log.RoadRunnerDecoderService().decode(file, sessionId, batcher)
                lowerName.endsWith(".rlog") -> com.ares.analytics.service.log.RlogDecoderService().decode(file, sessionId, batcher)
                lowerName.endsWith(".revlog") -> com.ares.analytics.service.log.RevlogDecoderService(this@LogParserService).decode(file, sessionId, batcher)
                else -> throw IllegalArgumentException("Unsupported log file format: ${file.name}")
            }

            }
            batcher.flush()
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

            databaseService.insertSession(baseSession)
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
        } catch (failure: Throwable) {
            cleanupFailedImport(sessionId, failure)
            throw failure
        }
    }

    internal suspend fun parseWpiLog(file: File, sessionId: String, batcher: FrameBatcher) {
        wpiLogDecoder.parseWpiLog(file, sessionId, batcher)
    }

    private suspend fun cleanupFailedImport(sessionId: String, failure: Throwable) {
        try {
            databaseService.deleteSession(sessionId)
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        }
    }

    internal fun decoderName(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".wpilogxz") -> "wpilog-xz"
            name.startsWith("action_log_") && name.endsWith(".jsonl") -> "action-jsonl"
            name.endsWith(".wpilog") -> "wpilog"
            name.endsWith(".jsonl") -> "jsonl"
            name.endsWith(".csv.gz") -> "csv-gzip"
            name.endsWith(".csv") -> "csv"
            name.endsWith(".parquet") -> "parquet"
            name.endsWith(".dslog") || name.endsWith(".dsevents") -> "driver-station"
            name.endsWith(".rlog") -> "rlog"
            name.endsWith(".revlog") -> "revlog"
            name.endsWith(".log") -> "road-runner"
            else -> file.extension.lowercase().ifEmpty { "unknown" }
        }
    }

    private fun expandWpiLogXz(sourceFile: File, input: FileInputStream, destination: File) {
        val ratioBound = sourceFile.length().coerceAtLeast(1L)
            .coerceAtMost(MAX_XZ_EXPANDED_BYTES / MAX_XZ_EXPANSION_RATIO) * MAX_XZ_EXPANSION_RATIO
        val expandedLimit = maxOf(MIN_XZ_EXPANDED_BYTES, ratioBound)
            .coerceAtMost(MAX_XZ_EXPANDED_BYTES)
        org.tukaani.xz.XZInputStream(input, MAX_XZ_DECODER_MEMORY_KIB).use { expanded ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(XZ_COPY_BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = expanded.read(buffer)
                    if (read < 0) break
                    total = Math.addExact(total, read.toLong())
                    require(total <= expandedLimit) {
                        "Compressed WPILOG expands beyond the ${expandedLimit / (1024 * 1024)} MiB safety limit"
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        private const val XZ_COPY_BUFFER_BYTES = 64 * 1024
        private const val MAX_XZ_DECODER_MEMORY_KIB = 64 * 1024
        private const val MAX_XZ_EXPANSION_RATIO = 100L
        private const val MIN_XZ_EXPANDED_BYTES = 16L * 1024L * 1024L
        private const val MAX_XZ_EXPANDED_BYTES = 512L * 1024L * 1024L
    }
}
