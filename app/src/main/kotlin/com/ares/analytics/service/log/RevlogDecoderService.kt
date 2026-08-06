package com.ares.analytics.service.log

import com.ares.analytics.service.DatabaseService
import com.ares.analytics.service.FrameBatcher
import com.ares.analytics.service.LogParserService
import java.io.File
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
            withContext(Dispatchers.IO) {
                // Attempt to run the official revlog-converter via npx
                val pb = ProcessBuilder(
                    "cmd.exe", "/c",
                    "npx --yes @rev-robotics/revlog-converter ${file.absolutePath} -o ${tempWpiLog.absolutePath}"
                )
                pb.redirectErrorStream(true)
                val process = pb.start()
                val finished = process.waitFor(30, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                }
                
                if (finished && process.exitValue() == 0 && tempWpiLog.exists() && tempWpiLog.length() > 0) {
                    // Successfully converted to WPILOG! Now parse the converted WPILOG file.
                    logParserService.parseWpiLog(tempWpiLog, sessionId, batcher)
                } else {
                    // If npx failed or wasn't installed, fallback to check if revlog-converter is globally on PATH
                    val pbFallback = ProcessBuilder(
                        "cmd.exe", "/c",
                        "revlog-converter ${file.absolutePath} -o ${tempWpiLog.absolutePath}"
                    )
                    pbFallback.redirectErrorStream(true)
                    val processFallback = pbFallback.start()
                    val finishedFallback = processFallback.waitFor(30, TimeUnit.SECONDS)
                    if (!finishedFallback) {
                        processFallback.destroyForcibly()
                    }
                    
                    if (finishedFallback && processFallback.exitValue() == 0 && tempWpiLog.exists() && tempWpiLog.length() > 0) {
                        logParserService.parseWpiLog(tempWpiLog, sessionId, batcher)
                    } else {
                        System.err.println("REVLOG conversion failed. Make sure Node.js and @rev-robotics/revlog-converter are available.")
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to execute REVLOG conversion process: ${e.message}")
        } finally {
            if (tempWpiLog.exists()) {
                tempWpiLog.delete()
            }
        }
    }
}
