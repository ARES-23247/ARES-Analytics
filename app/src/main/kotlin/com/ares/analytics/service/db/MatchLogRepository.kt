package com.ares.analytics.service.db

import com.ares.analytics.shared.*
import com.ares.analytics.service.QueryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.duckdb.DuckDBAppender
import org.duckdb.DuckDBConnection
import java.sql.Connection
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicLong

/**
 * Primary repository interface for telemetry persistent storage, DuckDB vectorized queries, and match history.
 *
 * Provides thread-safe transaction execution over DuckDB JDBC connections, utilizing DuckDB's native Appender C++ API
 * (`insertTelemetryFramesAppender`, `insertRobotActionsBulk`) for bulk frame ingest (~10-100x faster than traditional JDBC SQL batches).
 *
 * ### Physical Units & Storage Targets:
 * - Timestamps: Milliseconds ($ms$)
 * - Telemetry keys: Normalized NT4 paths (`"Drive/Pose_X"`, `"Hardware/Motors/fl/Power"`)
 * - Battery Voltage metrics: Volts ($V$)
 * - Motor Current metrics: Amperes ($A$)
 * - Loop timing: Milliseconds ($ms$)
 * - Vision latency: Milliseconds ($ms$)
 * - EKF position drift / cross-track error: Meters ($m$)
 *
 * ### Thread Safety & Performance Guarantees:
 * Thread-safe suspend functions executing DB transactions under mutual exclusion lock ([dbMutex]) on [Dispatchers.IO].
 * Appender operations stream raw memory arrays to DuckDB C++ native buffers with zero JVM heap fragmentation.
 *
 * @param conn Primary DuckDB connection bound to disk storage.
 * @param ephemeralConn In-memory DuckDB connection for high-throughput live telemetry buffers.
 * @param dbMutex Mutual exclusion coroutine lock controlling connection write concurrency.
 *
 * @see SchemaMigrationManager
 * @see DatabaseBackupExporter
 * @see QueryResult
 */
