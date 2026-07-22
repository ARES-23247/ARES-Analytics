package com.ares.analytics.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class ParquetExporterService(private val databaseService: DatabaseService) {

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
            TO '$absolutePath' (FORMAT PARQUET, COMPRESSION SNAPPY)
        """.trimIndent())
    }
}
