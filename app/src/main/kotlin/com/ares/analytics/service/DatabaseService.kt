package com.ares.analytics.service

import com.ares.analytics.shared.*
import com.ares.analytics.service.db.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import java.io.File
import java.sql.Statement
import java.sql.Connection
import java.sql.DriverManager

/**
 * High-level embedded relational database service wrapping the DuckDB C++ engine over JDBC.
 *
 * Manages persistent on-disk database files (`telemetry.duckdb`) and fast ephemeral in-memory databases (`jdbc:duckdb:`).
 * Orchestrates schema migrations via [SchemaMigrationManager], encapsulates CRUD queries via [MatchLogRepository],
 * and handles Parquet import/export routines via [DatabaseBackupExporter].
 *
 * ### Database Engine Specifications:
 * - Driver: `org.duckdb.DuckDBDriver`
 * - File Path: [dbPath] (defaults to `~/.ares-analytics/telemetry.duckdb`)
 * - Native Extensions Loaded: `parquet` (for high-speed binary trace ingestion)
 *
 * ### Thread Safety & Performance Guarantees:
 * Multi-thread safe. All write and query transactions are synchronized through an asynchronous coroutine [dbMutex] lock.
 * Delegates SQL execution to `MatchLogRepository`.
 *
 * @param dbPath Absolute filesystem path to the DuckDB database file.
 *
 * @see SchemaMigrationManager
 * @see MatchLogRepository
 * @see DatabaseBackupExporter
 */
class DatabaseService(val dbPath: String = System.getProperty("user.home") + "/.ares-analytics/telemetry.duckdb") : TelemetryAnalyticsRepository {

    private val conn: Connection
    private val readConn: Connection
    private val ephemeralConn: Connection
    private val ephemeralReadConn: Connection
    private val dbMutex = Mutex()
    private val readMutex = Mutex()
    val metrics = DatabaseMetrics()

    private val schemaManager: SchemaMigrationManager
    private val matchLogRepo: MatchLogRepository
    private val backupExporter: DatabaseBackupExporter

    private val checkpointScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var checkpointJob: Job

    init {
        Class.forName("org.duckdb.DuckDBDriver")
        val oldDbPath = System.getProperty("user.home") + "/.ares-analytics/telemetry.db"
        val isFirstRun = !File(dbPath).exists()
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()

        if (dbFile.exists() && dbFile.length() == 0L) {
            dbFile.delete()
        }

        val appDataDir = dbFile.parentFile?.absolutePath ?: (System.getProperty("user.home") + "/.ares-analytics")
        conn = DriverManager.getConnection("jdbc:duckdb:${dbFile.absolutePath}")
        readConn = conn.unwrap(org.duckdb.DuckDBConnection::class.java).duplicate()

        // Ensure parquet extension is loaded for export and configure DuckDB settings
        val tmpDirFile = File(appDataDir, "duckdb_tmp")
        tmpDirFile.mkdirs()
        conn.createStatement().use { st ->
            st.execute("SET memory_limit='1GB'")
            st.execute("SET threads=4")
            val safeTmpDir = tmpDirFile.absolutePath.replace("\\", "/").replace("'", "''")
            st.execute("SET temp_directory='$safeTmpDir'")
            st.execute("INSTALL parquet;")
            st.execute("LOAD parquet;")
        }

        ephemeralConn = DriverManager.getConnection("jdbc:duckdb:")
        ephemeralConn.createStatement().use { st ->
            st.execute("SET memory_limit='1GB'")
            st.execute("SET threads=4")
        }
        ephemeralReadConn = ephemeralConn.unwrap(org.duckdb.DuckDBConnection::class.java).duplicate()

        schemaManager = SchemaMigrationManager(conn, ephemeralConn)
        matchLogRepo = MatchLogRepository(conn, readConn, ephemeralConn, ephemeralReadConn, dbMutex, readMutex, metrics)
        backupExporter = DatabaseBackupExporter(conn, dbMutex)

        schemaManager.runMigrations(isFirstRun, oldDbPath)

        // Periodic WAL checkpoint — replaces the per-appender-batch CHECKPOINT that dominated
        // import time with fsyncs on every frame batch. A 60s cadence bounds WAL growth for
        // live streaming and bulk import alike; connection close still flushes on shutdown.
        checkpointJob = checkpointScope.launch {
            while (isActive) {
                delay(CHECKPOINT_INTERVAL_MS)
                runCatching { matchLogRepo.checkpoint() }
            }
        }
    }

