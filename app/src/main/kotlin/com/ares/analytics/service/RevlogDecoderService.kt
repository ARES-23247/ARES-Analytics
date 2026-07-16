package com.ares.analytics.service

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class RevlogDecoderService(
    private val databaseService: DatabaseService
) {

    suspend fun parseRevlog(
        file: File,
        sessionId: String,
        batcher: FrameBatcher,
        logParserService: LogParserService
    ) {
        val tempWpiLog = File(System.getProperty("java.io.tmpdir"), "revlog_" + UUID.randomUUID().toString() + ".wpilog")
        try {
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
        } catch (e: Exception) {
            System.err.println("Failed to execute REVLOG conversion process: ${e.message}")
        } finally {
            if (tempWpiLog.exists()) {
                tempWpiLog.delete()
            }
        }
    }
}