class MatchLogRepository(
    private val conn: Connection,
    private val readConn: Connection,
    private val ephemeralConn: Connection,
    private val ephemeralReadConn: Connection,
    private val dbMutex: Mutex,
    private val readMutex: Mutex
) {
    private val statementCache = java.util.concurrent.ConcurrentHashMap<String, java.sql.PreparedStatement>()
    private val nextSampleOrder = AtomicLong()

    private fun readConnectionFor(sessionId: String): Connection =
        if (sessionId == "live-telemetry") ephemeralReadConn else readConn

    private fun storageOrder(frame: TelemetryFrame): Long =
        if (frame.sampleOrder != 0L) frame.sampleOrder else nextSampleOrder.incrementAndGet()
    /**
     * Executes a raw database operation safely under [dbMutex] on [Dispatchers.IO].
     *
     * @param T Result type of the transaction block.
     * @param block Database execution logic.
     * @return Result produced by [block].
     */
    private suspend fun <T> withDbLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        dbMutex.withLock { block() }
    }

    private suspend fun <T> withReadLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        readMutex.withLock { block() }
    }

    /**
     * Final teardown — closes and clears the [statementCache]. Call from [DatabaseService.close]
     * before closing the underlying connections so cached PreparedStatements don't leak.
     */
    fun dispose() {
        statementCache.values.forEach { runCatching { it.close() } }
        statementCache.clear()
    }

    /**
     * Executes an arbitrary SQL execution string (DDL/DML) on the primary connection.
     *
     * @param sql Raw SQL statement string.
     */
    suspend fun executeRaw(sql: String) = withDbLock {
        val normalized = sql.trim().trimEnd(';').uppercase()
        val isSelect = normalized.startsWith("SELECT")
        val forbidden = listOf("DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE", "TRUNCATE", "EXEC")
        val hasForbidden = forbidden.any { Regex("\\b$it\\b").containsMatchIn(normalized) }

        if (!isSelect || hasForbidden) {
            println("Rejected dangerous SQL: $sql")
            throw IllegalArgumentException("Raw query rejected: Only read-only SELECT queries are allowed.")
        }
        conn.createStatement().use { it.execute(sql) }
    }

    suspend fun executeNativeCsvImport(sql: String) = withDbLock {
        if (!sql.trim().uppercase().startsWith("INSERT INTO TELEMETRY_FRAMES")) {
            throw IllegalArgumentException("executeNativeCsvImport only allows INSERT INTO telemetry_frames")
        }
        conn.createStatement().use { it.execute(sql) }
    }

    suspend fun executeQueryRaw(sql: String): QueryResult = withReadLock {
        // Primary guard: whitelist the first non-whitespace token. Only read-only
        // statement leaders are permitted. This runs BEFORE any execution.
        val normalized = sql.trim().trimEnd(';').trim().uppercase()
        val firstToken = Regex("^[A-Z]+").find(normalized)?.value ?: ""
        val allowedLeaders = setOf("SELECT", "WITH", "VALUES", "TABLE", "SHOW", "DESCRIBE", "EXPLAIN")
        if (firstToken !in allowedLeaders) {
            throw IllegalArgumentException("Raw query rejected: only read-only query leaders are allowed (got '$firstToken').")
        }
        // Defense-in-depth keyword denylist (expanded): even with a SELECT leader, block
        // statements that smuggle in writes, side-effects, or exfiltration primitives the
        // read-only transaction might not fully neutralize (e.g. EXPORT DATABASE).
        val forbidden = listOf(
            "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE", "ATTACH", "DETACH",
            "INSTALL", "LOAD", "PRAGMA", "COPY", "TRUNCATE", "EXECUTE", "CALL", "VACUUM",
            "EXPORT", "SET", "USE", "IMPORT"
        )
        val hasForbidden = forbidden.any { Regex("\\b$it\\b").containsMatchIn(normalized) }
        if (hasForbidden) {
            throw IllegalArgumentException("Raw query rejected: query contains a disallowed modification/side-effect keyword.")
        }
        // Enforce read-only at the engine level: wrap the query in a READ ONLY transaction
        // so any write attempt (even one that slipped past the token/keyword guards) is
        // rejected by DuckDB itself. Executed on readConn (separate from the writer conn).
        readConn.createStatement().use { it.execute("BEGIN TRANSACTION READ ONLY") }
        try {
            val result = readConn.createStatement().use { st ->
                val hasResultSet = st.execute(sql)
                if (hasResultSet) {
                    st.resultSet.use { rs ->
                        val meta = rs.metaData
                        val colCount = meta.columnCount
                        val columns = (1..colCount).map { meta.getColumnName(it) }
                        val rows = mutableListOf<List<String>>()
                        while (rs.next()) {
                            val row = (1..colCount).map {
                                rs.getObject(it)?.toString() ?: "NULL"
                            }
                            rows.add(row)
                        }
                        QueryResult(columns, rows)
                    }
                } else {
                    val updateCount = st.updateCount
                    QueryResult(
                        columns = listOf("Status"),
                        rows = listOf(listOf("Command completed successfully. Affected rows: $updateCount"))
                    )
                }
            }
            readConn.createStatement().use { it.execute("COMMIT") }
            result
        } catch (e: Exception) {
            runCatching { readConn.createStatement().use { it.execute("ROLLBACK") } }
            throw e
        }
    }

    /**
     * Execute a parameterized SQL query and return results as [QueryResult].
     * Use this for queries with user-provided values to prevent SQL injection.
     */
    suspend fun executeQueryWithParams(sql: String, params: List<Any>): QueryResult = withReadLock {
        readConn.prepareStatement(sql).use { ps ->
            params.forEachIndexed { index, param ->
                when (param) {
                    is String -> ps.setString(index + 1, param)
                    is Long -> ps.setLong(index + 1, param)
                    is Int -> ps.setInt(index + 1, param)
                    is Double -> ps.setDouble(index + 1, param)
                    else -> ps.setObject(index + 1, param)
                }
            }
            val hasResultSet = ps.execute()
            if (hasResultSet) {
                ps.resultSet.use { rs ->
                    val meta = rs.metaData
                    val colCount = meta.columnCount
                    val columns = (1..colCount).map { meta.getColumnName(it) }
                    val rows = mutableListOf<List<String>>()
                    while (rs.next()) {
                        val row = (1..colCount).map {
                            rs.getObject(it)?.toString() ?: "NULL"
                        }
                        rows.add(row)
                    }
                    QueryResult(columns, rows)
                }
            } else {
                val updateCount = ps.updateCount
                QueryResult(
                    columns = listOf("Status"),
                    rows = listOf(listOf("Command completed successfully. Affected rows: $updateCount"))
                )
            }
        }
    }

    suspend fun insertSession(session: Session) = withDbLock {
        conn.prepareStatement(
            "INSERT OR REPLACE INTO sessions (session_id, team_id, season_id, robot_id, created_at, duration_ms, tags, match_number, alliance_color) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, session.sessionId)
            ps.setString(2, session.teamId)
            ps.setString(3, session.seasonId)
            ps.setString(4, session.robotId)
            ps.setLong(5, session.createdAt)
            ps.setLong(6, session.durationMs)
            ps.setString(7, Json.encodeToString(session.tags))
            session.matchNumber?.let { ps.setLong(8, it.toLong()) } ?: ps.setNull(8, java.sql.Types.BIGINT)
            ps.setString(9, session.allianceColor)
            ps.executeUpdate()
        }
    }

    suspend fun getSessions(): List<Session> = withDbLock {
        val list = mutableListOf<Session>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT * FROM sessions ORDER BY created_at DESC").use { rs ->
                while (rs.next()) list.add(rs.toSession())
            }
        }
        list
    }

    suspend fun deleteSession(sessionId: String) = withDbLock {
        val previousAutoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            val sessionOwnedTables = arrayOf(
                "session_summaries",
                "telemetry_frames",
                "session_annotations",
                "alerts",
                "console_messages",
                "robot_actions",
                "sessions"
            )
            for (table in sessionOwnedTables) {
                conn.prepareStatement("DELETE FROM $table WHERE session_id = ?").use { ps ->
                    ps.setString(1, sessionId)
                    ps.executeUpdate()
                }
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = previousAutoCommit
        }
    }

    suspend fun insertSessionSummary(summary: SessionSummary) = withDbLock {
        conn.prepareStatement(
            "INSERT OR REPLACE INTO session_summaries (session_id, team_id, season_id, robot_id, created_at, duration_ms, min_battery_voltage, max_ekf_drift, avg_loop_time_ms, p95_loop_time_ms, motor_current_averages, vision_acceptance_rate, avg_cross_track_error, avg_battery_resistance, max_motor_temps, avg_vision_latency_ms, tags, match_number, alliance_color) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, summary.sessionId)
            ps.setString(2, summary.teamId)
            ps.setString(3, summary.seasonId)
            ps.setString(4, summary.robotId)
            ps.setLong(5, summary.createdAt)
            ps.setLong(6, summary.durationMs)
            ps.setDouble(7, summary.minBatteryVoltage)
            ps.setDouble(8, summary.maxEkfDrift)
            ps.setDouble(9, summary.avgLoopTimeMs)
            ps.setDouble(10, summary.p95LoopTimeMs)
            ps.setString(11, Json.encodeToString(summary.motorCurrentAverages))
            ps.setDouble(12, summary.visionAcceptanceRate)
            ps.setDouble(13, summary.avgCrossTrackError)
            ps.setDouble(14, summary.avgBatteryResistance)
            ps.setString(15, Json.encodeToString(summary.maxMotorTemps))
            ps.setDouble(16, summary.avgVisionLatencyMs)
            ps.setString(17, Json.encodeToString(summary.tags))
            summary.matchNumber?.let { ps.setLong(18, it.toLong()) } ?: ps.setNull(18, java.sql.Types.BIGINT)
            ps.setString(19, summary.allianceColor)
            ps.executeUpdate()
        }
    }

    suspend fun getSessionSummary(sessionId: String): SessionSummary? = withReadLock {
        readConn.prepareStatement("SELECT * FROM session_summaries WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.toSessionSummary() else null
            }
        }
    }

    suspend fun getAllSessionSummaries(): List<SessionSummary> = withDbLock {
        val list = mutableListOf<SessionSummary>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT * FROM session_summaries ORDER BY created_at DESC").use { rs ->
                while (rs.next()) list.add(rs.toSessionSummary())
            }
        }
        list
    }

    suspend fun insertTelemetryFrames(frames: List<TelemetryFrame>) = withDbLock {
        if (frames.isEmpty()) return@withDbLock

        // A single channel flush can straddle the moment a recording starts or stops.
        // Route every row by its own session identity instead of trusting frame[0].
        val liveFrames = frames.filter { it.sessionId == "live-telemetry" }
        val persistentFrames = frames.filter { it.sessionId != "live-telemetry" }
        if (persistentFrames.isNotEmpty()) insertTelemetryFrames(conn, persistentFrames)
        if (liveFrames.isNotEmpty()) insertTelemetryFrames(ephemeralConn, liveFrames)
    }

    private fun insertTelemetryFrames(targetConn: Connection, frames: List<TelemetryFrame>) {
        val previousAutoCommit = targetConn.autoCommit
        if (previousAutoCommit) targetConn.autoCommit = false
        try {
            if (targetConn === conn) {
                // Use DuckDB Appender for persistent storage — bypasses SQL parser entirely
                insertTelemetryFramesAppender(frames)
            } else {
                // Ephemeral connection (live telemetry) uses JDBC batch for INSERT OR REPLACE
                insertTelemetryFramesJdbc(targetConn, frames)
            }
            if (previousAutoCommit) targetConn.commit()
        } catch (e: Exception) {
            if (previousAutoCommit) runCatching { targetConn.rollback() }
            throw e
        } finally {
            if (previousAutoCommit) targetConn.autoCommit = true
        }
    }

    /**
     * High-performance bulk insert using DuckDB's native Appender API.
     * Bypasses SQL parsing and writes directly to columnar storage.
     * ~10-100x faster than JDBC PreparedStatement batch for bulk imports.
     *
     * IMPORTANT: Must be called under withDbLock or from a single-writer context.
     * Does not support INSERT OR REPLACE — assumes no duplicate keys (safe for imports).
     */
    private fun insertTelemetryFramesAppender(frames: List<TelemetryFrame>) {
        val duckConn = conn.unwrap(DuckDBConnection::class.java)
        val appender = duckConn.createAppender(DuckDBConnection.DEFAULT_SCHEMA, "telemetry_frames")
        try {
            for (frame in frames) {
                appender.beginRow()
                appender.append(frame.timestampMs)
                appender.append(frame.sessionId)
                appender.append(TelemetryMetricCatalog.normalizeTopic(frame.key))
                appender.append(frame.value)
                // DuckDBAppender maps a nullable String to SQL NULL. Empty string is a
                // legitimate telemetry value and must remain distinguishable from null.
                appender.append(frame.stringValue)
                appender.append(frame.timestampUs)
                appender.append(storageOrder(frame))
                appender.endRow()
            }
            appender.flush()
        } finally {
            appender.close()
            // CHECKPOINT intentionally NOT run per batch — a per-batch WAL fsync dominated
            // import time. Checkpointing is now caller/timer-controlled via [checkpoint]
            // (DatabaseService runs it on a periodic timer; connection close still flushes).
        }
    }

    /**
     * Forces a WAL checkpoint on the persistent connection. Caller/timer-controlled so it
     * runs once per import job or periodically, not after every appender batch.
     */
    suspend fun checkpoint() = withDbLock {
        conn.createStatement().use { it.execute("CHECKPOINT") }
    }

    /**
     * High-performance bulk insert for RobotAction records using DuckDB's native Appender API.
     * Stores Redux-style action log entries from the robot's ActionLogger JSONL output.
     */
    suspend fun insertRobotActionsBulk(actions: List<com.ares.analytics.shared.RobotActionRecord>) = withDbLock {
        if (actions.isEmpty()) return@withDbLock
        val duckConn = conn.unwrap(DuckDBConnection::class.java)
        val appender = duckConn.createAppender(DuckDBConnection.DEFAULT_SCHEMA, "robot_actions")
        try {
            for (action in actions) {
                appender.beginRow()
                appender.append(action.timestampMs)
                appender.append(action.sessionId)
                appender.append(action.runId)
                appender.append(action.robotId)
                appender.append(action.matchNumber)
                appender.append(action.alliance)
                appender.append(action.actionType)
                appender.append(action.payloadJson)
                appender.endRow()
            }
            appender.flush()
        } finally {
            appender.close()
        }
    }

    /**
     * Retrieves all robot actions for a given session, ordered chronologically.
     */
    suspend fun getActionsForSession(sessionId: String): List<com.ares.analytics.shared.RobotActionRecord> = withDbLock {
        val list = mutableListOf<com.ares.analytics.shared.RobotActionRecord>()
        conn.prepareStatement(
            "SELECT timestamp_ms, session_id, run_id, robot_id, match_number, alliance, action_type, payload_json FROM robot_actions WHERE session_id = ? ORDER BY timestamp_ms"
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(com.ares.analytics.shared.RobotActionRecord(
                        timestampMs = rs.getLong("timestamp_ms"),
                        sessionId = rs.getString("session_id"),
                        runId = rs.getString("run_id"),
                        robotId = rs.getString("robot_id"),
                        matchNumber = rs.getInt("match_number"),
                        alliance = rs.getString("alliance"),
                        actionType = rs.getString("action_type"),
                        payloadJson = rs.getString("payload_json")
                    ))
                }
            }
        }
        list
    }

    /**
     * JDBC PreparedStatement batch insert with INSERT OR REPLACE.
     * Used for live-telemetry on the ephemeral connection where deduplication matters.
     */
    private fun insertTelemetryFramesJdbc(targetConn: Connection, frames: List<TelemetryFrame>) {
        targetConn.prepareStatement(
            """
                INSERT OR REPLACE INTO telemetry_frames
                    (timestamp_ms, session_id, key, value, string_value, timestamp_us, sample_order)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            frames.forEachIndexed { index, frame ->
                statement.setLong(1, frame.timestampMs)
                statement.setString(2, frame.sessionId)
                statement.setString(3, TelemetryMetricCatalog.normalizeTopic(frame.key))
                statement.setDouble(4, frame.value)
                if (frame.stringValue == null) {
                    statement.setNull(5, java.sql.Types.VARCHAR)
                } else {
                    statement.setString(5, frame.stringValue)
                }
                statement.setLong(6, frame.timestampUs)
                statement.setLong(7, storageOrder(frame))
                statement.addBatch()

                // Bound driver-side batch memory during sustained live telemetry.
                if ((index + 1) % 1_000 == 0) statement.executeBatch()
            }
            statement.executeBatch()
        }
    }

    /**
     * Returns the (min, max) timestamp range for a given session's telemetry frames,
     * or null if no frames exist. Used after DuckDB native CSV import to compute
     * session duration without holding frames in application memory.
     */
    suspend fun getSessionTimestampRange(sessionId: String): Pair<Long, Long>? = withReadLock {
        readConnectionFor(sessionId).prepareStatement("SELECT MIN(timestamp_ms), MAX(timestamp_ms) FROM telemetry_frames WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    val min = rs.getLong(1)
                    val max = rs.getLong(2)
                    if (rs.wasNull()) null else Pair(min, max)
                } else null
            }
        }
    }

    suspend fun getTelemetryRange(sessionId: String, startMs: Long, endMs: Long): List<TelemetryFrame> = withReadLock {
        val targetConn = readConnectionFor(sessionId)
        val list = mutableListOf<TelemetryFrame>()
        targetConn.prepareStatement("SELECT * FROM telemetry_frames WHERE session_id = ? AND timestamp_ms BETWEEN ? AND ? ORDER BY timestamp_us ASC, sample_order ASC").use { ps ->
            ps.setString(1, sessionId)
            ps.setLong(2, startMs)
            ps.setLong(3, endMs)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toTelemetryFrame())
            }
        }
        list
    }

    /**
     * Returns the most recent value for every topic strictly before [timestampMs].
     * Replay uses this as the latched-state baseline when it loads a bounded window.
     */
    suspend fun getLatestTelemetryBefore(sessionId: String, timestampMs: Long): List<TelemetryFrame> = withReadLock {
        val targetConn = readConnectionFor(sessionId)
        val list = mutableListOf<TelemetryFrame>()
        targetConn.prepareStatement(
            """
            SELECT timestamp_ms, session_id, key, value, string_value, timestamp_us, sample_order
            FROM telemetry_frames
            WHERE session_id = ? AND timestamp_ms < ?
            QUALIFY ROW_NUMBER() OVER (PARTITION BY key ORDER BY timestamp_us DESC, sample_order DESC) = 1
            ORDER BY timestamp_us ASC, sample_order ASC
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.setLong(2, timestampMs)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toTelemetryFrame())
            }
        }
        list
    }

    suspend fun getTelemetryRangeBatched(sessionId: String, startMs: Long, endMs: Long, limit: Long, offset: Long): List<TelemetryFrame> = withReadLock {
        val targetConn = readConnectionFor(sessionId)
        val list = mutableListOf<TelemetryFrame>()
        targetConn.prepareStatement("SELECT * FROM telemetry_frames WHERE session_id = ? AND timestamp_ms BETWEEN ? AND ? ORDER BY timestamp_us ASC, sample_order ASC LIMIT ? OFFSET ?").use { ps ->
            ps.setString(1, sessionId)
            ps.setLong(2, startMs)
            ps.setLong(3, endMs)
            ps.setLong(4, limit)
            ps.setLong(5, offset)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toTelemetryFrame())
            }
        }
        list
    }

    suspend fun countTelemetryFrames(sessionId: String): Long = withReadLock {
        readConnectionFor(sessionId).prepareStatement("SELECT COUNT(*) FROM telemetry_frames WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    suspend fun getTelemetryForKey(sessionId: String, key: String): List<TelemetryFrame> = withReadLock {
        val list = mutableListOf<TelemetryFrame>()
        readConnectionFor(sessionId).prepareStatement("SELECT * FROM telemetry_frames WHERE session_id = ? AND key = ? ORDER BY timestamp_us ASC, sample_order ASC").use { ps ->
            ps.setString(1, sessionId)
            ps.setString(2, TelemetryMetricCatalog.normalizeTopic(key))
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toTelemetryFrame())
            }
        }
        list
    }

    /**
     * Returns a plot-ready numeric series bounded by [maxPoints]. DuckDB performs min/max
     * aggregation per viewport bucket so short spikes remain visible without materializing the
     * complete session in the desktop process.
     */
    suspend fun getTelemetrySeries(
        sessionId: String,
        key: String,
        startMs: Long,
        endMs: Long,
        maxPoints: Int
    ): List<TelemetryFrame> = withReadLock {
        require(endMs >= startMs) { "endMs must be greater than or equal to startMs" }
        require(maxPoints >= 2) { "maxPoints must be at least 2" }
        val bucketCount = (maxPoints / 2).coerceAtLeast(1)
        val durationMs = (endMs - startMs + 1).coerceAtLeast(1)
        val bucketWidthMs = kotlin.math.ceil(durationMs.toDouble() / bucketCount).toLong().coerceAtLeast(1)
        val normalizedKey = TelemetryMetricCatalog.normalizeTopic(key)
        val points = mutableListOf<TelemetryFrame>()
        val sql = """
            WITH bucketed AS (
                SELECT
                    FLOOR((timestamp_ms - ?) / ?)::BIGINT AS bucket_id,
                    MIN(value) AS min_value,
                    ARG_MIN(timestamp_ms, value) AS min_timestamp_ms,
                    ARG_MIN(timestamp_us, value) AS min_timestamp_us,
                    ARG_MIN(sample_order, value) AS min_sample_order,
                    MAX(value) AS max_value,
                    ARG_MAX(timestamp_ms, value) AS max_timestamp_ms,
                    ARG_MAX(timestamp_us, value) AS max_timestamp_us,
                    ARG_MAX(sample_order, value) AS max_sample_order
                FROM telemetry_frames
                WHERE session_id = ? AND key = ? AND timestamp_ms BETWEEN ? AND ?
                GROUP BY bucket_id
            ), plot_points AS (
                SELECT min_timestamp_ms AS timestamp_ms, min_value AS value,
                       min_timestamp_us AS timestamp_us, min_sample_order AS sample_order
                FROM bucketed
                UNION ALL
                SELECT max_timestamp_ms, max_value, max_timestamp_us, max_sample_order
                FROM bucketed
                WHERE max_timestamp_us <> min_timestamp_us
                   OR max_sample_order <> min_sample_order
                   OR max_value <> min_value
            )
            SELECT timestamp_ms, value, timestamp_us, sample_order
            FROM plot_points
            ORDER BY timestamp_us ASC, sample_order ASC
            LIMIT ?
        """.trimIndent()
        readConnectionFor(sessionId).prepareStatement(sql).use { ps ->
            ps.setLong(1, startMs)
            ps.setLong(2, bucketWidthMs)
            ps.setString(3, sessionId)
            ps.setString(4, normalizedKey)
            ps.setLong(5, startMs)
            ps.setLong(6, endMs)
            ps.setInt(7, maxPoints)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    points.add(
                        TelemetryFrame(
                            timestampMs = rs.getLong("timestamp_ms"),
                            sessionId = sessionId,
                            key = normalizedKey,
                            value = rs.getDouble("value"),
                            timestampUs = rs.getLong("timestamp_us"),
                            sampleOrder = rs.getLong("sample_order")
                        )
                    )
                }
            }
        }
        points
    }

    /** A bounded, ordered table page for a set of visible telemetry columns. */
    suspend fun getTelemetryPageForKeys(
        sessionId: String,
        keys: List<String>,
        startMs: Long,
        endMs: Long,
        limit: Int,
        offset: Long
    ): List<TelemetryFrame> = withReadLock {
        require(endMs >= startMs) { "endMs must be greater than or equal to startMs" }
        require(limit in 1..50_000) { "limit must be between 1 and 50000" }
        require(offset >= 0) { "offset must not be negative" }
        if (keys.isEmpty()) return@withReadLock emptyList()

        val normalizedKeys = keys.distinct().map(TelemetryMetricCatalog::normalizeTopic)
        val placeholders = normalizedKeys.joinToString(",") { "?" }
        val sql = """
            SELECT * FROM telemetry_frames
            WHERE session_id = ?
              AND key IN ($placeholders)
              AND timestamp_ms BETWEEN ? AND ?
            ORDER BY timestamp_us ASC, sample_order ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        val frames = mutableListOf<TelemetryFrame>()
        readConnectionFor(sessionId).prepareStatement(sql).use { ps ->
            var parameter = 1
            ps.setString(parameter++, sessionId)
            normalizedKeys.forEach { ps.setString(parameter++, it) }
            ps.setLong(parameter++, startMs)
            ps.setLong(parameter++, endMs)
            ps.setInt(parameter++, limit)
            ps.setLong(parameter, offset)
            ps.executeQuery().use { rs ->
                while (rs.next()) frames.add(rs.toTelemetryFrame())
            }
        }
        frames
    }

    suspend fun getDiagnosticsTelemetry(sessionId: String): List<TelemetryFrame> = withDbLock {
        val list = mutableListOf<TelemetryFrame>()
        conn.prepareStatement("SELECT * FROM telemetry_frames WHERE session_id = ? AND key LIKE 'Diagnostics/%'").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toTelemetryFrame())
            }
        }
        list
    }
    suspend fun getTelemetryForFilters(sessionId: String, keys: List<String>, prefixes: List<String>): List<TelemetryFrame> = withReadLock {
        val list = mutableListOf<TelemetryFrame>()
        val queryBuilder = StringBuilder("SELECT * FROM telemetry_frames WHERE session_id = ?")
        val conditions = mutableListOf<String>()
        if (keys.isNotEmpty()) {
            val placeholders = keys.joinToString(",") { "?" }
            conditions.add("key IN ($placeholders)")
        }
        if (prefixes.isNotEmpty()) {
            val likeConditions = prefixes.joinToString(" OR ") { "LOWER(key) LIKE LOWER(?)" }
            conditions.add("($likeConditions)")
        }
        if (conditions.isEmpty()) return@withReadLock list
        queryBuilder.append(" AND (").append(conditions.joinToString(" OR ")).append(") ORDER BY timestamp_us ASC, sample_order ASC")

        readConnectionFor(sessionId).prepareStatement(queryBuilder.toString()).use { ps ->
            ps.setString(1, sessionId)
            var idx = 2
            for (k in keys) {
                ps.setString(idx++, TelemetryMetricCatalog.normalizeTopic(k))
            }
            for (p in prefixes) {
                ps.setString(idx++, TelemetryMetricCatalog.normalizeTopic(p))
            }
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toTelemetryFrame())
            }
        }
        list
    }

    suspend fun getDistinctTelemetryKeys(sessionId: String): List<String> = withReadLock {
        val keys = mutableListOf<String>()
        readConnectionFor(sessionId).prepareStatement(
            "SELECT DISTINCT key FROM telemetry_frames WHERE session_id = ? ORDER BY key"
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) keys.add(rs.getString(1))
            }
        }
        keys
    }

    suspend fun getTelemetryForKeyPatterns(sessionId: String, patterns: List<String>): List<TelemetryFrame> =
        getTelemetryForFilters(sessionId, emptyList(), patterns)

    suspend fun getDistinctTimestamps(sessionId: String): List<Long> = withReadLock {
        val list = mutableListOf<Long>()
        readConnectionFor(sessionId).prepareStatement("SELECT DISTINCT timestamp_ms FROM telemetry_frames WHERE session_id = ? ORDER BY timestamp_ms ASC").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.getLong(1))
            }
        }
        list
    }

    suspend fun deleteTelemetryFrames(sessionId: String) = withDbLock {
        // Route by sessionId to the correct connection: "live-telemetry" frames live in
        // ephemeralConn (see insertTelemetryFrames routing). Deleting on `conn` would be a
        // silent no-op and let the ephemeral buffer grow unbounded.
        val targetConn = if (sessionId == "live-telemetry") ephemeralConn else conn
        targetConn.prepareStatement("DELETE FROM telemetry_frames WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeUpdate()
        }
    }

    suspend fun pruneTelemetryFrames(sessionId: String, cutoffMs: Long) = withDbLock {
        // Same routing as deleteTelemetryFrames / getTelemetryRange: live frames are in
        // ephemeralConn, so the 5-min live prune must target that connection.
        val targetConn = if (sessionId == "live-telemetry") ephemeralConn else conn
        targetConn.prepareStatement("DELETE FROM telemetry_frames WHERE session_id = ? AND timestamp_ms < ?").use { ps ->
            ps.setString(1, sessionId)
            ps.setLong(2, cutoffMs)
            ps.executeUpdate()
        }
    }

    suspend fun insertAnnotation(annotation: SessionAnnotation) = withDbLock {
        conn.prepareStatement("INSERT OR REPLACE INTO session_annotations (annotation_id, session_id, text, created_at, author_id) VALUES (?, ?, ?, ?, ?)").use { ps ->
            ps.setString(1, annotation.annotationId)
            ps.setString(2, annotation.sessionId)
            ps.setString(3, annotation.text)
            ps.setLong(4, annotation.createdAt)
            ps.setString(5, annotation.authorId)
            ps.executeUpdate()
        }
    }

    suspend fun getAnnotations(sessionId: String): List<SessionAnnotation> = withDbLock {
        val list = mutableListOf<SessionAnnotation>()
        conn.prepareStatement("SELECT * FROM session_annotations WHERE session_id = ? ORDER BY created_at ASC").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toSessionAnnotation())
            }
        }
        list
    }

    suspend fun updateSessionTags(sessionId: String, tags: List<String>) = withDbLock {
        conn.prepareStatement("UPDATE sessions SET tags = ? WHERE session_id = ?").use { ps ->
            ps.setString(1, Json.encodeToString(tags))
            ps.setString(2, sessionId)
            ps.executeUpdate()
        }
        conn.prepareStatement("UPDATE session_summaries SET tags = ? WHERE session_id = ?").use { ps ->
            ps.setString(1, Json.encodeToString(tags))
            ps.setString(2, sessionId)
            ps.executeUpdate()
        }
    }

    suspend fun updateSessionMatchDetails(sessionId: String, matchNumber: Int?, allianceColor: String?) = withDbLock {
        conn.prepareStatement("UPDATE sessions SET match_number = ?, alliance_color = ? WHERE session_id = ?").use { ps ->
            if (matchNumber != null) ps.setLong(1, matchNumber.toLong()) else ps.setNull(1, java.sql.Types.BIGINT)
            ps.setString(2, allianceColor)
            ps.setString(3, sessionId)
            ps.executeUpdate()
        }
        conn.prepareStatement("UPDATE session_summaries SET match_number = ?, alliance_color = ? WHERE session_id = ?").use { ps ->
            if (matchNumber != null) ps.setLong(1, matchNumber.toLong()) else ps.setNull(1, java.sql.Types.BIGINT)
            ps.setString(2, allianceColor)
            ps.setString(3, sessionId)
            ps.executeUpdate()
        }
    }

    suspend fun associateSessionWithMatch(sessionId: String, matchNumber: Int, allianceColor: String, opponentTeams: List<String>) {
        updateSessionMatchDetails(sessionId, matchNumber, allianceColor)
    }

    suspend fun insertAlert(alert: AlertRecord) = withDbLock {
        conn.prepareStatement("INSERT OR REPLACE INTO alerts (alert_id, session_id, rule_key, trigger_timestamp_ms, resolve_timestamp_ms, duration_ms, peak_value, triaged) VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use { ps ->
            ps.setString(1, alert.alertId)
            ps.setString(2, alert.sessionId)
            ps.setString(3, alert.ruleKey)
            ps.setLong(4, alert.triggerTimestampMs)
            alert.resolveTimestampMs?.let { ps.setLong(5, it) }
                ?: ps.setNull(5, java.sql.Types.BIGINT)
            ps.setLong(6, alert.durationMs)
            ps.setDouble(7, alert.peakValue)
            ps.setLong(8, if (alert.triaged) 1L else 0L)
            ps.executeUpdate()
        }
    }

    suspend fun getAlerts(sessionId: String): List<AlertRecord> = withDbLock {
        val list = mutableListOf<AlertRecord>()
        conn.prepareStatement("SELECT * FROM alerts WHERE session_id = ? ORDER BY trigger_timestamp_ms ASC").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toAlertRecord())
            }
        }
        list
    }

    suspend fun insertTopology(topology: HardwareTopology) = withDbLock {
        conn.prepareStatement("INSERT OR REPLACE INTO cached_topologies (robot_id, topology_json) VALUES (?, ?)").use { ps ->
            ps.setString(1, topology.robotId)
            ps.setString(2, Json.encodeToString(topology))
            ps.executeUpdate()
        }
    }

    suspend fun getTopology(robotId: String): HardwareTopology? = withDbLock {
        conn.prepareStatement("SELECT topology_json FROM cached_topologies WHERE robot_id = ?").use { ps ->
            ps.setString(1, robotId)
            ps.executeQuery().use { rs ->
                if (rs.next()) Json.decodeFromString(rs.getString("topology_json")) else null
            }
        }
    }

    private inline fun <T> executeBatchInsert(
        targetConn: Connection,
        items: List<T>,
        sql: String,
        batchSize: Int = 10000,
        crossinline bind: (java.sql.PreparedStatement, T) -> Unit
    ) {
        targetConn.autoCommit = false
        try {
            val cacheKey = "${targetConn.hashCode()}_$sql"
            val ps = statementCache.getOrPut(cacheKey) { targetConn.prepareStatement(sql) }
            items.chunked(batchSize).forEach { chunk ->
                for (item in chunk) {
                    bind(ps, item)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            targetConn.commit()
        } catch (e: Exception) {
            targetConn.rollback()
            throw e
        } finally {
            targetConn.autoCommit = true
        }
    }

    suspend fun insertConsoleMessages(messages: List<ConsoleMessage>, sessionId: String) = withDbLock {
        executeBatchInsert(conn, messages, "INSERT OR REPLACE INTO console_messages (timestamp_ms, session_id, text, severity) VALUES (?, ?, ?, ?)") { ps, msg ->
            ps.setLong(1, msg.timestampMs)
            ps.setString(2, sessionId)
            ps.setString(3, msg.text)
            ps.setString(4, msg.severity)
        }
    }

    suspend fun getConsoleMessages(sessionId: String): List<ConsoleMessage> = withDbLock {
        val list = mutableListOf<ConsoleMessage>()
        conn.prepareStatement("SELECT * FROM console_messages WHERE session_id = ? ORDER BY timestamp_ms ASC").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toConsoleMessage())
            }
        }
        list
    }

    // --- ResultSet Mappers ---

    private fun ResultSet.toSession(): Session {
        val matchNum = getLong("match_number")
        val matchNumFinal = if (wasNull()) null else matchNum.toInt()

        return Session(
            sessionId = getString("session_id"),
            teamId = getString("team_id"),
            seasonId = getString("season_id"),
            robotId = getString("robot_id"),
            createdAt = getLong("created_at"),
            durationMs = getLong("duration_ms"),
            tags = Json.decodeFromString(getString("tags") ?: "[]"),
            matchNumber = matchNumFinal,
            allianceColor = getString("alliance_color")
        )
    }

    private fun ResultSet.toSessionSummary(): SessionSummary {
        val matchNum = getLong("match_number")
        val matchNumFinal = if (wasNull()) null else matchNum.toInt()

        return SessionSummary(
            sessionId = getString("session_id"),
            teamId = getString("team_id"),
            seasonId = getString("season_id"),
            robotId = getString("robot_id"),
            createdAt = getLong("created_at"),
            durationMs = getLong("duration_ms"),
            minBatteryVoltage = getDouble("min_battery_voltage"),
            maxEkfDrift = getDouble("max_ekf_drift"),
            avgLoopTimeMs = getDouble("avg_loop_time_ms"),
            p95LoopTimeMs = getDouble("p95_loop_time_ms"),
            motorCurrentAverages = Json.decodeFromString(getString("motor_current_averages") ?: "{}"),
            visionAcceptanceRate = getDouble("vision_acceptance_rate"),
            avgCrossTrackError = getDouble("avg_cross_track_error"),
            avgBatteryResistance = getDouble("avg_battery_resistance"),
            maxMotorTemps = Json.decodeFromString(getString("max_motor_temps") ?: "{}"),
            avgVisionLatencyMs = getDouble("avg_vision_latency_ms"),
            tags = Json.decodeFromString(getString("tags") ?: "[]"),
            matchNumber = matchNumFinal,
            allianceColor = getString("alliance_color")
        )
    }

    private fun ResultSet.toTelemetryFrame(): TelemetryFrame {
        val sVal = getString("string_value")
        val sValFinal = if (wasNull()) null else sVal
        return TelemetryFrame(
            timestampMs = getLong("timestamp_ms"),
            sessionId = getString("session_id"),
            key = getString("key"),
            value = getDouble("value"),
            stringValue = sValFinal,
            timestampUs = getLong("timestamp_us"),
            sampleOrder = getLong("sample_order")
        )
    }

    private fun ResultSet.toSessionAnnotation(): SessionAnnotation {
        return SessionAnnotation(
            annotationId = getString("annotation_id"),
            sessionId = getString("session_id"),
            text = getString("text"),
            createdAt = getLong("created_at"),
            authorId = getString("author_id")
        )
    }

    private fun ResultSet.toAlertRecord(): AlertRecord {
        val rTime = getLong("resolve_timestamp_ms")
        val rTimeFinal = if (wasNull()) null else rTime

        return AlertRecord(
            alertId = getString("alert_id"),
            sessionId = getString("session_id"),
            ruleKey = getString("rule_key"),
            triggerTimestampMs = getLong("trigger_timestamp_ms"),
            resolveTimestampMs = rTimeFinal,
            durationMs = getLong("duration_ms"),
            peakValue = getDouble("peak_value"),
            triaged = getLong("triaged") != 0L
        )
    }

    private fun ResultSet.toConsoleMessage(): ConsoleMessage {
        return ConsoleMessage(
            timestampMs = getLong("timestamp_ms"),
            text = getString("text"),
            severity = getString("severity")
        )
    }

    suspend fun getTelemetryDensity(sessionId: String, buckets: Int = 100): List<Float> = withDbLock {
        val activeConn = if (sessionId == "live-telemetry") ephemeralConn else conn
        var minTime = 0L
        var maxTime = 0L
        activeConn.prepareStatement("SELECT MIN(timestamp_ms), MAX(timestamp_ms) FROM telemetry_frames WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    minTime = rs.getLong(1)
                    maxTime = rs.getLong(2)
                }
            }
        }

        if (minTime == maxTime || maxTime == 0L) {
            return@withDbLock List(buckets) { 0f }
        }
        val duration = maxTime - minTime
        val bucketSize = duration.toDouble() / buckets
        val bucketCounts = LongArray(buckets)
        activeConn.prepareStatement("""
            SELECT CAST((timestamp_ms - ?) / ? AS INTEGER) as bucket_idx, COUNT(*) as cnt
            FROM telemetry_frames
            WHERE session_id = ?
            GROUP BY bucket_idx
        """.trimIndent()).use { ps ->
            ps.setLong(1, minTime)
            ps.setDouble(2, bucketSize)
            ps.setString(3, sessionId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val idx = rs.getInt(1).coerceIn(0, buckets - 1)
                    val cnt = rs.getLong(2)
                    bucketCounts[idx] += cnt
                }
            }
        }
        val maxCount = bucketCounts.maxOrNull() ?: 1L
        if (maxCount == 0L) {
             return@withDbLock List(buckets) { 0f }
        }

        bucketCounts.map { it.toFloat() / maxCount }
    }

}
