package com.ares.analytics.service.db

import java.io.File
import java.sql.Connection

/**
 * Manages DDL schema initialization and database migration operations for DuckDB telemetry stores.
 *
 * Configures relational tables for main persistent database connections and temporary in-memory
 * connection instances, establishing primary keys, indexed metrics, and default values for robot performance metrics.
 *
 * ### Database Tables & Schemas:
 * - `sessions`: `(session_id VARCHAR PRIMARY KEY, team_id, season_id, robot_id, created_at BIGINT, duration_ms BIGINT, tags VARCHAR, match_number BIGINT, alliance_color VARCHAR)`
 * - `session_summaries`: Aggregate performance KPIs (`min_battery_voltage` V, `max_ekf_drift` m, `avg_loop_time_ms` ms, `p95_loop_time_ms` ms, `vision_acceptance_rate` %, `avg_cross_track_error` m)
 * - `telemetry_frames`: Time-series data points `(timestamp_ms BIGINT, session_id VARCHAR, key VARCHAR, value DOUBLE, string_value VARCHAR)`
 * - `session_annotations`, `alerts`, `console_messages`, `cached_topologies`, `robot_actions`
 *
 * ### Thread Safety & Performance Guarantees:
 * Must be executed sequentially during system startup before telemetry frame ingestion begins.
 * Uses atomic DDL statements (`CREATE TABLE IF NOT EXISTS`).
 *
 * @param conn Primary DuckDB JDBC connection bound to persistent storage on disk.
 * @param ephemeralConn Temporary in-memory DuckDB JDBC connection used for fast buffer filtering.
 *
 * @see DatabaseBackupExporter
 * @see MatchLogRepository
 */
