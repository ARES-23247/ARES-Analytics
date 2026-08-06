package com.ares.analytics.service

/**
 * Tabular result set model encapsulated from arbitrary SQL queries executed against the DuckDB telemetry store.
 *
 * Provides a decoupled, structured representation of query output for custom database analytics, trajectory
 * visualization, and UI table rendering without binding UI screens directly to JDBC ResultSets.
 *
 * ### Data Representation:
 * - [columns]: Ordered list of column header string labels returned by SQL SELECT statements.
 * - [rows]: List of rows, where each row is a list of stringified cell values aligned index-by-index with [columns].
 *
 * ### Thread Safety & Performance Guarantees:
 * Immutable data structure. Safe for concurrent access across UI state flows and background IO dispatchers. Zero heap allocations
 * post-construction during table read rendering.
 *
 * @property columns Ordered column names matching the database query projection.
 * @property rows List of tabular data rows, formatted as stringified column cell values.
 *
 * @see com.ares.analytics.service.DatabaseService
 * @see com.ares.analytics.service.db.MatchLogRepository
 */
data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>
)

