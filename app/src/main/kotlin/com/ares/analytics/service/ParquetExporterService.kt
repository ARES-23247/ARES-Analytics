package com.ares.analytics.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Service for exporting DuckDB telemetry tables into Snappy-compressed Apache Parquet binary files.
 *
 * Utilizes DuckDB's native `COPY ... TO ... (FORMAT PARQUET, COMPRESSION SNAPPY)` statement to output
 * columnar binary files suitable for cloud upload and offline data science tools (Pandas, Polars, DuckDB).
 *
 * ### Output File Layout:
 * Columns: `timestamp_ms` (BIGINT), `session_id` (VARCHAR), `key` (VARCHAR), `value` (DOUBLE), `string_value` (VARCHAR).
 *
 * ### Thread Safety & Performance Guarantees:
 * Executes native C++ DuckDB file serialization on `Dispatchers.IO`. Zero JVM heap allocation during binary export.
 *
 * @param databaseService Primary DuckDB telemetry database service.
 *
 * @see ExportService
 * @see SyncEngineService
 */
class ParquetExporterService(private val databaseService: DatabaseService) {

    /**
     * Bulk exports all telemetry frames for a specified [sessionId] into a Snappy-compressed Parquet file.
     *
     * @param sessionId Session identifier string.
     * @param destinationFile Destination Parquet file.
     * @throws IllegalArgumentException If no telemetry frames exist for [sessionId].
     */
    suspend fun exportSessionToParquet(sessionId: String, destinationFile: File) = withContext(Dispatchers.IO) {
        val count = databaseService.countTelemetryFrames(sessionId)
        if (count == 0L) {
            throw IllegalArgumentException("Cannot export empty session: $sessionId")
        }

        // Ensure parent folder exists
        destinationFile.parentFile?.mkdirs()

        // Delete existing file since COPY TO doesn't overwrite by default
        if (destinationFile.exists()) {
            destinationFile.delete()
        }
        val absolutePath = destinationFile.absolutePath.replace("\\", "/")
        
        databaseService.executeRaw("""
            COPY (SELECT * FROM telemetry_frames WHERE session_id = '$sessionId')
            TO '$absolutePath' (FORMAT PARQUET, COMPRESSION SNAPPY, ROW_GROUP_SIZE 100000)
        """.trimIndent())
    }
}
