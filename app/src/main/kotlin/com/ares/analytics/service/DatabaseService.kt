package com.ares.analytics.service

import com.ares.analytics.shared.*
import com.ares.analytics.service.db.*
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class DatabaseService(val dbPath: String = System.getProperty("user.home") + "/.ares-analytics/telemetry.duckdb") {
    
    private val conn: Connection
    private val ephemeralConn: Connection
    private val dbMutex = Mutex()
    
    private val schemaManager: SchemaMigrationManager
    private val matchLogRepo: MatchLogRepository
    private val backupExporter: DatabaseBackupExporter
    
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
        
        schemaManager = SchemaMigrationManager(conn, ephemeralConn)
        matchLogRepo = MatchLogRepository(conn, ephemeralConn, dbMutex)
        backupExporter = DatabaseBackupExporter(conn, dbMutex)
        
        schemaManager.runMigrations(isFirstRun, oldDbPath)
    }

    suspend fun executeRaw(sql: String) = matchLogRepo.executeRaw(sql)
    suspend fun executeQueryRaw(sql: String): QueryResult = matchLogRepo.executeQueryRaw(sql)
    suspend fun executeQueryWithParams(sql: String, params: List<Any>): QueryResult = matchLogRepo.executeQueryWithParams(sql, params)
    suspend fun insertSession(session: Session) = matchLogRepo.insertSession(session)
    suspend fun getSessions(): List<Session> = matchLogRepo.getSessions()
    suspend fun deleteSession(sessionId: String) = matchLogRepo.deleteSession(sessionId)
    suspend fun insertSessionSummary(summary: SessionSummary) = matchLogRepo.insertSessionSummary(summary)
    suspend fun getSessionSummary(sessionId: String): SessionSummary? = matchLogRepo.getSessionSummary(sessionId)
    suspend fun getAllSessionSummaries(): List<SessionSummary> = matchLogRepo.getAllSessionSummaries()
    suspend fun insertTelemetryFrames(frames: List<TelemetryFrame>) = matchLogRepo.insertTelemetryFrames(frames)
    suspend fun insertRobotActionsBulk(actions: List<com.ares.analytics.shared.RobotActionRecord>) = matchLogRepo.insertRobotActionsBulk(actions)
    suspend fun getActionsForSession(sessionId: String): List<com.ares.analytics.shared.RobotActionRecord> = matchLogRepo.getActionsForSession(sessionId)
    suspend fun getSessionTimestampRange(sessionId: String): Pair<Long, Long>? = matchLogRepo.getSessionTimestampRange(sessionId)
    suspend fun getTelemetryRange(sessionId: String, startMs: Long, endMs: Long): List<TelemetryFrame> = matchLogRepo.getTelemetryRange(sessionId, startMs, endMs)
    suspend fun getTelemetryRangeBatched(sessionId: String, startMs: Long, endMs: Long, limit: Long, offset: Long): List<TelemetryFrame> = matchLogRepo.getTelemetryRangeBatched(sessionId, startMs, endMs, limit, offset)
    suspend fun countTelemetryFrames(sessionId: String): Long = matchLogRepo.countTelemetryFrames(sessionId)
    suspend fun getTelemetryForKey(sessionId: String, key: String): List<TelemetryFrame> = matchLogRepo.getTelemetryForKey(sessionId, key)
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
    suspend fun updateSessionLogFilePath(sessionId: String, logFilePath: String) = matchLogRepo.updateSessionLogFilePath(sessionId, logFilePath)
    suspend fun insertAlert(alert: AlertRecord) = matchLogRepo.insertAlert(alert)
    suspend fun getAlerts(sessionId: String): List<AlertRecord> = matchLogRepo.getAlerts(sessionId)
    suspend fun insertTopology(topology: HardwareTopology) = matchLogRepo.insertTopology(topology)
    suspend fun getTopology(robotId: String): HardwareTopology? = matchLogRepo.getTopology(robotId)
    suspend fun insertConsoleMessages(messages: List<ConsoleMessage>, sessionId: String) = matchLogRepo.insertConsoleMessages(messages, sessionId)
    suspend fun getConsoleMessages(sessionId: String): List<ConsoleMessage> = matchLogRepo.getConsoleMessages(sessionId)
    suspend fun getTelemetryDensity(sessionId: String, buckets: Int = 100): List<Float> = matchLogRepo.getTelemetryDensity(sessionId, buckets)
    
    suspend fun importParquet(file: File) = backupExporter.importParquet(file)

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun close() {
        if (!conn.isClosed) {
            conn.close()
        }
    }
}



