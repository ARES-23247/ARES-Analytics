package com.ares.analytics.service

import com.ares.analytics.database.AresDatabase
import com.ares.analytics.shared.*
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

class DatabaseService(dbPath: String = System.getProperty("user.home") + "/.ares-analytics/telemetry.db") {
    
    private val driver: JdbcSqliteDriver
    private val database: AresDatabase
    private val queries get() = database.aresDatabaseQueries

    init {
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()
        
        val properties = Properties().apply {
            setProperty("journal_mode", "WAL")
        }
        
        driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", properties)
        
        // Run schema creation statements one-by-one to self-heal legacy databases
        val createStatements = listOf(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                session_id TEXT PRIMARY KEY NOT NULL,
                team_id TEXT NOT NULL,
                season_id TEXT NOT NULL,
                robot_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                tags TEXT NOT NULL DEFAULT '[]',
                match_number INTEGER,
                alliance_color TEXT,
                log_file_path TEXT
            );
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS session_summaries (
                session_id TEXT PRIMARY KEY NOT NULL,
                team_id TEXT NOT NULL,
                season_id TEXT NOT NULL,
                robot_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                min_battery_voltage REAL NOT NULL DEFAULT 0.0,
                max_ekf_drift REAL NOT NULL DEFAULT 0.0,
                avg_loop_time_ms REAL NOT NULL DEFAULT 0.0,
                p95_loop_time_ms REAL NOT NULL DEFAULT 0.0,
                motor_current_averages TEXT NOT NULL DEFAULT '{}',
                vision_acceptance_rate REAL NOT NULL DEFAULT 0.0,
                tags TEXT NOT NULL DEFAULT '[]',
                match_number INTEGER,
                alliance_color TEXT
            );
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS telemetry_frames (
                timestamp_ms INTEGER NOT NULL,
                session_id TEXT NOT NULL,
                key TEXT NOT NULL,
                value REAL NOT NULL,
                PRIMARY KEY (session_id, key, timestamp_ms)
            ) WITHOUT ROWID;
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS session_annotations (
                annotation_id TEXT PRIMARY KEY NOT NULL,
                session_id TEXT NOT NULL,
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                author_id TEXT
            );
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_annotations_session ON session_annotations(session_id);",
            """
            CREATE TABLE IF NOT EXISTS alerts (
                alert_id TEXT PRIMARY KEY NOT NULL,
                session_id TEXT NOT NULL,
                rule_key TEXT NOT NULL,
                trigger_timestamp_ms INTEGER NOT NULL,
                resolve_timestamp_ms INTEGER,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                peak_value REAL NOT NULL DEFAULT 0.0,
                triaged INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_alerts_session ON alerts(session_id);",
            """
            CREATE TABLE IF NOT EXISTS console_messages (
                timestamp_ms INTEGER NOT NULL,
                session_id TEXT NOT NULL,
                text TEXT NOT NULL,
                severity TEXT NOT NULL,
                PRIMARY KEY (session_id, timestamp_ms, text)
            ) WITHOUT ROWID;
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS cached_topologies (
                robot_id TEXT PRIMARY KEY NOT NULL,
                topology_json TEXT NOT NULL
            );
            """.trimIndent()
        )

        for (statement in createStatements) {
            try {
                driver.execute(null, statement, 0)
            } catch (e: Exception) {
                println("[DatabaseService] Failed to execute statement: $statement. Error: ${e.message}")
            }
        }

        // Self-healing migrations for existing databases
        val migrations = listOf(
            "ALTER TABLE sessions ADD COLUMN log_file_path TEXT;"
        )
        for (migration in migrations) {
            try {
                driver.execute(null, migration, 0)
            } catch (_: Exception) {
                // Column already exists — safe to ignore
            }
        }
        
        database = AresDatabase(driver)
    }

    fun close() {
        try {
            driver.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CRUD: Sessions
    // ────────────────────────────────────────────────────────────────────────────

    suspend fun insertSession(session: Session) = withContext(Dispatchers.IO) {
        queries.insertSession(
            session_id = session.sessionId,
            team_id = session.teamId,
            season_id = session.seasonId,
            robot_id = session.robotId,
            created_at = session.createdAt,
            duration_ms = session.durationMs,
            tags = Json.encodeToString(session.tags),
            match_number = session.matchNumber?.toLong(),
            alliance_color = session.allianceColor
        )
    }

    suspend fun getSessions(): List<Session> = withContext(Dispatchers.IO) {
        queries.getSessions().executeAsList().map { it.toSession() }
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        queries.transaction {
            queries.deleteSession(sessionId)
            queries.deleteSessionSummary(sessionId)
            queries.deleteTelemetryFrames(sessionId)
            queries.deleteAnnotations(sessionId)
            queries.deleteAlerts(sessionId)
            queries.deleteConsoleMessages(sessionId)
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CRUD: Session Summaries
    // ────────────────────────────────────────────────────────────────────────────

    suspend fun insertSessionSummary(summary: SessionSummary) = withContext(Dispatchers.IO) {
        queries.insertSessionSummary(
            session_id = summary.sessionId,
            team_id = summary.teamId,
            season_id = summary.seasonId,
            robot_id = summary.robotId,
            created_at = summary.createdAt,
            duration_ms = summary.durationMs,
            min_battery_voltage = summary.minBatteryVoltage,
            max_ekf_drift = summary.maxEkfDrift,
            avg_loop_time_ms = summary.avgLoopTimeMs,
            p95_loop_time_ms = summary.p95LoopTimeMs,
            motor_current_averages = Json.encodeToString(summary.motorCurrentAverages),
            vision_acceptance_rate = summary.visionAcceptanceRate,
            tags = Json.encodeToString(summary.tags),
            match_number = summary.matchNumber?.toLong(),
            alliance_color = summary.allianceColor
        )
    }

    suspend fun getSessionSummary(sessionId: String): SessionSummary? = withContext(Dispatchers.IO) {
        queries.getSessionSummary(sessionId).executeAsOneOrNull()?.toSessionSummary()
    }

    suspend fun getAllSessionSummaries(): List<SessionSummary> = withContext(Dispatchers.IO) {
        queries.getAllSessionSummaries().executeAsList().map { it.toSessionSummary() }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CRUD: Telemetry Frames
    // ────────────────────────────────────────────────────────────────────────────

    suspend fun insertTelemetryFrames(frames: List<TelemetryFrame>) = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) return@withContext
        queries.transaction {
            for (frame in frames) {
                queries.insertTelemetryFrame(
                    timestamp_ms = frame.timestampMs,
                    session_id = frame.sessionId,
                    key = frame.key,
                    value_ = frame.value
                )
            }
        }
    }

    suspend fun getTelemetryRange(sessionId: String, startMs: Long, endMs: Long): List<TelemetryFrame> = withContext(Dispatchers.IO) {
        queries.getTelemetryRange(sessionId, startMs, endMs).executeAsList().map { it.toTelemetryFrame() }
    }

    suspend fun getTelemetryForKey(sessionId: String, key: String): List<TelemetryFrame> = withContext(Dispatchers.IO) {
        queries.getTelemetryForKey(sessionId, key).executeAsList().map { it.toTelemetryFrame() }
    }

    suspend fun deleteTelemetryFrames(sessionId: String) = withContext(Dispatchers.IO) {
        queries.deleteTelemetryFrames(sessionId)
    }

    suspend fun pruneTelemetryFrames(sessionId: String, cutoffMs: Long) = withContext(Dispatchers.IO) {
        queries.pruneTelemetryFrames(sessionId, cutoffMs)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CRUD: Session Annotations
    // ────────────────────────────────────────────────────────────────────────────

    suspend fun insertAnnotation(annotation: SessionAnnotation) = withContext(Dispatchers.IO) {
        queries.insertAnnotation(
            annotation_id = annotation.annotationId,
            session_id = annotation.sessionId,
            text = annotation.text,
            created_at = annotation.createdAt,
            author_id = annotation.authorId
        )
    }

    suspend fun getAnnotations(sessionId: String): List<SessionAnnotation> = withContext(Dispatchers.IO) {
        queries.getAnnotations(sessionId).executeAsList().map { it.toSessionAnnotation() }
    }

    suspend fun updateSessionTags(sessionId: String, tags: List<String>) = withContext(Dispatchers.IO) {
        val tagsJson = Json.encodeToString(tags)
        queries.transaction {
            queries.updateSessionTags(tagsJson, sessionId)
            queries.updateSessionSummaryTags(tagsJson, sessionId)
        }
    }

    suspend fun updateSessionMatchDetails(sessionId: String, matchNumber: Int?, allianceColor: String?) = withContext(Dispatchers.IO) {
        val matchLong = matchNumber?.toLong()
        queries.transaction {
            queries.updateSessionMatchDetails(matchLong, allianceColor, sessionId)
            queries.updateSessionSummaryMatchDetails(matchLong, allianceColor, sessionId)
        }
    }

    suspend fun associateSessionWithMatch(sessionId: String, matchNumber: Int, allianceColor: String, opponentTeams: List<String>) = withContext(Dispatchers.IO) {
        queries.transaction {
            var currentTags = emptyList<String>()
            val session = queries.getSessions().executeAsList().firstOrNull { it.session_id == sessionId }
            if (session != null) {
                currentTags = Json.decodeFromString<List<String>>(session.tags)
            }
            
            val newTags = currentTags.filter { 
                !it.startsWith("match-") && !it.startsWith("alliance-") && !it.startsWith("vs-") 
            }.toMutableList()
            
            newTags.add("match-$matchNumber")
            newTags.add("alliance-$allianceColor")
            opponentTeams.forEach { opp ->
                newTags.add("vs-$opp")
            }
            val tagsJson = Json.encodeToString(newTags)
            val matchLong = matchNumber.toLong()
            
            queries.updateSessionTags(tagsJson, sessionId)
            queries.updateSessionMatchDetails(matchLong, allianceColor, sessionId)
            queries.updateSessionSummaryTags(tagsJson, sessionId)
            queries.updateSessionSummaryMatchDetails(matchLong, allianceColor, sessionId)
        }
    }

    suspend fun updateSessionLogFilePath(sessionId: String, logFilePath: String) = withContext(Dispatchers.IO) {
        queries.updateSessionLogFilePath(logFilePath, sessionId)
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CRUD: Alerts
    // ────────────────────────────────────────────────────────────────────────────

    suspend fun insertAlert(alert: AlertRecord) = withContext(Dispatchers.IO) {
        queries.insertAlert(
            alert_id = alert.alertId,
            session_id = alert.sessionId,
            rule_key = alert.ruleKey,
            trigger_timestamp_ms = alert.triggerTimestampMs,
            resolve_timestamp_ms = alert.resolveTimestampMs,
            duration_ms = alert.durationMs,
            peak_value = alert.peakValue,
            triaged = if (alert.triaged) 1 else 0
        )
    }

    suspend fun getAlerts(sessionId: String): List<AlertRecord> = withContext(Dispatchers.IO) {
        queries.getAlerts(sessionId).executeAsList().map { it.toAlertRecord() }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // CRUD: Cached Topologies
    // ────────────────────────────────────────────────────────────────────────────

    suspend fun insertTopology(topology: HardwareTopology) = withContext(Dispatchers.IO) {
        queries.insertTopology(
            robot_id = topology.robotId,
            topology_json = Json.encodeToString(topology)
        )
    }

    suspend fun getTopology(robotId: String): HardwareTopology? = withContext(Dispatchers.IO) {
        val json = queries.getTopology(robotId).executeAsOneOrNull()
        if (json != null) {
            Json.decodeFromString<HardwareTopology>(json)
        } else null
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Helper Mappings
    // ────────────────────────────────────────────────────────────────────────────

    private fun com.ares.analytics.database.Sessions.toSession(): Session {
        return Session(
            sessionId = session_id,
            teamId = team_id,
            seasonId = season_id,
            robotId = robot_id,
            createdAt = created_at,
            durationMs = duration_ms,
            tags = Json.decodeFromString<List<String>>(tags),
            matchNumber = match_number?.toInt(),
            allianceColor = alliance_color
        )
    }

    private fun com.ares.analytics.database.Session_summaries.toSessionSummary(): SessionSummary {
        return SessionSummary(
            sessionId = session_id,
            teamId = team_id,
            seasonId = season_id,
            robotId = robot_id,
            createdAt = created_at,
            durationMs = duration_ms,
            minBatteryVoltage = min_battery_voltage,
            maxEkfDrift = max_ekf_drift,
            avgLoopTimeMs = avg_loop_time_ms,
            p95LoopTimeMs = p95_loop_time_ms,
            motorCurrentAverages = Json.decodeFromString<Map<String, Double>>(motor_current_averages),
            visionAcceptanceRate = vision_acceptance_rate,
            tags = Json.decodeFromString<List<String>>(tags),
            matchNumber = match_number?.toInt(),
            allianceColor = alliance_color
        )
    }

    private fun com.ares.analytics.database.Telemetry_frames.toTelemetryFrame(): TelemetryFrame {
        return TelemetryFrame(
            timestampMs = timestamp_ms,
            sessionId = session_id,
            key = key,
            value = value_
        )
    }

    private fun com.ares.analytics.database.Session_annotations.toSessionAnnotation(): SessionAnnotation {
        return SessionAnnotation(
            annotationId = annotation_id,
            sessionId = session_id,
            text = text,
            createdAt = created_at,
            authorId = author_id
        )
    }

    private fun com.ares.analytics.database.Alerts.toAlertRecord(): AlertRecord {
        return AlertRecord(
            alertId = alert_id,
            sessionId = session_id,
            ruleKey = rule_key,
            triggerTimestampMs = trigger_timestamp_ms,
            resolveTimestampMs = resolve_timestamp_ms,
            durationMs = duration_ms,
            peakValue = peak_value,
            triaged = triaged == 1L
        )
    }

    suspend fun insertConsoleMessages(messages: List<ConsoleMessage>, sessionId: String) = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext
        queries.transaction {
            for (msg in messages) {
                queries.insertConsoleMessage(
                    timestamp_ms = msg.timestampMs,
                    session_id = sessionId,
                    text = msg.text,
                    severity = msg.severity
                )
            }
        }
    }

    suspend fun getConsoleMessages(sessionId: String): List<ConsoleMessage> = withContext(Dispatchers.IO) {
        queries.getConsoleMessages(sessionId).executeAsList().map {
            ConsoleMessage(
                timestampMs = it.timestamp_ms,
                text = it.text,
                severity = it.severity
            )
        }
    }
}
