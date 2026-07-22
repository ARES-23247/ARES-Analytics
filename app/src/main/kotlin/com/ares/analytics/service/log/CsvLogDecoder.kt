package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import com.ares.analytics.shared.TelemetryFrame
import java.io.File

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class CsvLogDecoder(private val databaseService: DatabaseService) {

    /**
     * Imports a CSV file directly into DuckDB using native `read_csv_auto` + `UNPIVOT`,
     * bypassing all Kotlin-side string parsing and TelemetryFrame object allocation.
     * This is ~10-50× faster than the streaming Kotlin parser for large CSV files.
     *
     * The CSV is expected to have a header row with one timestamp column (containing
     * "time" or "timestamp" in its name) and remaining columns as telemetry keys.
     */
    suspend fun parseCsvLogNative(file: File, sessionId: String) {
        val absolutePath = file.absolutePath.replace("\\", "/").replace("'", "''")

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
    suspend fun parseCsvLogStreaming(file: File, sessionId: String, batcher: FrameBatcher) {
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
}
