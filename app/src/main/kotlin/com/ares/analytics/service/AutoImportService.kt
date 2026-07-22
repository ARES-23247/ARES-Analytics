package com.ares.analytics.service

import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.concurrent.TimeUnit

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class AutoImportService(
    private val databaseService: DatabaseService,
    private val logParserService: LogParserService,
    private val hootDecoderService: HootDecoderService,
    private val processManagerService: ProcessManagerService,
    private val configProvider: () -> WorkspaceConfig?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var job: Job? = null
    private val _importNotifications = MutableSharedFlow<String>(replay = 100)
    val importNotifications: SharedFlow<String> = _importNotifications.asSharedFlow()

    private var onImportSuccessCallback: (() -> Unit)? = null

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun start(onImportSuccess: () -> Unit) {
        onImportSuccessCallback = onImportSuccess
        job?.cancel()
        job = scope.launch {
            val adbPath = findAdbPath()
            while (isActive) {
                try {
                    val config = configProvider()
                    if (config != null && !config.projectPath.isNullOrEmpty()) {
                        // 1. Local logs auto-import
                        importLocalLogs(config)

                        // 2. Robot logs auto-import based on League
                        when (config.league) {
                            League.FTC -> {
                                if (processManagerService.adbConnected.value) {
                                    importFtcRobotLogs(config, adbPath)
                                }
                            }
                            League.FRC -> {
                                val host = config.nt4Host ?: getDefaultFrcHost(config.teamId)
                                if (isHostReachable(host)) {
                                    importFrcRobotLogs(config, host)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    _importNotifications.emit("[AUTO-IMPORT] Error in scan cycle: ${e.message}")
                    e.printStackTrace()
                }
                delay(5000) // Scan every 5 seconds
            }
        }
    }

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun stop() {
        job?.cancel()
        job = null
        onImportSuccessCallback = null
    }

    private suspend fun importLocalLogs(config: WorkspaceConfig) {
        val logsDirs = listOf(
            File(config.projectPath, "logs"),
            File(config.projectPath, "ftc-app/logs")
        )

        for (dir in logsDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles { _, name ->
                val lower = name.lowercase()
                lower.endsWith(".wpilog") || lower.endsWith(".jsonl") || lower.endsWith(".csv") || lower.endsWith(".hoot")
            } ?: continue
            val importedDir = File(dir, "imported")
            importedDir.mkdirs()

            for (file in files) {
                if (file.isDirectory) continue
                
                // Skip files currently being written to
                if (isFileInUseLocally(file)) {
                    continue
                }

                try {
                    _importNotifications.emit("[AUTO-IMPORT] Found local log: ${file.name}. Importing...")
                    val baseTags = mutableListOf("auto-import")
                    if (file.name.lowercase().startsWith("sim_")) {
                        baseTags.add("simulated")
                    }
                    val sessionId = if (file.name.endsWith(".hoot", ignoreCase = true)) {
                        hootDecoderService.importHootLog(file, config.teamId, config.seasonId, config.robotId)
                    } else {
                        val session = logParserService.parseLogFile(
                            file, config.teamId, config.seasonId, config.robotId,
                            tags = baseTags
                        )
                        session.sessionId
                    }

                    // Delete the local raw log after successful import to save disk space
                    file.delete()
                    _importNotifications.emit("[AUTO-IMPORT] Successfully imported ${file.name} (Session ID: ${sessionId.take(8)}...)")
                    
                    // Trigger UI reload
                    onImportSuccessCallback?.invoke()
                } catch (e: Exception) {
                    _importNotifications.emit("[AUTO-IMPORT] Failed to import local log ${file.name}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun importFtcRobotLogs(config: WorkspaceConfig, adbPath: String) {
        val robotDirs = listOf(
            "/sdcard/FIRST/telemetry_logs/",
            "/sdcard/ctre-logs/",
            "/sdcard/FIRST/ctre-logs/"
        )
        val localDestDir = File(config.projectPath, "logs/imported")
        localDestDir.mkdirs()

        for (robotDir in robotDirs) {
            val filesOnRobot = listFilesOnFtcRobot(adbPath, robotDir)
            for (filename in filesOnRobot) {
                val lower = filename.lowercase()
                if (lower.endsWith(".wpilog") || lower.endsWith(".jsonl") || lower.endsWith(".csv") || lower.endsWith(".hoot")) {
                    val remotePath = "$robotDir$filename"
                    
                    // Check if file is still being written to by ARESDataLogger
                    if (isFileInUseOnFtcRobot(adbPath, remotePath)) {
                        continue
                    }
                    val tempLocalFile = File(System.getProperty("java.io.tmpdir"), filename)
                    
                    try {
                        _importNotifications.emit("[AUTO-IMPORT] Found FTC robot log: $filename. Pulling...")
                        if (pullFileFromFtcRobot(adbPath, remotePath, tempLocalFile)) {
                            val sessionId = if (lower.endsWith(".hoot")) {
                                hootDecoderService.importHootLog(tempLocalFile, config.teamId, config.seasonId, config.robotId)
                            } else {
                                val session = logParserService.parseLogFile(
                                    tempLocalFile, config.teamId, config.seasonId, config.robotId,
                                    tags = listOf("auto-import", "robot-log")
                                )
                                session.sessionId
                            }

                            // Delete the temporary local file since it's now in the database
                            tempLocalFile.delete()

                            // Delete from robot to save disk space
                            deleteFileFromFtcRobot(adbPath, remotePath)
                            _importNotifications.emit("[AUTO-IMPORT] Successfully imported robot log $filename (Session ID: ${sessionId.take(8)}...)")
                            
                            // Trigger UI reload
                            onImportSuccessCallback?.invoke()
                        }
                    } catch (e: Exception) {
                        _importNotifications.emit("[AUTO-IMPORT] Failed to import robot log $filename: ${e.message}")
                        tempLocalFile.delete()
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private suspend fun importFrcRobotLogs(config: WorkspaceConfig, host: String) {
        val robotDirs = listOf(
            "/home/lvuser/logs/",
            "/media/sda1/logs/"
        )
        val localDestDir = File(config.projectPath, "logs/imported")
        localDestDir.mkdirs()

        for (robotDir in robotDirs) {
            val filesOnRobot = listFilesOnFrcRobot(host, robotDir)
            for (filename in filesOnRobot) {
                val lower = filename.lowercase()
                if (lower.endsWith(".wpilog") || lower.endsWith(".jsonl") || lower.endsWith(".csv") || lower.endsWith(".hoot")) {
                    val remotePath = "$robotDir$filename"

                    // Check if file is still being written to by DataLogManager
                    if (isFileInUseOnFrcRobot(host, remotePath)) {
                        continue
                    }
                    val tempLocalFile = File(System.getProperty("java.io.tmpdir"), filename)
                    
                    try {
                        _importNotifications.emit("[AUTO-IMPORT] Found FRC robot log: $filename. Pulling...")
                        if (pullFileFromFrcRobot(host, remotePath, tempLocalFile)) {
                            val sessionId = if (lower.endsWith(".hoot")) {
                                hootDecoderService.importHootLog(tempLocalFile, config.teamId, config.seasonId, config.robotId)
                            } else {
                                val session = logParserService.parseLogFile(
                                    tempLocalFile, config.teamId, config.seasonId, config.robotId,
                                    tags = listOf("auto-import", "robot-log")
                                )
                                session.sessionId
                            }

                            // Delete the temporary local file since it's now in the database
                            tempLocalFile.delete()

                            // Delete from RoboRIO to save space
                            deleteFileFromFrcRobot(host, remotePath)
                            _importNotifications.emit("[AUTO-IMPORT] Successfully imported RoboRIO log $filename (Session ID: ${sessionId.take(8)}...)")
                            
                            // Trigger UI reload
                            onImportSuccessCallback?.invoke()
                        }
                    } catch (e: Exception) {
                        _importNotifications.emit("[AUTO-IMPORT] Failed to import RoboRIO log $filename: ${e.message}")
                        tempLocalFile.delete()
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // --- FTC ADB Helper Methods ---

    private suspend fun listFilesOnFtcRobot(adbPath: String, directory: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(adbPath, "shell", "ls", directory)
            val proc = pb.start()
            proc.errorStream.close()
            proc.outputStream.close()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            val finished = proc.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext emptyList()
            }
            output.split("\n", "\r")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.contains("No such file") && !it.contains("Permission denied") && !it.contains("ls:") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun pullFileFromFtcRobot(adbPath: String, remotePath: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(adbPath, "pull", remotePath, localFile.absolutePath)
            val proc = pb.start()
            proc.inputStream.close()
            proc.errorStream.close()
            proc.outputStream.close()
            val finished = proc.waitFor(60, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext false
            }
            proc.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun deleteFileFromFtcRobot(adbPath: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(adbPath, "shell", "rm", remotePath)
            val proc = pb.start()
            proc.inputStream.close()
            proc.errorStream.close()
            proc.outputStream.close()
            val finished = proc.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext false
            }
            proc.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun isFileInUseOnFtcRobot(adbPath: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(adbPath, "shell", "lsof", remotePath)
            val proc = pb.start()
            proc.errorStream.close()
            proc.outputStream.close()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor(10, TimeUnit.SECONDS)
            output.contains(remotePath) || output.isNotBlank()
        } catch (e: Exception) {
            false // If lsof fails, assume not in use to avoid blocking
        }
    }

    // --- FRC SSH/SCP Helper Methods ---

    private suspend fun listFilesOnFrcRobot(host: String, directory: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(
                "ssh",
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "-o", "ConnectTimeout=3",
                "-o", "BatchMode=yes",
                "lvuser@$host",
                "ls $directory"
            )
            val proc = pb.start()
            proc.errorStream.close()
            proc.outputStream.close()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            val finished = proc.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext emptyList()
            }
            output.split("\n", "\r")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.contains("No such file") && !it.contains("Permission denied") && !it.contains("ls:") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun pullFileFromFrcRobot(host: String, remotePath: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(
                "scp",
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "-o", "ConnectTimeout=5",
                "-o", "BatchMode=yes",
                "lvuser@$host:$remotePath",
                localFile.absolutePath
            )
            val proc = pb.start()
            proc.inputStream.close()
            proc.errorStream.close()
            proc.outputStream.close()
            val finished = proc.waitFor(60, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext false
            }
            proc.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun deleteFileFromFrcRobot(host: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(
                "ssh",
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "-o", "ConnectTimeout=3",
                "-o", "BatchMode=yes",
                "lvuser@$host",
                "rm $remotePath"
            )
            val proc = pb.start()
            proc.inputStream.close()
            proc.errorStream.close()
            proc.outputStream.close()
            val finished = proc.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return@withContext false
            }
            proc.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun isFileInUseOnFrcRobot(host: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(
                "ssh",
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=/dev/null",
                "-o", "ConnectTimeout=3",
                "-o", "BatchMode=yes",
                "lvuser@$host",
                "fuser $remotePath"
            )
            val proc = pb.start()
            proc.inputStream.close()
            proc.errorStream.close()
            proc.outputStream.close()
            proc.waitFor(10, TimeUnit.SECONDS)
            proc.exitValue() == 0 // fuser returns 0 if any process is using the file
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun isHostReachable(host: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val isWindows = System.getProperty("os.name").contains("win", ignoreCase = true)
            val pb = if (isWindows) {
                ProcessBuilder("ping", "-n", "1", "-w", "1000", host)
            } else {
                ProcessBuilder("ping", "-c", "1", "-W", "1", host)
            }
            val proc = pb.start()
            proc.inputStream.close()
            proc.errorStream.close()
            proc.outputStream.close()
            val finished = proc.waitFor(2, TimeUnit.SECONDS)
            finished && proc.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    // --- General Utility Methods ---

    private fun isFileInUseLocally(file: File): Boolean {
        return try {
            java.io.RandomAccessFile(file, "rw").use { }
            false
        } catch (e: Exception) {
            true
        }
    }

    private fun findAdbPath(): String {
        try {
            val proc = ProcessBuilder("adb", "--version").start()
            proc.inputStream.close()
            proc.errorStream.close()
            proc.outputStream.close()
            proc.waitFor(2, TimeUnit.SECONDS)
            return "adb"
        } catch (e: Exception) {
            // Ignore and fall through
        }
        val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (!androidHome.isNullOrEmpty()) {
            val exe = if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
                File(androidHome, "platform-tools/adb.exe")
            } else {
                File(androidHome, "platform-tools/adb")
            }
            if (exe.exists() && exe.canExecute()) {
                return exe.absolutePath
            }
        }
        val userHome = System.getProperty("user.home")
        val defaultPaths = listOf(
            File(userHome, "AppData/Local/Android/Sdk/platform-tools/adb.exe"),
            File(userHome, "Library/Android/sdk/platform-tools/adb"),
            File("/usr/bin/adb"),
            File("/usr/local/bin/adb")
        )
        for (file in defaultPaths) {
            if (file.exists() && file.canExecute()) {
                return file.absolutePath
            }
        }

        return "adb"
    }

    private fun getDefaultFrcHost(teamId: String): String {
        val teamNumber = teamId.filter { it.isDigit() }
        return if (teamNumber.length in 1..4) {
            val padded = teamNumber.padStart(4, '0')
            val te = padded.substring(0, 2).toInt()
            val am = padded.substring(2, 4).toInt()
            "10.$te.$am.2"
        } else {
            "10.0.0.2"
        }
    }
}
