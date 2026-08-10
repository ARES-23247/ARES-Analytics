package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import java.io.File

/**
 * Reserved adapter for direct Parquet log import.
 *
 * Direct decoding is not implemented or registered with [com.ares.analytics.service.LogParserService].
 * Calling [parseParquetLog] fails explicitly so a future caller cannot report a successful empty import.
 *
 * @param databaseService Primary DuckDB persistence service.
 *
 * @see CsvLogDecoder
 * @see WpiLogDecoder
 */
class ParquetLogDecoder(private val databaseService: DatabaseService) {

    /**
     * Parses an Apache Parquet binary log file into the telemetry pipeline.
     *
     * @param file Target `.parquet` file.
     * @param sessionId Session identifier string.
     * @param batcher Destination telemetry frame channel buffer.
     */
    suspend fun parseParquetLog(file: File, sessionId: String, batcher: FrameBatcher) {
        throw UnsupportedOperationException(
            "Direct Parquet log decoding is not implemented; use the database Parquet import/export path"
        )
    }
}
