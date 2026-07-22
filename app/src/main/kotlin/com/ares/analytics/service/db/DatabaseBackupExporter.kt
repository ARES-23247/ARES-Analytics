package com.ares.analytics.service.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class DatabaseBackupExporter(
    private val conn: Connection,
    private val dbMutex: Mutex
) {
    private suspend fun <T> withDbLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        dbMutex.withLock { block() }
    }

    suspend fun importParquet(file: File) = withDbLock {
        val absolutePath = file.absolutePath.replace("\\", "/")
        conn.createStatement().use { st ->
            st.execute("""
                INSERT OR REPLACE INTO telemetry_frames 
                SELECT * FROM read_parquet('$absolutePath')
            """.trimIndent())
        }
    }
}