class SchemaMigrationManager(
    private val conn: Connection,
    private val ephemeralConn: Connection
) {
    /**
     * Runs DDL schema migrations across both primary and ephemeral DuckDB connections.
     *
     * If a legacy SQLite database exists at [oldDbPath], attempts to attach it and migrate its
     * historical tables exactly once. Completion is recorded transactionally, so a failed import
     * is retried on the next launch instead of being silently abandoned after the DuckDB file exists.
     *
     * @param oldDbPath Absolute legacy database path, or null for isolated/custom databases.
     */
    fun runMigrations(oldDbPath: String?) {
        createSchemaSync(conn)
        createSchemaSync(ephemeralConn)

        if (oldDbPath != null && File(oldDbPath).isFile && !migrationCompleted(LEGACY_SQLITE_MIGRATION)) {
            val safeOldDbPath = oldDbPath.replace("'", "''")
            try {
                conn.createStatement().use { st ->
                    try {
                        st.execute("ATTACH '$safeOldDbPath' AS legacy_sqlite (TYPE SQLITE)")
                        st.execute("BEGIN TRANSACTION")
                        try {
                            st.execute("INSERT OR IGNORE INTO sessions SELECT session_id, team_id, season_id, robot_id, created_at, duration_ms, tags, match_number, alliance_color FROM legacy_sqlite.sessions")
                            st.execute("INSERT OR IGNORE INTO session_summaries SELECT session_id, team_id, season_id, robot_id, created_at, duration_ms, min_battery_voltage, max_ekf_drift, avg_loop_time_ms, p95_loop_time_ms, motor_current_averages, vision_acceptance_rate, avg_cross_track_error, avg_battery_resistance, max_motor_temps, avg_vision_latency_ms, tags, match_number, alliance_color FROM legacy_sqlite.session_summaries")
                            st.execute(
                                """
                                INSERT OR IGNORE INTO telemetry_frames
                                    (timestamp_ms, session_id, key, value, string_value, timestamp_us, sample_order)
                                SELECT timestamp_ms, session_id, REGEXP_REPLACE(key, '^/+', ''), value, NULL,
                                    timestamp_ms * 1000,
                                    ROW_NUMBER() OVER (ORDER BY session_id, timestamp_ms, key)
                                FROM legacy_sqlite.telemetry_frames
                                """.trimIndent()
                            )
                            st.execute("INSERT OR IGNORE INTO session_annotations SELECT annotation_id, session_id, text, created_at, author_id FROM legacy_sqlite.session_annotations")
                            st.execute("INSERT OR IGNORE INTO alerts SELECT alert_id, session_id, rule_key, trigger_timestamp_ms, resolve_timestamp_ms, duration_ms, peak_value, triaged FROM legacy_sqlite.alerts")
                            st.execute("INSERT OR IGNORE INTO cached_topologies SELECT robot_id, topology_json FROM legacy_sqlite.cached_topologies")
                            st.execute("INSERT OR IGNORE INTO console_messages SELECT timestamp_ms, session_id, text, severity FROM legacy_sqlite.console_messages")
                            st.execute("INSERT INTO schema_migrations VALUES ('$LEGACY_SQLITE_MIGRATION', epoch_ms(current_timestamp))")
                            st.execute("COMMIT")
                        } catch (e: Exception) {
                            st.execute("ROLLBACK")
                            throw e
                        }
                    } catch (e: Exception) {
                        throw e
                    } finally {
                        runCatching { st.execute("DETACH legacy_sqlite") }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun migrationCompleted(name: String): Boolean = conn.prepareStatement(
        "SELECT 1 FROM schema_migrations WHERE name = ?"
    ).use { statement ->
        statement.setString(1, name)
        statement.executeQuery().use { rows -> rows.next() }
    }

    /**
     * Executes DDL `CREATE TABLE IF NOT EXISTS` queries synchronously on the specified JDBC connection.
     *
     * @param targetConn The active DuckDB connection to initialize.
     */
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
                    timestamp_us BIGINT NOT NULL,
                    sample_order BIGINT NOT NULL,
                    PRIMARY KEY (session_id, key, timestamp_us, sample_order)
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

                CREATE TABLE IF NOT EXISTS schema_migrations (
                    name VARCHAR PRIMARY KEY,
                    completed_at BIGINT NOT NULL
                );
            """.trimIndent())

            migrateTelemetryPrecision(targetConn)
            st.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_session_time ON telemetry_frames(session_id, timestamp_ms)")
            st.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_session_key_time ON telemetry_frames(session_id, key, timestamp_ms)")
            st.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_session_id ON telemetry_frames(session_id)")

            try {
                st.execute("ALTER TABLE telemetry_frames ADD COLUMN string_value VARCHAR;")
            } catch (e: Exception) {
                // Ignore if it already exists
            }
        }
    }

    /**
     * Rebuilds the legacy millisecond-keyed table once so multiple samples from the same
     * topic and millisecond can coexist. Existing rows retain their millisecond value and
     * receive a deterministic source timestamp/order.
     */
    private fun migrateTelemetryPrecision(targetConn: Connection) {
        val columns = targetConn.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema = current_schema() AND table_name = 'telemetry_frames'"
            ).use { rows ->
                buildSet {
                    while (rows.next()) add(rows.getString(1).lowercase())
                }
            }
        }
        if ("timestamp_us" in columns && "sample_order" in columns) return

        targetConn.createStatement().use { statement ->
            statement.execute("BEGIN TRANSACTION")
            try {
                statement.execute(
                    """
                    CREATE TABLE telemetry_frames_precision (
                        timestamp_ms BIGINT NOT NULL,
                        session_id VARCHAR NOT NULL,
                        key VARCHAR NOT NULL,
                        value DOUBLE NOT NULL,
                        string_value VARCHAR,
                        timestamp_us BIGINT NOT NULL,
                        sample_order BIGINT NOT NULL,
                        PRIMARY KEY (session_id, key, timestamp_us, sample_order)
                    )
                    """.trimIndent()
                )
                val stringValueExpression = if ("string_value" in columns) "string_value" else "NULL"
                statement.execute(
                    """
                    INSERT INTO telemetry_frames_precision
                    SELECT
                        timestamp_ms,
                        session_id,
                        REGEXP_REPLACE(TRIM(key), '^/+', ''),
                        value,
                        $stringValueExpression,
                        timestamp_ms * 1000,
                        ROW_NUMBER() OVER (ORDER BY session_id, timestamp_ms, key) - 1
                    FROM telemetry_frames
                    """.trimIndent()
                )
                statement.execute("DROP TABLE telemetry_frames")
                statement.execute("ALTER TABLE telemetry_frames_precision RENAME TO telemetry_frames")
                statement.execute("COMMIT")
            } catch (error: Exception) {
                runCatching { statement.execute("ROLLBACK") }
                throw error
            }
        }
    }

    private companion object {
        const val LEGACY_SQLITE_MIGRATION = "legacy-sqlite-v1"
    }
}
