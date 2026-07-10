package com.ares.analytics.service

import com.ares.analytics.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.duckdb.DuckDBAppender
import org.duckdb.DuckDBConnection
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

class DatabaseService(dbPath: String = System.getProperty("user.home") + "/.ares-analytics/telemetry.duckdb") {
    
    private val conn: Connection
    private val ephemeralConn: Connection
    private val dbMutex = Mutex()
    
    init {
        Class.forName("org.duckdb.DuckDBDriver")
        
        val oldDbPath = System.getProperty("user.home") + "/.ares-analytics/telemetry.db"
        val isFirstRun = !File(dbPath).exists()
        
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()
        
        if (dbFile.exists() && dbFile.length() == 0L) {
            dbFile.delete()
        }
        
        conn = DriverManager.getConnection("jdbc:duckdb:${dbFile.absolutePath}")
        
        // Ensure parquet extension is loaded for export
        conn.createStatement().use { st ->
            st.execute("INSTALL parquet;")
            st.execute("LOAD parquet;")
        }
        
        ephemeralConn = DriverManager.getConnection("jdbc:duckdb:")
        
        createSchemaSync(conn)
        createSchemaSync(ephemeralConn)
        
        if (isFirstRun && File(oldDbPath).exists()) {
            try {
                conn.createStatement().use { st ->
                    st.execute("ATTACH '$oldDbPath' AS sqlite (TYPE SQLITE)")
                    st.execute("INSERT OR IGNORE INTO sessions SELECT session_id, team_id, season_id, robot_id, created_at, duration_ms, tags, match_number, alliance_color FROM sqlite.sessions")
                    st.execute("INSERT OR IGNORE INTO session_summaries SELECT session_id, team_id, season_id, robot_id, created_at, duration_ms, min_battery_voltage, max_ekf_drift, avg_loop_time_ms, p95_loop_time_ms, motor_current_averages, vision_acceptance_rate, avg_cross_track_error, avg_battery_resistance, max_motor_temps, avg_vision_latency_ms, tags, match_number, alliance_color FROM sqlite.session_summaries")
                    st.execute("INSERT OR IGNORE INTO telemetry_frames SELECT timestamp_ms, session_id, key, value FROM sqlite.telemetry_frames")
                    st.execute("INSERT OR IGNORE INTO session_annotations SELECT annotation_id, session_id, text, created_at, author_id FROM sqlite.session_annotations")
                    st.execute("INSERT OR IGNORE INTO alerts SELECT alert_id, session_id, rule_key, trigger_timestamp_ms, resolve_timestamp_ms, duration_ms, peak_value, triaged FROM sqlite.alerts")
                    st.execute("INSERT OR IGNORE INTO cached_topologies SELECT robot_id, topology_json FROM sqlite.cached_topologies")
                    st.execute("INSERT OR IGNORE INTO console_messages SELECT timestamp_ms, session_id, text, severity FROM sqlite.console_messages")
                    st.execute("DETACH sqlite")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun <T> withDbLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        dbMutex.withLock { block() }
    }
    
    private fun createSchemaSync(targetConn: Connection) {
        targetConn.createStatement().use { st ->
            st.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    session_id VARCHAR PRIMARY KEY,
                    team_id VARCHAR NOT NULL,
                    season_id VARCHAR NOT NULL,
                    robot_id VARCHAR NOT NULL,
                    created_at BIGINT NOT NULL,
                    duration_ms BIGINT NOT NULL DEFAULT 0,
                    tags VARCHAR NOT NULL DEFAULT '[]',
                    match_number BIGINT,
                    alliance_color VARCHAR
                );
                
                CREATE TABLE IF NOT EXISTS session_summaries (
                    session_id VARCHAR PRIMARY KEY,
                    team_id VARCHAR NOT NULL,
                    season_id VARCHAR NOT NULL,
                    robot_id VARCHAR NOT NULL,
                    created_at BIGINT NOT NULL,
                    duration_ms BIGINT NOT NULL DEFAULT 0,
                    min_battery_voltage DOUBLE NOT NULL DEFAULT 0.0,
                    max_ekf_drift DOUBLE NOT NULL DEFAULT 0.0,
                    avg_loop_time_ms DOUBLE NOT NULL DEFAULT 0.0,
                    p95_loop_time_ms DOUBLE NOT NULL DEFAULT 0.0,
                    motor_current_averages VARCHAR NOT NULL DEFAULT '{}',
                    vision_acceptance_rate DOUBLE NOT NULL DEFAULT 0.0,
                    avg_cross_track_error DOUBLE NOT NULL DEFAULT 0.0,
                    avg_battery_resistance DOUBLE NOT NULL DEFAULT 0.0,
                    max_motor_temps VARCHAR NOT NULL DEFAULT '{}',
                    avg_vision_latency_ms DOUBLE NOT NULL DEFAULT 0.0,
                    tags VARCHAR NOT NULL DEFAULT '[]',
                    match_number BIGINT,
                    alliance_color VARCHAR
                );
                
                CREATE TABLE IF NOT EXISTS telemetry_frames (
                    timestamp_ms BIGINT NOT NULL,
                    session_id VARCHAR NOT NULL,
                    key VARCHAR NOT NULL,
                    value DOUBLE NOT NULL,
                    string_value VARCHAR,
                    PRIMARY KEY (session_id, key, timestamp_ms)
                );
                
                CREATE TABLE IF NOT EXISTS session_annotations (
                    annotation_id VARCHAR PRIMARY KEY,
                    session_id VARCHAR NOT NULL,
                    text VARCHAR NOT NULL,
                    created_at BIGINT NOT NULL,
                    author_id VARCHAR
                );
                
                CREATE TABLE IF NOT EXISTS alerts (
                    alert_id VARCHAR PRIMARY KEY,
                    session_id VARCHAR NOT NULL,
                    rule_key VARCHAR NOT NULL,
                    trigger_timestamp_ms BIGINT NOT NULL,
                    resolve_timestamp_ms BIGINT,
                    duration_ms BIGINT NOT NULL DEFAULT 0,
                    peak_value DOUBLE NOT NULL DEFAULT 0.0,
                    triaged BIGINT NOT NULL DEFAULT 0
                );
                
                CREATE TABLE IF NOT EXISTS console_messages (
                    timestamp_ms BIGINT NOT NULL,
                    session_id VARCHAR NOT NULL,
                    text VARCHAR NOT NULL,
                    severity VARCHAR NOT NULL,
                    PRIMARY KEY (session_id, timestamp_ms, text)
                );
                
                CREATE TABLE IF NOT EXISTS cached_topologies (
                    robot_id VARCHAR PRIMARY KEY,
                    topology_json VARCHAR NOT NULL
                );
                
                CREATE TABLE IF NOT EXISTS robot_actions (
                    timestamp_ms BIGINT NOT NULL,
                    session_id VARCHAR NOT NULL,
                    run_id VARCHAR NOT NULL,
                    robot_id VARCHAR NOT NULL,
                    match_number INTEGER NOT NULL DEFAULT 0,
                    alliance VARCHAR NOT NULL DEFAULT 'UNKNOWN',
                    action_type VARCHAR NOT NULL,
                    payload_json VARCHAR NOT NULL
                );
            """.trimIndent())
            
            try {
                st.execute("ALTER TABLE telemetry_frames ADD COLUMN string_value VARCHAR;")
            } catch (e: Exception) {
                // Ignore if it already exists
            }
        }
    }
    
    suspend fun executeRaw(sql: String) = withDbLock {
        conn.createStatement().use { it.execute(sql) }
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
            if (session.matchNumber != null) ps.setLong(8, session.matchNumber!!.toLong()) else ps.setNull(8, java.sql.Types.BIGINT)
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
        conn.prepareStatement("DELETE FROM sessions WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM session_summaries WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM telemetry_frames WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM session_annotations WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM alerts WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM console_messages WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeUpdate()
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
            if (summary.matchNumber != null) ps.setLong(18, summary.matchNumber!!.toLong()) else ps.setNull(18, java.sql.Types.BIGINT)
            ps.setString(19, summary.allianceColor)
            ps.executeUpdate()
        }
    }

    suspend fun getSessionSummary(sessionId: String): SessionSummary? = withDbLock {
        conn.prepareStatement("SELECT * FROM session_summaries WHERE session_id = ?").use { ps ->
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
        val targetConn = if (frames.first().sessionId == "live-telemetry") ephemeralConn else conn
        
        if (targetConn === conn) {
            // Use DuckDB Appender for persistent storage — bypasses SQL parser entirely
            insertTelemetryFramesAppender(frames)
        } else {
            // Ephemeral connection (live telemetry) uses JDBC batch for INSERT OR REPLACE
            insertTelemetryFramesJdbc(targetConn, frames)
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
                appender.append(frame.key)
                appender.append(frame.value)
                // DuckDB Appender doesn't support null — use empty string as sentinel
                appender.append(frame.stringValue ?: "")
                appender.endRow()
            }
            appender.flush()
        } finally {
            appender.close()
        }
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
        targetConn.autoCommit = false
        try {
            targetConn.prepareStatement("INSERT OR REPLACE INTO telemetry_frames (timestamp_ms, session_id, key, value, string_value) VALUES (?, ?, ?, ?, ?)").use { ps ->
                frames.chunked(10000).forEach { chunk ->
                    for (frame in chunk) {
                        ps.setLong(1, frame.timestampMs)
                        ps.setString(2, frame.sessionId)
                        ps.setString(3, frame.key)
                        ps.setDouble(4, frame.value)
                        if (frame.stringValue != null) {
                            ps.setString(5, frame.stringValue)
                        } else {
                            ps.setNull(5, java.sql.Types.VARCHAR)
                        }
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
            targetConn.commit()
        } catch (e: Exception) {
            targetConn.rollback()
            throw e
        } finally {
            targetConn.autoCommit = true
        }
    }

    /**
     * Returns the (min, max) timestamp range for a given session's telemetry frames,
     * or null if no frames exist. Used after DuckDB native CSV import to compute
     * session duration without holding frames in application memory.
     */
    suspend fun getSessionTimestampRange(sessionId: String): Pair<Long, Long>? = withDbLock {
        conn.prepareStatement("SELECT MIN(timestamp_ms), MAX(timestamp_ms) FROM telemetry_frames WHERE session_id = ?").use { ps ->
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

    suspend fun getTelemetryRange(sessionId: String, startMs: Long, endMs: Long): List<TelemetryFrame> = withDbLock {
        val targetConn = if (sessionId == "live-telemetry") ephemeralConn else conn
        val list = mutableListOf<TelemetryFrame>()
        targetConn.prepareStatement("SELECT * FROM telemetry_frames WHERE session_id = ? AND timestamp_ms BETWEEN ? AND ? ORDER BY timestamp_ms ASC").use { ps ->
            ps.setString(1, sessionId)
            ps.setLong(2, startMs)
            ps.setLong(3, endMs)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toTelemetryFrame())
            }
        }
        list
    }

    suspend fun getTelemetryRangeBatched(sessionId: String, startMs: Long, endMs: Long, limit: Long, offset: Long): List<TelemetryFrame> = withDbLock {
        val targetConn = if (sessionId == "live-telemetry") ephemeralConn else conn
        val list = mutableListOf<TelemetryFrame>()
        targetConn.prepareStatement("SELECT * FROM telemetry_frames WHERE session_id = ? AND timestamp_ms BETWEEN ? AND ? ORDER BY timestamp_ms ASC LIMIT ? OFFSET ?").use { ps ->
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

    suspend fun countTelemetryFrames(sessionId: String): Long = withDbLock {
        conn.prepareStatement("SELECT COUNT(*) FROM telemetry_frames WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    suspend fun getTelemetryForKey(sessionId: String, key: String): List<TelemetryFrame> = withDbLock {
        val list = mutableListOf<TelemetryFrame>()
        conn.prepareStatement("SELECT * FROM telemetry_frames WHERE session_id = ? AND key = ? ORDER BY timestamp_ms ASC").use { ps ->
            ps.setString(1, sessionId)
            ps.setString(2, key)
            ps.executeQuery().use { rs ->
                while (rs.next()) list.add(rs.toTelemetryFrame())
            }
        }
        list
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

    suspend fun deleteTelemetryFrames(sessionId: String) = withDbLock {
        conn.prepareStatement("DELETE FROM telemetry_frames WHERE session_id = ?").use { ps ->
            ps.setString(1, sessionId)
            ps.executeUpdate()
        }
    }

    suspend fun pruneTelemetryFrames(sessionId: String, cutoffMs: Long) = withDbLock {
        conn.prepareStatement("DELETE FROM telemetry_frames WHERE session_id = ? AND timestamp_ms < ?").use { ps ->
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

    suspend fun updateSessionLogFilePath(sessionId: String, logFilePath: String) = withDbLock {
        // Log file path no longer supported on DuckDB Session schema
    }

    suspend fun insertAlert(alert: AlertRecord) = withDbLock {
        conn.prepareStatement("INSERT OR REPLACE INTO alerts (alert_id, session_id, rule_key, trigger_timestamp_ms, resolve_timestamp_ms, duration_ms, peak_value, triaged) VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use { ps ->
            ps.setString(1, alert.alertId)
            ps.setString(2, alert.sessionId)
            ps.setString(3, alert.ruleKey)
            ps.setLong(4, alert.triggerTimestampMs)
            if (alert.resolveTimestampMs != null) ps.setLong(5, alert.resolveTimestampMs!!) else ps.setNull(5, java.sql.Types.BIGINT)
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

    suspend fun insertConsoleMessages(messages: List<ConsoleMessage>, sessionId: String) = withDbLock {
        conn.autoCommit = false
        try {
            conn.prepareStatement("INSERT OR REPLACE INTO console_messages (timestamp_ms, session_id, text, severity) VALUES (?, ?, ?, ?)").use { ps ->
                for (msg in messages) {
                    ps.setLong(1, msg.timestampMs)
                    ps.setString(2, sessionId)
                    ps.setString(3, msg.text)
                    ps.setString(4, msg.severity)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
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
            stringValue = sValFinal
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
        activeConn.createStatement().use { st ->
            st.executeQuery("SELECT MIN(timestamp_ms), MAX(timestamp_ms) FROM telemetry_frames WHERE session_id = '$sessionId'").use { rs ->
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
        activeConn.createStatement().use { st ->
            val query = """
                SELECT CAST((timestamp_ms - $minTime) / $bucketSize AS INTEGER) as bucket_idx, COUNT(*) as cnt 
                FROM telemetry_frames 
                WHERE session_id = '$sessionId' 
                GROUP BY bucket_idx
            """.trimIndent()
            st.executeQuery(query).use { rs ->
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

    fun close() {
        if (!conn.isClosed) {
            conn.close()
        }
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
