package com.ares.analytics.service

import com.ares.analytics.service.log.*
import com.ares.analytics.shared.AppJsonPretty
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Automated log file importer watching local directories and remote robot storage systems (FTC ADB / FRC RoboRIO).
 *
 * Polling service executing on `Dispatchers.IO` to continuously discover and import new robot log files.
 * Supports FTC Control Hub log pulling via ADB (`adb pull /sdcard/FIRST/logs/` on port 5555) and FRC RoboRIO SCP pulling (`rio@10.TE.AM.2`).
 * Interoperates with [LogParserService] to automatically ingest `.wpilog`, `.rlog`, `.hoot`, `.dslog`, `.revlog`, `.jsonl`, and `.csv` files.
 *
 * ### Import Pipelines:
 * 1. **Local Disk Watcher**: Scans active workspace project directory for newly created `.jsonl` or `.wpilog` files.
 * 2. **FTC ADB Puller**: Polls connected Android Control Hubs via ADB daemon.
 * 3. **FRC SSH/SCP Puller**: Fetches USB driver station logs from connected RoboRIOs.
 *
 * ### Thread Safety & Performance Guarantees:
 * Executes in a cancellable background coroutine on [Dispatchers.IO]. Pushes notifications to a shared event flow [importNotifications].
 *
 * @param logParserService Central log parser service.
 * @param hootDecoderService Decoder service for CTRE `.hoot` logs.
 * @param processManagerService Service monitoring ADB connection status.
 * @param configProvider Lambda supplying current active workspace configuration.
 * @param scope Coroutine scope running background watcher loops.
 *
 * @see LogParserService
 * @see ProcessManagerService
 */
