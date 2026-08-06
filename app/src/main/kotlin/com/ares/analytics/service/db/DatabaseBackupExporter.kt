package com.ares.analytics.service.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection

/**
 * Service managing database import and export operations for historical telemetry log persistence.
 *
 * Utilizes DuckDB's native vectorized Parquet integration (`read_parquet`) to bulk load match log frames
 * into the main relational database without row-by-row JDBC overhead.
 *
 * ### Database Schema Targets:
 * - Table: `telemetry_frames`
 * - Columns: `timestamp_ms` (BIGINT, ms), `session_id` (VARCHAR), `key` (VARCHAR), `value` (DOUBLE), `string_value` (VARCHAR)
 *
 * ### Thread Safety & Performance Guarantees:
 * Thread-safe. Synchronizes database transactions using an asynchronous [dbMutex] and executes IO operations
 * on [Dispatchers.IO]. Vectorized Parquet queries run in native C++ DuckDB code, maintaining minimal heap allocation in JVM.
 *
 * @param conn Active JDBC connection to the DuckDB instance.
 * @param dbMutex Asynchronous mutual exclusion lock preventing concurrent write transactions on the database connection.
 *
 * @see SchemaMigrationManager
 * @see MatchLogRepository
 */
class DatabaseBackupExporter(
    private val conn: Connection,
    private val dbMutex: Mutex
) {
    /**
     * Helper suspend function executing a database operation under mutual exclusion lock on the IO thread context.
     *
     * @param T Return type of the database operation block.
     * @param block Lambda containing thread-unsafe JDBC database calls.
     * @return Result of [block] execution.
     */
    private suspend fun <T> withDbLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        dbMutex.withLock { block() }
    }

    /**
     * Bulk imports telemetry frames from an Apache Parquet binary file directly into DuckDB's `telemetry_frames` table.
     *
     * Replaces existing conflicting records matching the primary key `(session_id, key, timestamp_ms)` using SQL `INSERT OR REPLACE`.
     *
     * @param file Target `.parquet` log file containing serialized telemetry records.
     * @throws java.sql.SQLException If DuckDB encounters a file read or table insertion error.
     */
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

