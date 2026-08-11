package com.ares.analytics.service

import com.ares.analytics.shared.League
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

enum class AresGenerationPhase { IDLE, RUNNING, SUCCEEDED, FAILED }

data class AresGenerationState(
    val phase: AresGenerationPhase = AresGenerationPhase.IDLE,
    val message: String = "",
    val contentHash: String? = null
)

/** Small testable boundary used by offline authoring screens. */
interface AresProjectGenerator {
    val aresGenerationState: StateFlow<AresGenerationState>
    fun generateAresProject(projectPath: String, league: League)
}

/**
 * Service managing external OS process lifecycle execution for Gradle builds, ADB logcat streams, and physics simulators.
 *
 * Spawns and monitors underlying system processes for compiling FTC/FRC codebases (`./gradlew assembleDebug`), streaming Android
 * Control Hub logs (`adb logcat`), and launching desktop robot physics simulators (`DesktopSimLauncher`).
 *
 * ### Process Management Tasks:
 * - **Gradle Compilation**: Invokes local Gradle wrapper (`gradlew.bat` or `./gradlew`) with real-time output line buffering.
 * - **ADB Daemon Monitoring**: Monitors ADB connection state to physical Control Hubs on port 5555.
 * - **Simulator Launcher**: Executes JVM desktop physics simulator processes with cancellation supervisor jobs.
 *
 * ### Thread Safety & Performance Guarantees:
 * Process standard output/error reading runs asynchronously on `Dispatchers.IO`. Utilizes `SharedFlow(replay = 200)` to buffer process logs without thread blocking.
 *
 * @see AutoImportService
 * @see TargetScannerService
 */
class ProcessManagerService : AresProjectGenerator {

    private val _buildOutput = MutableSharedFlow<String>(replay = 200)
    val buildOutput: SharedFlow<String> = _buildOutput.asSharedFlow()

    private val _logcatOutput = MutableSharedFlow<String>(replay = 200)
    val logcatOutput: SharedFlow<String> = _logcatOutput.asSharedFlow()

    private val _isSimRunning = MutableStateFlow(false)
    val isSimRunning: StateFlow<Boolean> = _isSimRunning.asStateFlow()

    private val _isBuildRunning = MutableStateFlow(false)
    val isBuildRunning: StateFlow<Boolean> = _isBuildRunning.asStateFlow()

    private val _aresGenerationState = MutableStateFlow(AresGenerationState())
    override val aresGenerationState: StateFlow<AresGenerationState> = _aresGenerationState.asStateFlow()

    private val _adbConnected = MutableStateFlow(false)
    val adbConnected: StateFlow<Boolean> = _adbConnected.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeBuildJob: Job? = null
    private var activeLogcatJob: Job? = null
    private var activeSimJob: Job? = null
    private var adbMonitorJob: Job? = null

    private var buildProcess: Process? = null
    private var logcatProcess: Process? = null
    private var simProcess: Process? = null

    init {
        // Start periodic ADB connection check
        startAdbMonitoring()
    }

