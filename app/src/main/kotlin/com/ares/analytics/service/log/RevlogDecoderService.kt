package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import com.ares.analytics.service.LogParserService
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for decoding REV Robotics proprietary binary `.revlog` files (emitted by REV Hardware Client / SPARK MAX / SPARK Flex).
 *
 * Interoperates with REV's `@rev-robotics/revlog-converter` Node.js package to convert binary revlogs into standard WPILib `.wpilog` files,
 * subsequently passing the converted file to [LogParserService.parseWpiLog].
 *
 * ### Workflow & Execution Strategy:
 * 1. Invokes `npx --yes @rev-robotics/revlog-converter input.revlog -o temp.wpilog` in subprocess.
 * 2. Fallbacks to global CLI invocation `revlog-converter` if `npx` execution fails or times out (30 sec timeout).
 * 3. Streams converted WPILOG frames through [LogParserService] into [FrameBatcher].
 * 4. Deletes temporary `.wpilog` files upon process completion in `finally` block.
 *
 * ### Thread Safety & Performance Guarantees:
 * Executes subprocess spawning asynchronously on `Dispatchers.IO`. Subprocesses enforce strict 30-second timeout destruction.
 *
 * @param databaseService Primary DuckDB persistence service.
 * @param logParserService Central log parser service handling delegated WPILOG decoding.
 *
 * @see BaseLogDecoder
 * @see WpiLogDecoder
 * @see LogParserService
 */
class RevlogDecoderService(
    private val databaseService: DatabaseService,
    private val logParserService: LogParserService
) : BaseLogDecoder() {

    /**
     * Converts a binary `.revlog` file to temporary WPILOG format and streams decoded telemetry frames.
     *
     * @param file Source `.revlog` binary file.
     * @param sessionId Session identifier string.
     * @param batcher Destination telemetry frame batch buffer.
     */
    override suspend fun decode(
        file: File,
        sessionId: String,
        batcher: FrameBatcher
    ) {
        val tempWpiLog = File(System.getProperty("java.io.tmpdir"), "revlog_" + UUID.randomUUID().toString() + ".wpilog")
        try {
            val converted = withContext(Dispatchers.IO) {
                // Arguments are passed directly so spaces and shell metacharacters in paths remain data.
                runConverter(
                    listOf("npx.cmd", "--yes", "@rev-robotics/revlog-converter", file.absolutePath, "-o", tempWpiLog.absolutePath)
                ) || runConverter(
                    listOf("revlog-converter.cmd", file.absolutePath, "-o", tempWpiLog.absolutePath)
                ) || runConverter(
                    listOf("revlog-converter", file.absolutePath, "-o", tempWpiLog.absolutePath)
                )
            }
            if (!converted || !tempWpiLog.isFile || tempWpiLog.length() == 0L) {
                throw IOException("REVLOG conversion failed; install the REV converter CLI or Node.js")
            }
            logParserService.parseWpiLog(tempWpiLog, sessionId, batcher)
        } finally {
            if (tempWpiLog.exists()) {
                tempWpiLog.delete()
            }
        }
    }

    private fun runConverter(command: List<String>): Boolean {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            val finished = process.waitFor(CONVERTER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: IOException) {
            false
        }
    }

    private companion object {
        const val CONVERTER_TIMEOUT_SECONDS = 30L
    }
}