class AutoImportService(
    private val logParserService: LogParserService,
    private val hootDecoderService: HootDecoderService,
    private val processManagerService: ProcessManagerService,
    private val configProvider: () -> WorkspaceConfig?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, exception ->
        println("[AUTO-IMPORT] Unhandled exception in background scope: ${exception.message}")
    }),
    private val scanIntervalMs: Long = 5_000L
) {
    private var job: Job? = null
    private val _importNotifications = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val importNotifications: SharedFlow<String> = _importNotifications.asSharedFlow()

    private var onImportSuccessCallback: (() -> Unit)? = null
    internal data class SourceSnapshot(val size: Long, val modified: Long)
    private val sourceObservations = java.util.concurrent.ConcurrentHashMap<String, SourceSnapshot>()
    private val importedFingerprintCaches = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()

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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _importNotifications.emit("[AUTO-IMPORT] Error in scan cycle: ${e.message}")
                    e.printStackTrace()
                }
                delay(scanIntervalMs)
            }
        }
    }

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
            val files = dir.listFiles { _, name -> isSupportedLog(name) } ?: continue
            for (file in files) {
                if (file.isDirectory) continue

                val sourceId = "local:${file.absoluteFile.toPath().normalize()}"
                val snapshot = SourceSnapshot(file.length(), file.lastModified())
                if (!observeStableSource(sourceId, snapshot)) {
                    continue
                }

                val fingerprint = sourceFingerprint(sourceId, snapshot)
                val archiveDir = File(config.projectPath, "logs/imported")
                archiveDir.mkdirs()
                val manifest = File(archiveDir, IMPORT_MANIFEST_NAME)
                val quarantineManifest = quarantineManifest(config)
                if (isFingerprintImported(manifest, fingerprint) || isFingerprintImported(quarantineManifest, fingerprint)) continue
                val archivedFile = safeArchiveFile(archiveDir, fingerprint, file.name)

                try {
                    _importNotifications.emit("[AUTO-IMPORT] Found local log: ${file.name}. Importing...")
                    val baseTags = mutableListOf("auto-import")
                    if (file.name.lowercase().startsWith("sim_")) {
                        baseTags.add("simulated")
                    }
                    copyStableLocalFile(file, archivedFile, snapshot)
                    val result = if (file.name.endsWith(".hoot", ignoreCase = true)) {
                        val sessionId = hootDecoderService.importHootLog(archivedFile, config.teamId, config.seasonId, config.robotId)
                        sessionId to logParserService.buildImportReport(archivedFile, sessionId, decoderOverride = "hoot")
                            .copy(sourceName = file.name)
                    } else {
                        val imported = logParserService.parseLogFileWithReport(
                            archivedFile, config.teamId, config.seasonId, config.robotId,
                            tags = baseTags
                        )
                        imported.session.sessionId to imported.report.copy(sourceName = file.name)
                    }
                    val (sessionId, report) = result

                    writeImportReport(archivedFile, report)
                    markFingerprintImported(manifest, fingerprint)
                    if (!file.delete()) {
                        _importNotifications.emit(
                            "[AUTO-IMPORT] Imported ${file.name}; source could not be removed and will be ignored by fingerprint"
                        )
                    }
                    _importNotifications.emit("[AUTO-IMPORT] Successfully imported ${file.name} (Session ID: ${sessionId.take(8)}...)")

                    // Trigger UI reload
                    onImportSuccessCallback?.invoke()
                } catch (e: Exception) {
                    if (archivedFile.exists()) {
                        runCatching { quarantineFailedImport(config, archivedFile, fingerprint, e, file.name) }
                            .onFailure { e.addSuppressed(it) }
                    }
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
                if (isSupportedLog(lower)) {
                    val remotePath = "$robotDir$filename"
                    val sourceId = "ftc:$remotePath"
                    val snapshot = getFtcFileSnapshot(adbPath, remotePath) ?: continue
                    if (!observeStableSource(sourceId, snapshot)) continue
                    val fingerprint = sourceFingerprint(sourceId, snapshot)
                    val manifest = File(localDestDir, IMPORT_MANIFEST_NAME)
                    val quarantineManifest = quarantineManifest(config)
                    if (isFingerprintImported(manifest, fingerprint) || isFingerprintImported(quarantineManifest, fingerprint)) continue

                    // Check if file is still being written to by ARESDataLogger
                    if (isFileInUseOnFtcRobot(adbPath, remotePath)) {
                        continue
                    }
                    val tempLocalFile = File(localDestDir, ".$fingerprint.partial")

                    try {
                        _importNotifications.emit("[AUTO-IMPORT] Found FTC robot log: $filename. Pulling...")
                        if (pullFileFromFtcRobot(adbPath, remotePath, tempLocalFile)) {
                            val afterPull = getFtcFileSnapshot(adbPath, remotePath)
                            if (afterPull != snapshot || tempLocalFile.length() != snapshot.size) {
                                tempLocalFile.delete()
                                if (afterPull != null) sourceObservations[sourceId] = afterPull
                                continue
                            }
                            val archivedFile = safeArchiveFile(localDestDir, fingerprint, filename)
                            Files.move(tempLocalFile.toPath(), archivedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            val result = if (lower.endsWith(".hoot")) {
                                val sessionId = hootDecoderService.importHootLog(archivedFile, config.teamId, config.seasonId, config.robotId)
                                sessionId to logParserService.buildImportReport(archivedFile, sessionId, decoderOverride = "hoot")
                                    .copy(sourceName = filename)
                            } else {
                                val imported = logParserService.parseLogFileWithReport(
                                    archivedFile, config.teamId, config.seasonId, config.robotId,
                                    tags = listOf("auto-import", "robot-log")
                                )
                                imported.session.sessionId to imported.report.copy(sourceName = filename)
                            }
                            val (sessionId, report) = result

                            writeImportReport(archivedFile, report)
                            markFingerprintImported(manifest, fingerprint)
                            // Keep imported file safely in logs/imported archive folder
                            _importNotifications.emit("[AUTO-IMPORT] Successfully imported robot log $filename (Session ID: ${sessionId.take(8)}...)")

                            // Trigger UI reload
                            onImportSuccessCallback?.invoke()
                        }
                    } catch (e: Exception) {
                        val archivedFile = safeArchiveFile(localDestDir, fingerprint, filename)
                        if (archivedFile.exists()) {
                            runCatching { quarantineFailedImport(config, archivedFile, fingerprint, e, filename) }
                                .onFailure { e.addSuppressed(it) }
                        }
                        _importNotifications.emit("[AUTO-IMPORT] Failed to import robot log $filename: ${e.message}")
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
                if (isSupportedLog(lower)) {
                    val remotePath = "$robotDir$filename"
                    val sourceId = "frc:$host:$remotePath"
                    val snapshot = getFrcFileSnapshot(host, remotePath) ?: continue
                    if (!observeStableSource(sourceId, snapshot)) continue
                    val fingerprint = sourceFingerprint(sourceId, snapshot)
                    val manifest = File(localDestDir, IMPORT_MANIFEST_NAME)
                    val quarantineManifest = quarantineManifest(config)
                    if (isFingerprintImported(manifest, fingerprint) || isFingerprintImported(quarantineManifest, fingerprint)) continue

                    // Check if file is still being written to by DataLogManager
                    if (isFileInUseOnFrcRobot(host, remotePath)) {
                        continue
                    }
                    val tempLocalFile = File(localDestDir, ".$fingerprint.partial")

                    try {
                        _importNotifications.emit("[AUTO-IMPORT] Found FRC robot log: $filename. Pulling...")
                        if (pullFileFromFrcRobot(host, remotePath, tempLocalFile)) {
                            val afterPull = getFrcFileSnapshot(host, remotePath)
                            if (afterPull != snapshot || tempLocalFile.length() != snapshot.size) {
                                tempLocalFile.delete()
                                if (afterPull != null) sourceObservations[sourceId] = afterPull
                                continue
                            }
                            val archivedFile = safeArchiveFile(localDestDir, fingerprint, filename)
                            Files.move(tempLocalFile.toPath(), archivedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            val result = if (lower.endsWith(".hoot")) {
                                val sessionId = hootDecoderService.importHootLog(archivedFile, config.teamId, config.seasonId, config.robotId)
                                sessionId to logParserService.buildImportReport(archivedFile, sessionId, decoderOverride = "hoot")
                                    .copy(sourceName = filename)
                            } else {
                                val imported = logParserService.parseLogFileWithReport(
                                    archivedFile, config.teamId, config.seasonId, config.robotId,
                                    tags = listOf("auto-import", "robot-log")
                                )
                                imported.session.sessionId to imported.report.copy(sourceName = filename)
                            }
                            val (sessionId, report) = result

                            writeImportReport(archivedFile, report)
                            markFingerprintImported(manifest, fingerprint)
                            // Keep imported file safely in logs/imported archive folder
                            _importNotifications.emit("[AUTO-IMPORT] Successfully imported RoboRIO log $filename (Session ID: ${sessionId.take(8)}...)")

                            // Trigger UI reload
                            onImportSuccessCallback?.invoke()
                        }
                    } catch (e: Exception) {
                        val archivedFile = safeArchiveFile(localDestDir, fingerprint, filename)
                        if (archivedFile.exists()) {
                            runCatching { quarantineFailedImport(config, archivedFile, fingerprint, e, filename) }
                                .onFailure { e.addSuppressed(it) }
                        }
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

    private suspend fun getFtcFileSnapshot(adbPath: String, remotePath: String): SourceSnapshot? = withContext(Dispatchers.IO) {
        readSnapshotFromProcess(ProcessBuilder(adbPath, "shell", "stat", "-c", "%s:%Y", remotePath))
    }

    private suspend fun isFileInUseOnFtcRobot(adbPath: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(adbPath, "shell", "lsof", remotePath)
            val proc = pb.start()
            proc.errorStream.close()
            proc.outputStream.close()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor(10, TimeUnit.SECONDS)
            // Only treat an explicit lsof match on the remote path as "in use". The previous
            // `|| output.isNotBlank()` short-circuited to true on ANY lsof output (e.g. a
            // usage banner from a missing binary), blocking every import (AUDIT M11).
            output.contains(remotePath)
        } catch (e: Exception) {
            false // If lsof fails, assume not in use to avoid blocking
        }
    }

    // --- FRC SSH/SCP Helper Methods ---

    private suspend fun listFilesOnFrcRobot(host: String, directory: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(
                listOf("ssh") + sshOptions(3) + listOf("lvuser@$host", "ls ${shellQuote(directory)}")
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
                listOf("scp") + sshOptions(5) +
                    listOf("lvuser@$host:${shellQuote(remotePath)}", localFile.absolutePath)
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

    private suspend fun getFrcFileSnapshot(host: String, remotePath: String): SourceSnapshot? = withContext(Dispatchers.IO) {
        readSnapshotFromProcess(
            ProcessBuilder(
                listOf("ssh") + sshOptions(3) +
                    listOf("lvuser@$host", "stat -c '%s:%Y' -- ${shellQuote(remotePath)}")
            )
        )
    }

    private suspend fun isFileInUseOnFrcRobot(host: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(
                listOf("ssh") + sshOptions(3) +
                    listOf("lvuser@$host", "fuser ${shellQuote(remotePath)}")
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

    internal fun observeStableSource(sourceId: String, snapshot: SourceSnapshot): Boolean {
        return sourceObservations.put(sourceId, snapshot) == snapshot
    }

    internal fun sourceFingerprint(sourceId: String, snapshot: SourceSnapshot): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = "$sourceId\u0000${snapshot.size}\u0000${snapshot.modified}".toByteArray(Charsets.UTF_8)
        return digest.digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    internal fun safeArchiveFile(directory: File, fingerprint: String, sourceName: String): File {
        val basename = sourceName.substringAfterLast('/').substringAfterLast('\\').trim()
        require(basename.isNotEmpty() && basename != "." && basename != "..") {
            "Invalid log filename"
        }
        val sanitized = buildString(basename.length) {
            basename.forEach { character ->
                append(
                    when {
                        character.isLetterOrDigit() -> character
                        character == '.' || character == '_' || character == '-' || character == ' ' -> character
                        else -> '_'
                    }
                )
            }
        }.trim().take(MAX_ARCHIVE_BASENAME_LENGTH)
        require(sanitized.isNotEmpty() && isSupportedLog(sanitized)) { "Unsupported log filename" }

        val root = directory.toPath().toAbsolutePath().normalize()
        val target = root.resolve("${fingerprint.take(12)}_$sanitized").normalize()
        require(target.parent == root && target.startsWith(root)) { "Log archive path escaped its root" }
        return target.toFile()
    }

    private fun importedFingerprints(manifest: File): MutableSet<String> {
        return importedFingerprintCaches.computeIfAbsent(manifest.absolutePath) {
            java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply {
                if (manifest.exists()) {
                    manifest.useLines { lines ->
                        lines.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
                    }
                }
            }
        }
    }

    internal fun isFingerprintImported(manifest: File, fingerprint: String): Boolean {
        return fingerprint in importedFingerprints(manifest)
    }

    internal fun markFingerprintImported(manifest: File, fingerprint: String) {
        val fingerprints = importedFingerprints(manifest)
        if (!fingerprints.add(fingerprint)) return
        manifest.parentFile?.mkdirs()
        try {
            FileOutputStream(manifest, true).use { output ->
                output.write((fingerprint + "\n").toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
        } catch (e: Exception) {
            fingerprints.remove(fingerprint)
            throw e
        }
    }

    internal fun quarantineFailedImport(
        config: WorkspaceConfig,
        archivedFile: File,
        fingerprint: String,
        failure: Throwable,
        sourceName: String = archivedFile.name
    ): File {
        val quarantineDir = File(config.projectPath, "logs/quarantine")
        quarantineDir.mkdirs()
        val quarantinedFile = File(quarantineDir, archivedFile.name)
        Files.move(archivedFile.toPath(), quarantinedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val report = logParserService.buildRejectedImportReport(quarantinedFile, failure)
            .copy(sourceName = sourceName)
        writeImportReport(quarantinedFile, report)
        markFingerprintImported(File(quarantineDir, QUARANTINE_MANIFEST_NAME), fingerprint)
        return quarantinedFile
    }

    internal fun writeImportReport(logFile: File, report: ImportReport): File {
        val reportFile = File(logFile.parentFile, logFile.name + IMPORT_REPORT_SUFFIX)
        val temporaryFile = File(reportFile.parentFile, ".${reportFile.name}.tmp")
        temporaryFile.writeText(AppJsonPretty.encodeToString(report))
        try {
            Files.move(
                temporaryFile.toPath(),
                reportFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporaryFile.toPath(), reportFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return reportFile
    }

    private fun quarantineManifest(config: WorkspaceConfig): File =
        File(config.projectPath, "logs/quarantine/$QUARANTINE_MANIFEST_NAME")

    private fun isSupportedLog(name: String): Boolean {
        val lower = name.lowercase()
        return SUPPORTED_EXTENSIONS.any(lower::endsWith)
    }

    private fun copyStableLocalFile(source: File, destination: File, expected: SourceSnapshot) {
        destination.parentFile?.mkdirs()
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val afterCopy = SourceSnapshot(source.length(), source.lastModified())
        if (afterCopy != expected || destination.length() != expected.size) {
            destination.delete()
            throw java.io.IOException("Log changed while it was being copied")
        }
    }

    private fun sshOptions(connectTimeoutSeconds: Int): List<String> = listOf(
        "-o", "StrictHostKeyChecking=yes",
        "-o", "ConnectTimeout=$connectTimeoutSeconds",
        "-o", "BatchMode=yes"
    )

    private fun readSnapshotFromProcess(processBuilder: ProcessBuilder): SourceSnapshot? {
        return try {
            val process = processBuilder.start()
            process.errorStream.close()
            process.outputStream.close()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                val parts = output.lineSequence().firstOrNull()?.trim()?.split(':') ?: return null
                if (parts.size != 2) null else SourceSnapshot(parts[0].toLong(), parts[1].toLong())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

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
        val teamNumber = teamId.filter(Char::isDigit).toIntOrNull()
        return if (teamNumber != null && teamNumber in 1..25_599) {
            val te = teamNumber / 100
            val am = teamNumber % 100
            "10.$te.$am.2"
        } else {
            "10.0.0.2"
        }
    }

    companion object {
        internal const val IMPORT_MANIFEST_NAME = ".auto-import-index"
        internal const val QUARANTINE_MANIFEST_NAME = ".auto-import-quarantine-index"
        internal const val IMPORT_REPORT_SUFFIX = ".import-report.json"
        internal const val MAX_ARCHIVE_BASENAME_LENGTH = 160
        internal val SUPPORTED_EXTENSIONS = setOf(
            ".wpilog", ".wpilogxz", ".jsonl", ".csv", ".parquet", ".hoot",
            ".dslog", ".rlog", ".revlog", ".log"
        )
    }
}