    private fun startAdbMonitoring() {
        adbMonitorJob?.cancel()
        adbMonitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    val pb = ProcessBuilder("adb", "devices")
                    val proc = pb.start()
                    val output = proc.inputStream.bufferedReader().use { it.readText() }
                    proc.errorStream.close()
                    proc.outputStream.close()
                    val monitorFinished = proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                    if (!monitorFinished) {
                        proc.destroyForcibly()
                    }
                    val isConnected = monitorFinished && (output.contains("192.168.43.1:5555") || output.contains("device\n") || output.contains("device\r"))
                    _adbConnected.value = isConnected
                } catch (e: Exception) {
                    _adbConnected.value = false
                }
                delay(5000)
            }
        }
    }

    fun runBuild(projectPath: String, league: League) {
        killActiveBuild()

        activeBuildJob = serviceScope.launch {
            _isBuildRunning.value = true
            try {
                val isWindows = System.getProperty("os.name").contains("win", ignoreCase = true)
                val cmd = if (isWindows) {
                    when (league) {
                        League.FTC -> listOf("cmd.exe", "/c", "gradlew.bat", ":TeamCode:assembleDebug")
                        League.FRC -> listOf("cmd.exe", "/c", "gradlew.bat", "assemble")
                    }
                } else {
                    when (league) {
                        League.FTC -> listOf("./gradlew", ":TeamCode:assembleDebug")
                        League.FRC -> listOf("./gradlew", "assemble")
                    }
                }

                _buildOutput.emit("[SYSTEM] Starting Gradle build: ${cmd.joinToString(" ")}")
                val pb = ProcessBuilder(cmd)
                    .directory(File(projectPath))
                    .redirectErrorStream(true)
                val proc = pb.start()
                buildProcess = proc

                proc.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        _buildOutput.emit(line)
                    }
                }
                val exitCode = proc.waitFor()
                _buildOutput.emit("[SYSTEM] Build finished with exit code $exitCode")

                // Auto-deploy on success for FTC
                if (exitCode == 0 && league == League.FTC) {
                    runAdbDeploy(projectPath)
                }
            } catch (e: Exception) {
                _buildOutput.emit("[SYSTEM] Error running build: ${e.message}")
            } finally {
                _isBuildRunning.value = false
            }
        }
    }

    /**
     * Regenerates the checked-in Kotlin bridge from canonical `.ares` documents.
     *
     * The wrapper is launched through its JAR with a fixed argument list. This avoids interpreting
     * the student-selected project path as shell syntax on Windows while retaining normal Gradle
     * wrapper behavior. A robot connection is never involved.
     */
    override fun generateAresProject(projectPath: String, league: League) {
        killActiveBuild()
        activeBuildJob = serviceScope.launch {
            _isBuildRunning.value = true
            _aresGenerationState.value = AresGenerationState(
                AresGenerationPhase.RUNNING,
                "Saving complete. Generating Kotlin from the local project..."
            )
            val diagnosticLines = ArrayDeque<String>(GENERATION_DIAGNOSTIC_LINE_LIMIT)
            try {
                val root = requireSafeProjectRoot(projectPath)
                val wrapperJar = File(root, "gradle/wrapper/gradle-wrapper.jar").canonicalFile
                require(wrapperJar.isFile && wrapperJar.toPath().startsWith(root.toPath())) {
                    "This directory does not contain gradle/wrapper/gradle-wrapper.jar"
                }
                require(File(root, ".ares").canonicalFile.isDirectory) {
                    "This directory does not contain canonical .ares project documents"
                }
                val javaExecutable = File(
                    System.getProperty("java.home"),
                    "bin/${if (System.getProperty("os.name").contains("win", true)) "java.exe" else "java"}"
                ).canonicalFile
                require(javaExecutable.isFile) { "The app Java runtime could not be found" }

                val command = listOf(
                    javaExecutable.path,
                    "-classpath",
                    wrapperJar.path,
                    "org.gradle.wrapper.GradleWrapperMain",
                    "generateAresProject",
                    "--console=plain"
                )
                _buildOutput.emit("[ARES] Generating checked-in Kotlin from canonical project files")
                val process = ProcessBuilder(command)
                    .directory(root)
                    .redirectErrorStream(true)
                    .start()
                buildProcess = process
                process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (diagnosticLines.size == GENERATION_DIAGNOSTIC_LINE_LIMIT) diagnosticLines.removeFirst()
                        diagnosticLines.addLast(line)
                        _buildOutput.emit(line)
                        _aresGenerationState.value = AresGenerationState(AresGenerationPhase.RUNNING, line.take(500))
                    }
                }
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    error(
                        diagnosticLines.joinToString("\n").ifBlank {
                            "Gradle generation failed with exit code $exitCode"
                        }
                    )
                }
                val hash = readGeneratedContentHash(root, league)
                val suffix = hash?.let { " Content ${it.take(12)}..." }.orEmpty()
                _aresGenerationState.value = AresGenerationState(
                    AresGenerationPhase.SUCCEEDED,
                    "Generated Kotlin is current.$suffix Robot builds will still verify it is not stale.",
                    hash
                )
                _buildOutput.emit("[ARES] Generation finished successfully.$suffix")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message?.takeLast(GENERATION_DIAGNOSTIC_CHARACTER_LIMIT)
                    ?: "ARES project generation failed"
                _aresGenerationState.value = AresGenerationState(AresGenerationPhase.FAILED, message)
                _buildOutput.emit("[ARES] Generation failed: $message")
            } finally {
                buildProcess = null
                _isBuildRunning.value = false
            }
        }
    }

    fun runSimulation(projectPath: String, league: League, simulatorCommand: String? = null) {
        killActiveSim()

        activeSimJob = serviceScope.launch {
            try {
                _isSimRunning.value = true
                val isWindows = System.getProperty("os.name").contains("win", ignoreCase = true)
                val userCmd = simulatorCommand?.takeIf { it.isNotBlank() }
                val fatJarFile = File(projectPath, "simulator/build/libs/simulator-all.jar")
                val javaExe = File(System.getProperty("java.home"), "bin/${if (isWindows) "java.exe" else "java"}").path
                val cmd = when {
                    userCmd != null && isWindows -> listOf("cmd.exe", "/d", "/s", "/c", userCmd)
                    userCmd != null -> listOf("sh", "-c", userCmd)
                    fatJarFile.exists() -> listOf(javaExe, "-jar", fatJarFile.absolutePath)
                    isWindows && league == League.FTC -> listOf("cmd.exe", "/c", "gradlew.bat", ":TeamCode:runSim")
                    isWindows -> listOf("cmd.exe", "/c", "gradlew.bat", "simulateJava")
                    league == League.FTC -> listOf("./gradlew", ":TeamCode:runSim")
                    else -> listOf("./gradlew", "simulateJava")
                }

                _buildOutput.emit("[SYSTEM] Starting Simulation: ${cmd.joinToString(" ")}")
                val pb = ProcessBuilder(cmd)
                    .directory(File(projectPath))
                    .redirectErrorStream(true)
                val proc = pb.start()
                simProcess = proc

                proc.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        _buildOutput.emit(line)
                    }
                }
                val exitCode = proc.waitFor()
                _buildOutput.emit("[SYSTEM] Simulation finished with exit code $exitCode")
            } catch (e: Exception) {
                _buildOutput.emit("[SYSTEM] Error running simulation: ${e.message}")
            } finally {
                _isSimRunning.value = false
            }
        }
    }

    fun startLogcat() {
        killActiveLogcat()

        activeLogcatJob = serviceScope.launch {
            try {
                _logcatOutput.emit("[SYSTEM] Starting ADB logcat stream...")
                val adb = resolveAdbPath()
                val pb = ProcessBuilder(adb, "logcat", "-v", "time")
                    .redirectErrorStream(true)
                val proc = pb.start()
                logcatProcess = proc

                proc.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        _logcatOutput.emit(line)
                    }
                }
            } catch (e: Exception) {
                _logcatOutput.emit("[SYSTEM] Error streaming logcat: ${e.message}")
            }
        }
    }

    private fun resolveAdbPath(): String {
        val platformTools = File(System.getenv("LOCALAPPDATA") ?: "", "Android/Sdk/platform-tools/adb.exe")
        if (platformTools.exists()) return platformTools.absolutePath
        val adbMac = File(System.getProperty("user.home"), "Library/Android/sdk/platform-tools/adb")
        if (adbMac.exists()) return adbMac.absolutePath
        return "adb"
    }

    private suspend fun runAdbDeploy(projectPath: String) {
        _buildOutput.emit("[SYSTEM] Auto-deploying to FTC Control Hub...")
        val adb = resolveAdbPath()
        val connectPb = ProcessBuilder(adb, "connect", "192.168.43.1:5555").redirectErrorStream(true)
        val connectProc = connectPb.start()
        connectProc.inputStream.close()
        connectProc.errorStream.close()
        connectProc.outputStream.close()
        val finished = connectProc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            connectProc.destroyForcibly()
            _buildOutput.emit("[SYSTEM] Warning: adb connect timed out. Attempting install anyway.")
        }
        val connectExit = if (finished) connectProc.exitValue() else -1
        if (connectExit != 0 && finished) {
            _buildOutput.emit("[SYSTEM] Warning: adb connect returned non-zero exit code $connectExit. Attempting install anyway.")
        }

        // Try installing debug apk
        var apkPath = File(projectPath, "ftc-app/TeamCode/build/outputs/apk/debug/TeamCode-debug.apk")
        if (!apkPath.exists()) {
            apkPath = File(projectPath, "TeamCode/build/outputs/apk/debug/TeamCode-debug.apk")
        }
        val installPb = ProcessBuilder(adb, "install", "-r", apkPath.absolutePath).redirectErrorStream(true)
        val installProc = installPb.start()
        installProc.errorStream.close()
        installProc.outputStream.close()

        installProc.inputStream.bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                _buildOutput.emit("[ADB] $line")
            }
        }
        val installFinished = installProc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
        if (!installFinished) {
            installProc.destroyForcibly()
            _buildOutput.emit("[SYSTEM] Error: ADB Deploy timed out.")
        }
        val installExit = if (installFinished) installProc.exitValue() else -1
        _buildOutput.emit("[SYSTEM] ADB Deploy finished with exit code $installExit")

        // Auto restart logcat on successful deploy
        if (installExit == 0) {
            startLogcat()
        }
    }

    fun killActiveBuild() {
        val generationWasRunning = _aresGenerationState.value.phase == AresGenerationPhase.RUNNING
        try {
            buildProcess?.descendants()?.forEach { it.destroyForcibly() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        buildProcess?.destroyForcibly()
        buildProcess = null
        activeBuildJob?.cancel()
        activeBuildJob = null
        _isBuildRunning.value = false
        if (generationWasRunning) {
            _aresGenerationState.value = AresGenerationState(AresGenerationPhase.FAILED, "Generation canceled.")
        }
    }

    fun killActiveLogcat() {
        logcatProcess?.destroyForcibly()
        logcatProcess = null
        activeLogcatJob?.cancel()
        activeLogcatJob = null
    }

    fun killActiveSim() {
        try {
            simProcess?.descendants()?.forEach { it.destroyForcibly() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        simProcess?.destroyForcibly()
        simProcess = null
        activeSimJob?.cancel()
        activeSimJob = null
        _isSimRunning.value = false
    }

    fun shutdown() {
        killActiveBuild()
        killActiveLogcat()
        killActiveSim()
        adbMonitorJob?.cancel()
        serviceScope.cancel()
    }

    private fun requireSafeProjectRoot(projectPath: String): File {
        require(projectPath.isNotBlank()) { "Choose a robot project directory first" }
        val root = File(projectPath).canonicalFile
        require(root.isDirectory) { "The selected project directory does not exist" }
        return root
    }

    private fun readGeneratedContentHash(root: File, league: League): String? {
        val relative = when (league) {
            League.FTC -> "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/generated/GeneratedAresProject.kt"
            League.FRC -> "src/main/kotlin/com/areslib/frc/generated/GeneratedAresProject.kt"
        }
        val generated = File(root, relative).canonicalFile
        if (!generated.isFile || !generated.toPath().startsWith(root.toPath())) return null
        return GENERATED_CONTENT_HASH.find(generated.readText())?.groupValues?.get(1)
    }

    private companion object {
        const val GENERATION_DIAGNOSTIC_LINE_LIMIT = 24
        const val GENERATION_DIAGNOSTIC_CHARACTER_LIMIT = 4_000
        val GENERATED_CONTENT_HASH = Regex("CONTENT_SHA256:\\s*String\\s*=\\s*\"([0-9a-fA-F]{64})\"")
    }
}

