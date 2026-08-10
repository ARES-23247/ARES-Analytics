package com.ares.analytics.service.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
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
    /** Result of importing a Parquet trace under a caller-owned session identity. */
    data class ParquetImportResult(
        val frameCount: Long,
        val minTimestampMs: Long?,
        val maxTimestampMs: Long?
    )

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
        val absolutePath = sqlLiteral(file.canonicalPath.replace("\\", "/"))
        val columns = parquetColumns(absolutePath)
        val required = setOf("timestamp_ms", "session_id", "key", "value")
        require((required - columns).isEmpty()) {
            "Parquet telemetry log is missing required columns: ${(required - columns).sorted().joinToString()}"
        }
        val stringExpression = if ("string_value" in columns) "CAST(string_value AS VARCHAR)" else "NULL"
        val timestampUsExpression = if ("timestamp_us" in columns) {
            "COALESCE(TRY_CAST(timestamp_us AS BIGINT), CAST(timestamp_ms AS BIGINT) * 1000)"
        } else {
            "CAST(timestamp_ms AS BIGINT) * 1000"
        }
        val sampleOrderExpression = if ("sample_order" in columns) {
            "COALESCE(TRY_CAST(sample_order AS BIGINT), ROW_NUMBER() OVER ())"
        } else {
            "ROW_NUMBER() OVER ()"
        }
        val previousAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            conn.createStatement().use { st ->
                st.execute(
                    """
                    DELETE FROM telemetry_frames AS target
                    USING (
                        SELECT CAST(session_id AS VARCHAR) AS session_id,
                            REGEXP_REPLACE(TRIM(CAST(key AS VARCHAR)), '^/+', '') AS key,
                            $timestampUsExpression AS timestamp_us
                        FROM read_parquet('$absolutePath')
                        WHERE timestamp_ms IS NOT NULL AND session_id IS NOT NULL AND key IS NOT NULL
                    ) AS source
                    WHERE target.session_id = source.session_id
                        AND target.key = source.key
                        AND target.timestamp_us = source.timestamp_us
                    """.trimIndent()
                )
                st.execute("""
                    INSERT INTO telemetry_frames
                        (timestamp_ms, session_id, key, value, string_value, timestamp_us, sample_order)
                    SELECT CAST(timestamp_ms AS BIGINT), CAST(session_id AS VARCHAR),
                        REGEXP_REPLACE(TRIM(CAST(key AS VARCHAR)), '^/+', ''),
                        COALESCE(TRY_CAST(value AS DOUBLE), 0.0), $stringExpression,
                        $timestampUsExpression, $sampleOrderExpression
                    FROM read_parquet('$absolutePath')
                    WHERE timestamp_ms IS NOT NULL AND session_id IS NOT NULL AND key IS NOT NULL
                    ON CONFLICT (session_id, key, timestamp_us, sample_order) DO UPDATE SET
                        value = EXCLUDED.value,
                        string_value = EXCLUDED.string_value
                """.trimIndent())
            }
            conn.commit()
        } catch (error: Exception) {
            conn.rollback()
            throw error
        } finally {
            conn.autoCommit = previousAutoCommit
        }
    }

    /**
     * Imports a user-selected Parquet telemetry trace and remaps every row to [sessionId].
     *
     * Required source columns are `timestamp_ms`, `key`, and `value`; `string_value` is optional.
     * A source `session_id` is deliberately ignored so imported backups cannot overwrite or merge
     * with an unrelated local session. Schema validation occurs before the transaction writes rows.
     */
    suspend fun importParquetAsSession(file: File, sessionId: String): ParquetImportResult = withDbLock {
        require(file.isFile) { "Parquet log does not exist: ${file.absolutePath}" }
        require(file.extension.equals("parquet", ignoreCase = true)) {
            "Expected a .parquet telemetry log: ${file.name}"
        }

        val safePath = sqlLiteral(file.canonicalPath.replace("\\", "/"))
        val columns = parquetColumns(safePath)
        val required = setOf("timestamp_ms", "key", "value")
        val missing = required - columns
        require(missing.isEmpty()) {
            "Parquet telemetry log is missing required columns: ${missing.sorted().joinToString()}"
        }

        val safeSessionId = sqlLiteral(sessionId)
        val stringExpression = if ("string_value" in columns) {
            "CAST(string_value AS VARCHAR)"
        } else {
            "NULL"
        }
        val timestampUsExpression = if ("timestamp_us" in columns) {
            "COALESCE(TRY_CAST(timestamp_us AS BIGINT), CAST(timestamp_ms AS BIGINT) * 1000)"
        } else {
            "CAST(timestamp_ms AS BIGINT) * 1000"
        }
        val sampleOrderExpression = if ("sample_order" in columns) {
            "COALESCE(TRY_CAST(sample_order AS BIGINT), ROW_NUMBER() OVER ())"
        } else {
            "ROW_NUMBER() OVER ()"
        }
        val previousAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            conn.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO telemetry_frames
                        (timestamp_ms, session_id, key, value, string_value, timestamp_us, sample_order)
                    SELECT
                        CAST(timestamp_ms AS BIGINT),
                        '$safeSessionId',
                        REGEXP_REPLACE(CAST(key AS VARCHAR), '^/+', ''),
                        COALESCE(TRY_CAST(value AS DOUBLE), 0.0),
                        $stringExpression,
                        $timestampUsExpression,
                        $sampleOrderExpression
                    FROM read_parquet('$safePath')
                    WHERE timestamp_ms IS NOT NULL AND key IS NOT NULL
                    ON CONFLICT (session_id, key, timestamp_us, sample_order) DO UPDATE SET
                        value = EXCLUDED.value,
                        string_value = EXCLUDED.string_value
                    """.trimIndent()
                )
            }
            val result = conn.prepareStatement(
                "SELECT COUNT(*), MIN(timestamp_ms), MAX(timestamp_ms) FROM telemetry_frames WHERE session_id = ?"
            ).use { statement ->
                statement.setString(1, sessionId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    val count = rows.getLong(1)
                    val min = rows.getLong(2).takeUnless { rows.wasNull() }
                    val max = rows.getLong(3).takeUnless { rows.wasNull() }
                    ParquetImportResult(count, min, max)
                }
            }
            conn.commit()
            result
        } catch (error: Exception) {
            conn.rollback()
            throw error
        } finally {
            conn.autoCommit = previousAutoCommit
        }
    }

    private fun parquetColumns(safePath: String): Set<String> =
        conn.createStatement().use { statement ->
            statement.executeQuery("DESCRIBE SELECT * FROM read_parquet('$safePath')").use { result ->
                buildSet {
                    while (result.next()) add(result.getString(1).lowercase())
                }
            }
        }

    /**
     * Exports one session through a narrowly scoped, internally escaped COPY statement.
     * General raw-SQL execution intentionally remains read-only and must not be used for export.
     */
    suspend fun exportSessionToParquet(sessionId: String, destinationFile: File) = withDbLock {
        val target = destinationFile.canonicalFile
        target.parentFile?.let { Files.createDirectories(it.toPath()) }
        Files.deleteIfExists(target.toPath())
        val safePath = sqlLiteral(target.absolutePath.replace("\\", "/"))
        val safeSessionId = sqlLiteral(sessionId)
        conn.createStatement().use { statement ->
            statement.execute(
                "COPY (SELECT * FROM telemetry_frames WHERE session_id = '$safeSessionId') " +
                    "TO '$safePath' (FORMAT PARQUET, COMPRESSION ZSTD, ROW_GROUP_SIZE 100000)"
            )
        }
    }

    /**
     * Exports multiple historical sessions into a single ZIP archive containing individual Parquet files.
     *
     * @param sessionIds List of session IDs to include in the backup.
     * @param zipFile Target `.zip` file for the exported archive.
     */
    suspend fun exportSessionsToZip(sessionIds: List<String>, zipFile: File) = withDbLock {
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile).buffered()).use { zos ->
            for (sessionId in sessionIds) {
                val tempFile = File.createTempFile("export_", ".parquet")
                try {
                    val absolutePath = tempFile.absolutePath.replace("\\", "/").replace("'", "''")
                    val safeSessionId = sessionId.replace("'", "''")
                    conn.createStatement().use { st ->
                        st.execute("COPY (SELECT * FROM telemetry_frames WHERE session_id = '$safeSessionId') TO '$absolutePath' (FORMAT PARQUET)")
                    }

                    val safeEntryName = sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    zos.putNextEntry(java.util.zip.ZipEntry("$safeEntryName.parquet"))
                    tempFile.inputStream().use { fis ->
                        fis.copyTo(zos, bufferSize = 256 * 1024)
                    }
                    zos.closeEntry()
                } finally {
                    tempFile.delete()
                }
            }
        }
    }

    private fun sqlLiteral(value: String): String = value.replace("'", "''")
}