    suspend fun checkpoint() = matchLogRepo.checkpoint()

    suspend fun executeRaw(sql: String) = matchLogRepo.executeRaw(sql)
    suspend fun executeNativeCsvImport(sql: String) = matchLogRepo.executeNativeCsvImport(sql)
    suspend fun executeQueryRaw(sql: String): QueryResult = matchLogRepo.executeQueryRaw(sql)
    suspend fun executeQueryWithParams(sql: String, params: List<Any>): QueryResult = matchLogRepo.executeQueryWithParams(sql, params)
    suspend fun insertSession(session: Session) = matchLogRepo.insertSession(session)
    suspend fun getSessions(): List<Session> = matchLogRepo.getSessions()
    suspend fun deleteSession(sessionId: String) = matchLogRepo.deleteSession(sessionId)
    suspend fun insertSessionSummary(summary: SessionSummary) = matchLogRepo.insertSessionSummary(summary)
    override suspend fun getSessionSummary(sessionId: String): SessionSummary? = matchLogRepo.getSessionSummary(sessionId)
    suspend fun getAllSessionSummaries(): List<SessionSummary> = matchLogRepo.getAllSessionSummaries()
    suspend fun insertTelemetryFrames(frames: List<TelemetryFrame>) = matchLogRepo.insertTelemetryFrames(frames)
    suspend fun insertRobotActionsBulk(actions: List<com.ares.analytics.shared.RobotActionRecord>) = matchLogRepo.insertRobotActionsBulk(actions)
    suspend fun getActionsForSession(sessionId: String): List<com.ares.analytics.shared.RobotActionRecord> = matchLogRepo.getActionsForSession(sessionId)
    override suspend fun getSessionTimestampRange(sessionId: String): Pair<Long, Long>? = matchLogRepo.getSessionTimestampRange(sessionId)
    suspend fun getTelemetryRange(sessionId: String, startMs: Long, endMs: Long): List<TelemetryFrame> = matchLogRepo.getTelemetryRange(sessionId, startMs, endMs)
    suspend fun getLatestTelemetryBefore(sessionId: String, timestampMs: Long): List<TelemetryFrame> = matchLogRepo.getLatestTelemetryBefore(sessionId, timestampMs)
    suspend fun getTelemetryRangeBatched(sessionId: String, startMs: Long, endMs: Long, limit: Long, offset: Long): List<TelemetryFrame> = matchLogRepo.getTelemetryRangeBatched(sessionId, startMs, endMs, limit, offset)
    suspend fun countTelemetryFrames(sessionId: String): Long = matchLogRepo.countTelemetryFrames(sessionId)
    suspend fun getTelemetryForKey(sessionId: String, key: String): List<TelemetryFrame> = matchLogRepo.getTelemetryForKey(sessionId, key)
    override suspend fun getTelemetrySeries(
        sessionId: String,
        key: String,
        startMs: Long,
        endMs: Long,
        maxPoints: Int
    ): List<TelemetryFrame> = matchLogRepo.getTelemetrySeries(sessionId, key, startMs, endMs, maxPoints)
    suspend fun getTelemetryPageForKeys(
        sessionId: String,
        keys: List<String>,
        startMs: Long,
        endMs: Long,
        limit: Int = 5_000,
        offset: Long = 0
    ): List<TelemetryFrame> = matchLogRepo.getTelemetryPageForKeys(sessionId, keys, startMs, endMs, limit, offset)
    override suspend fun getDistinctTelemetryKeys(sessionId: String): List<String> = matchLogRepo.getDistinctTelemetryKeys(sessionId)
    suspend fun getTelemetryForKeyPatterns(sessionId: String, patterns: List<String>): List<TelemetryFrame> =
        matchLogRepo.getTelemetryForKeyPatterns(sessionId, patterns)
    suspend fun getDiagnosticsTelemetry(sessionId: String): List<TelemetryFrame> = matchLogRepo.getDiagnosticsTelemetry(sessionId)
    suspend fun getTelemetryForFilters(sessionId: String, keys: List<String>, prefixes: List<String>): List<TelemetryFrame> = matchLogRepo.getTelemetryForFilters(sessionId, keys, prefixes)
    suspend fun getDistinctTimestamps(sessionId: String): List<Long> = matchLogRepo.getDistinctTimestamps(sessionId)
    suspend fun deleteTelemetryFrames(sessionId: String) = matchLogRepo.deleteTelemetryFrames(sessionId)
    suspend fun pruneTelemetryFrames(sessionId: String, cutoffMs: Long) = matchLogRepo.pruneTelemetryFrames(sessionId, cutoffMs)
    suspend fun insertAnnotation(annotation: SessionAnnotation) = matchLogRepo.insertAnnotation(annotation)
    suspend fun getAnnotations(sessionId: String): List<SessionAnnotation> = matchLogRepo.getAnnotations(sessionId)
    suspend fun updateSessionTags(sessionId: String, tags: List<String>) = matchLogRepo.updateSessionTags(sessionId, tags)
    suspend fun updateSessionMatchDetails(sessionId: String, matchNumber: Int?, allianceColor: String?) = matchLogRepo.updateSessionMatchDetails(sessionId, matchNumber, allianceColor)
    suspend fun associateSessionWithMatch(sessionId: String, matchNumber: Int, allianceColor: String, opponentTeams: List<String>) = matchLogRepo.associateSessionWithMatch(sessionId, matchNumber, allianceColor, opponentTeams)
    suspend fun insertAlert(alert: AlertRecord) = matchLogRepo.insertAlert(alert)
    suspend fun getAlerts(sessionId: String): List<AlertRecord> = matchLogRepo.getAlerts(sessionId)
    suspend fun insertTopology(topology: HardwareTopology) = matchLogRepo.insertTopology(topology)
    suspend fun getTopology(robotId: String): HardwareTopology? = matchLogRepo.getTopology(robotId)
    suspend fun insertConsoleMessages(messages: List<ConsoleMessage>, sessionId: String) = matchLogRepo.insertConsoleMessages(messages, sessionId)
    suspend fun getConsoleMessages(sessionId: String): List<ConsoleMessage> = matchLogRepo.getConsoleMessages(sessionId)
    suspend fun getTelemetryDensity(sessionId: String, buckets: Int = 100): List<Float> = matchLogRepo.getTelemetryDensity(sessionId, buckets)

    suspend fun importParquet(file: File) = backupExporter.importParquet(file)
    suspend fun importParquetAsSession(file: File, sessionId: String) =
        backupExporter.importParquetAsSession(file, sessionId)
    suspend fun exportSessionToParquet(sessionId: String, file: File) =
        backupExporter.exportSessionToParquet(sessionId, file)

    fun close() = runBlocking {
        // Stop the periodic checkpoint timer first so it can't fire mid-teardown.
        checkpointScope.cancel()
        dbMutex.withLock {
            matchLogRepo.dispose()
            if (!conn.isClosed) { conn.close() }
            if (!readConn.isClosed) { readConn.close() }
            if (!ephemeralReadConn.isClosed) { ephemeralReadConn.close() }
            if (!ephemeralConn.isClosed) { ephemeralConn.close() }
        }
    }

    companion object {
        /** Periodic CHECKPOINT cadence (ms). */
        private const val CHECKPOINT_INTERVAL_MS = 60_000L
    }
}
