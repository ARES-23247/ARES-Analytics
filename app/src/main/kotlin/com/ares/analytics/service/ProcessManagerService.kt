package com.ares.analytics.service

import com.ares.analytics.shared.League
import com.areslib.codegen.GeneratedSubsystemFile
import com.areslib.codegen.SubsystemKotlinCodegenTarget
import com.areslib.codegen.SubsystemKotlinGenerator
import com.areslib.codegen.SubsystemStarterPlan
import com.areslib.codegen.SubsystemStarterReconciler
import com.areslib.subsystem.SubsystemDocumentCodec
import com.areslib.subsystem.SubsystemPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    fun previewSubsystemStarters(projectPath: String, league: League): SubsystemStarterPlan
    fun applySubsystemStarters(projectPath: String, league: League, confirmationToken: String? = null)
}

private enum class BuildOperationKind { BUILD, GENERATION, TEST }

private data class BuildOwnership(
    val generation: Long,
    val kind: BuildOperationKind?,
    val job: Job?,
    val process: Process?
)

private data class SubsystemStarterInputs(
    val root: File,
    val files: List<GeneratedSubsystemFile>,
)

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
class ProcessManagerService internal constructor(
    private val monitorAdbConnection: Boolean,
    aresRepositoryUri: String?,
) : AresProjectGenerator {

    constructor() : this(
        monitorAdbConnection = true,
        aresRepositoryUri = System.getProperty(ARES_REPOSITORY_URI_PROPERTY),
    )

    internal constructor(monitorAdbConnection: Boolean) : this(
        monitorAdbConnection = monitorAdbConnection,
        aresRepositoryUri = null,
    )

    private val aresRepositoryFileUri = aresRepositoryUri
        ?.takeIf(String::isNotBlank)
        ?.let(::validatedAresRepositoryUri)
    private val aresRepositoryArgument = aresRepositoryFileUri
        ?.let { "-ParesRepository=$it" }

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

    private val buildLifecycleMutex = Mutex()
    private val buildStateLock = Any()
    private val buildRequestId = AtomicLong(0L)
    private val shuttingDown = AtomicBoolean(false)

    @Volatile
    private var activeBuildJob: Job? = null
    private var activeLogcatJob: Job? = null
    private var activeSimJob: Job? = null
    private var adbMonitorJob: Job? = null

    @Volatile
    private var buildProcess: Process? = null
    private var activeBuildGeneration = 0L
    private var activeBuildKind: BuildOperationKind? = null
    private var logcatProcess: Process? = null
    private var simProcess: Process? = null

    init {
        // Start periodic ADB connection check
        if (monitorAdbConnection) startAdbMonitoring()
    }

    private fun startAdbMonitoring() {
        adbMonitorJob?.cancel()
        adbMonitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    val pb = ProcessBuilder("adb", "devices").redirectErrorStream(true)
                    val proc = pb.start()
                    val output = StringBuilder()
                    val exitCode = waitForProcess(proc, 3) { line ->
                        if (output.length < MAX_MONITOR_OUTPUT_CHARS) output.appendLine(line)
                    }
                    val text = output.toString()
                    val isConnected = exitCode == 0 &&
                        (text.contains("192.168.43.1:5555") || text.contains("device\n") || text.contains("device\r"))
                    _adbConnected.value = isConnected
                } catch (e: Exception) {
                    _adbConnected.value = false
                }
                delay(5000)
            }
        }
    }

    fun runBuild(projectPath: String, league: League) {
        enqueueBuildOperation(BuildOperationKind.BUILD) { generation ->
            executeBuild(generation, projectPath, league)
        }
    }

    private suspend fun executeBuild(generation: Long, projectPath: String, league: League) {
        try {
            val isWindows = System.getProperty("os.name").contains("win", ignoreCase = true)
            val command = withAresRepository(if (isWindows) {
                when (league) {
                    League.FTC -> listOf("cmd.exe", "/c", "gradlew.bat", ":TeamCode:assembleDebug")
                    League.FRC -> listOf("cmd.exe", "/c", "gradlew.bat", "assemble")
                }
            } else {
                when (league) {
                    League.FTC -> listOf("./gradlew", ":TeamCode:assembleDebug")
                    League.FRC -> listOf("./gradlew", "assemble")
                }
            })

            _buildOutput.emit("[SYSTEM] Starting Gradle build: ${command.joinToString(" ")}")
            val exitCode = runOwnedBuildProcess(
                generation,
                withAresRepositoryEnvironment(ProcessBuilder(command)
                    .directory(File(projectPath))
                    .redirectErrorStream(true))
            ) { line -> _buildOutput.emit(line) }
            currentCoroutineContext().ensureActive()
            _buildOutput.emit("[SYSTEM] Build finished with exit code $exitCode")

            // Auto-deploy on success for FTC
            if (exitCode == 0 && league == League.FTC) {
                runAdbDeploy(projectPath)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            _buildOutput.emit("[SYSTEM] Error running build: ${error.message}")
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
        enqueueBuildOperation(BuildOperationKind.GENERATION) { generation ->
            executeAresGeneration(generation, projectPath, league)
        }
    }

    override fun previewSubsystemStarters(projectPath: String, league: League): SubsystemStarterPlan {
        val inputs = subsystemStarterInputs(requireSafeProjectRoot(projectPath), league)
        return SubsystemStarterReconciler.plan(inputs.root.toPath(), inputs.files)
    }

    override fun applySubsystemStarters(projectPath: String, league: League, confirmationToken: String?) {
        val root = requireSafeProjectRoot(projectPath)
        // Re-plan immediately before scheduling so stale UI tokens fail closed in the Gradle task.
        val plan = previewSubsystemStarters(root.path, league)
        if (plan.hasReplacements) {
            require(confirmationToken != null && confirmationToken == plan.confirmationToken) {
                "The generated starter proposal changed. Review the new diff before replacing files."
            }
        }
        enqueueBuildOperation(BuildOperationKind.GENERATION) { generation ->
            executeSubsystemStarterGeneration(generation, root, league, confirmationToken)
        }
    }

    private suspend fun executeSubsystemStarterGeneration(
        generation: Long,
        root: File,
        league: League,
        confirmationToken: String?,
    ) {
        updateGenerationStateIfOwner(
            generation,
            AresGenerationState(AresGenerationPhase.RUNNING, "Applying reviewed subsystem starters and generated plumbing...")
        )
        val taskName = if (confirmationToken == null) "generateSubsystemStarters" else "replaceSubsystemStarters"
        val task = if (league == League.FTC) ":TeamCode:$taskName" else taskName
        val wrapperJar = File(root, "gradle/wrapper/gradle-wrapper.jar").canonicalFile
        require(wrapperJar.isFile && wrapperJar.toPath().startsWith(root.toPath())) {
            "This directory does not contain gradle/wrapper/gradle-wrapper.jar"
        }
        val javaExecutable = File(
            System.getProperty("java.home"),
            "bin/${if (System.getProperty("os.name").contains("win", true)) "java.exe" else "java"}"
        ).canonicalFile
        val command = withAresRepository(buildList {
            add(javaExecutable.path)
            add("-classpath")
            add(wrapperJar.path)
            add("org.gradle.wrapper.GradleWrapperMain")
            add(task)
            add("--console=plain")
            confirmationToken?.let { add("-Pares.subsystemReplacementToken=$it") }
        })
        val diagnosticLines = ArrayDeque<String>(GENERATION_DIAGNOSTIC_LINE_LIMIT)
        try {
            val exitCode = runOwnedBuildProcess(
                generation,
                withAresRepositoryEnvironment(ProcessBuilder(command).directory(root).redirectErrorStream(true)),
            ) { line ->
                if (diagnosticLines.size == GENERATION_DIAGNOSTIC_LINE_LIMIT) diagnosticLines.removeFirst()
                diagnosticLines.addLast(line)
                _buildOutput.emit(line)
                updateGenerationStateIfOwner(generation, AresGenerationState(AresGenerationPhase.RUNNING, line.take(500)))
            }
            check(exitCode == 0) {
                diagnosticLines.joinToString("\n").takeLast(GENERATION_DIAGNOSTIC_CHARACTER_LIMIT)
                    .ifBlank { "Subsystem starter generation failed with exit code $exitCode" }
            }
            updateGenerationStateIfOwner(
                generation,
                AresGenerationState(AresGenerationPhase.SUCCEEDED, "Subsystem starters and generated plumbing are current.")
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            val message = error.message?.takeLast(GENERATION_DIAGNOSTIC_CHARACTER_LIMIT)
                ?: "Subsystem starter generation failed"
            updateGenerationStateIfOwner(generation, AresGenerationState(AresGenerationPhase.FAILED, message))
            _buildOutput.emit("[ARES] Subsystem starter generation failed: $message")
        }
    }

    private fun subsystemStarterInputs(root: File, league: League): SubsystemStarterInputs {
        val platform = if (league == League.FTC) SubsystemPlatform.FTC else SubsystemPlatform.FRC
        val basePackage = if (league == League.FTC) {
            "org.firstinspires.ftc.teamcode.subsystems"
        } else {
            "com.areslib.frc.subsystems"
        }
        val starterRoot = if (league == League.FTC) {
            File(root, "TeamCode/src/main/java/${basePackage.replace('.', '/')}")
        } else {
            File(root, "src/main/kotlin/${basePackage.replace('.', '/')}")
        }.canonicalFile
        require(starterRoot.toPath().startsWith(root.toPath())) { "Subsystem starter root escaped the project" }
        val documentsRoot = File(root, ".ares/subsystems").canonicalFile
        val documents = documentsRoot.listFiles { file -> file.isFile && file.extension.equals("aressubsystem", true) }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
            .map { SubsystemDocumentCodec.decode(it.readText()) }
            .filter { it.platform == platform }
        val target = SubsystemKotlinCodegenTarget(platform, basePackage)
        return SubsystemStarterInputs(starterRoot, documents.flatMap { SubsystemKotlinGenerator.generate(it, target) })
    }

    private suspend fun executeAresGeneration(generation: Long, projectPath: String, league: League) {
        updateGenerationStateIfOwner(
            generation,
            AresGenerationState(
                AresGenerationPhase.RUNNING,
                "Saving complete. Generating Kotlin from the local project..."
            )
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

            val command = withAresRepository(listOf(
                javaExecutable.path,
                "-classpath",
                wrapperJar.path,
                "org.gradle.wrapper.GradleWrapperMain",
                "generateAresProject",
                "--console=plain"
            ))
            _buildOutput.emit("[ARES] Generating checked-in Kotlin from canonical project files")
            val exitCode = runOwnedBuildProcess(
                generation,
                withAresRepositoryEnvironment(ProcessBuilder(command)
                    .directory(root)
                    .redirectErrorStream(true))
            ) { line ->
                if (diagnosticLines.size == GENERATION_DIAGNOSTIC_LINE_LIMIT) diagnosticLines.removeFirst()
                diagnosticLines.addLast(line)
                _buildOutput.emit(line)
                updateGenerationStateIfOwner(
                    generation,
                    AresGenerationState(AresGenerationPhase.RUNNING, line.take(500))
                )
            }
            currentCoroutineContext().ensureActive()
            if (exitCode != 0) {
                error(
                    diagnosticLines.joinToString("\n").ifBlank {
                        "Gradle generation failed with exit code $exitCode"
                    }
                )
            }
            val hash = readGeneratedContentHash(root, league)
            val suffix = hash?.let { " Content ${it.take(12)}..." }.orEmpty()
            updateGenerationStateIfOwner(
                generation,
                AresGenerationState(
                    AresGenerationPhase.SUCCEEDED,
                    "Generated Kotlin is current.$suffix Robot builds will still verify it is not stale.",
                    hash
                )
            )
            _buildOutput.emit("[ARES] Generation finished successfully.$suffix")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            val message = error.message?.takeLast(GENERATION_DIAGNOSTIC_CHARACTER_LIMIT)
                ?: "ARES project generation failed"
            updateGenerationStateIfOwner(
                generation,
                AresGenerationState(AresGenerationPhase.FAILED, message)
            )
            _buildOutput.emit("[ARES] Generation failed: $message")
        }
    }

    private fun enqueueBuildOperation(
        kind: BuildOperationKind,
        operation: suspend (generation: Long) -> Unit
    ) {
        if (shuttingDown.get()) return
        val requestId = buildRequestId.incrementAndGet()
        serviceScope.launch {
            buildLifecycleMutex.withLock {
                if (shuttingDown.get() || requestId != buildRequestId.get()) return@withLock
                stopActiveBuildLocked()
                if (shuttingDown.get() || requestId != buildRequestId.get()) return@withLock

                val replacement = serviceScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        operation(requestId)
                    } finally {
                        releaseBuildOwnership(requestId)
                    }
                }
                synchronized(buildStateLock) {
                    activeBuildGeneration = requestId
                    activeBuildKind = kind
                    activeBuildJob = replacement
                    buildProcess = null
                }
                _isBuildRunning.value = true
                replacement.start()
            }
        }
    }

    /** Test seam that exercises the same ownership/replacement path as builds and generation. */
    internal fun runManagedProcessForTest(command: List<String>, generationOperation: Boolean = false) {
        require(command.isNotEmpty()) { "Process command must not be empty" }
        val kind = if (generationOperation) BuildOperationKind.GENERATION else BuildOperationKind.TEST
        enqueueBuildOperation(kind) { generation ->
            if (generationOperation) {
                updateGenerationStateIfOwner(
                    generation,
                    AresGenerationState(AresGenerationPhase.RUNNING, "Test generation running")
                )
            }
            runOwnedBuildProcess(
                generation,
                ProcessBuilder(command).redirectErrorStream(true)
            ) { }
        }
    }

    internal suspend fun awaitBuildIdleForTest() {
        while (true) {
            val active = synchronized(buildStateLock) { activeBuildJob } ?: return
            active.join()
            if (synchronized(buildStateLock) { activeBuildJob == null }) return
        }
    }

    private suspend fun runOwnedBuildProcess(
        generation: Long,
        processBuilder: ProcessBuilder,
        onLine: suspend (String) -> Unit
    ): Int {
        var process: Process? = null
        try {
            // Capture the handle even if cancellation arrives while the OS is creating it; the
            // ownership check immediately below then kills it instead of leaking an untracked child.
            val started = withContext(NonCancellable + Dispatchers.IO) { processBuilder.start() }
            process = started
            if (!claimBuildProcess(generation, started)) {
                throw CancellationException("Build ownership changed before process registration")
            }
            currentCoroutineContext().ensureActive()
            started.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    currentCoroutineContext().ensureActive()
                    onLine(line)
                }
            }
            currentCoroutineContext().ensureActive()
            return runInterruptible(Dispatchers.IO) { started.waitFor() }
        } catch (cancelled: CancellationException) {
            process?.let { terminateProcessTree(it) }
            throw cancelled
        } finally {
            process?.let {
                if (it.isAlive) terminateProcessTree(it)
                releaseBuildProcess(generation, it)
            }
        }
    }

    private fun claimBuildProcess(generation: Long, process: Process): Boolean = synchronized(buildStateLock) {
        if (activeBuildGeneration != generation || activeBuildJob?.isActive != true) {
            false
        } else {
            buildProcess = process
            true
        }
    }

    private fun releaseBuildProcess(generation: Long, process: Process) {
        synchronized(buildStateLock) {
            if (activeBuildGeneration == generation && buildProcess === process) {
                buildProcess = null
            }
        }
    }

    private fun releaseBuildOwnership(generation: Long) {
        synchronized(buildStateLock) {
            if (activeBuildGeneration != generation) {
                return@synchronized
            } else {
                activeBuildGeneration = 0L
                activeBuildKind = null
                activeBuildJob = null
                buildProcess = null
                _isBuildRunning.value = false
            }
        }
    }

    private fun updateGenerationStateIfOwner(generation: Long, state: AresGenerationState) {
        synchronized(buildStateLock) {
            if (activeBuildGeneration == generation && activeBuildKind == BuildOperationKind.GENERATION) {
                _aresGenerationState.value = state
            }
        }
    }

    fun runSimulation(projectPath: String, league: League, simulatorCommand: String? = null) {
        if (shuttingDown.get()) return
        killActiveSim()

        val replacement = serviceScope.launch(start = CoroutineStart.LAZY) {
            var ownedProcess: Process? = null
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
                    isWindows && league == League.FTC -> withAresRepository(listOf("cmd.exe", "/c", "gradlew.bat", ":TeamCode:runSim"))
                    isWindows -> withAresRepository(listOf("cmd.exe", "/c", "gradlew.bat", "simulateJava"))
                    league == League.FTC -> withAresRepository(listOf("./gradlew", ":TeamCode:runSim"))
                    else -> withAresRepository(listOf("./gradlew", "simulateJava"))
                }

                _buildOutput.emit("[SYSTEM] Starting Simulation: ${cmd.joinToString(" ")}")
                val pb = withAresRepositoryEnvironment(ProcessBuilder(cmd)
                    .directory(File(projectPath))
                    .redirectErrorStream(true))
                val proc = pb.start()
                ownedProcess = proc
                simProcess = proc
                currentCoroutineContext().ensureActive()

                proc.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        currentCoroutineContext().ensureActive()
                        _buildOutput.emit(line)
                    }
                }
                currentCoroutineContext().ensureActive()
                val exitCode = runInterruptible(Dispatchers.IO) { proc.waitFor() }
                _buildOutput.emit("[SYSTEM] Simulation finished with exit code $exitCode")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _buildOutput.emit("[SYSTEM] Error running simulation: ${e.message}")
            } finally {
                ownedProcess?.let { if (it.isAlive) terminateProcessTree(it) }
                if (simProcess === ownedProcess) simProcess = null
                _isSimRunning.value = false
            }
        }
        activeSimJob = replacement
        replacement.start()
    }

    fun startLogcat() {
        if (shuttingDown.get()) return
        killActiveLogcat()

        val replacement = serviceScope.launch(start = CoroutineStart.LAZY) {
            var ownedProcess: Process? = null
            try {
                _logcatOutput.emit("[SYSTEM] Starting ADB logcat stream...")
                val adb = resolveAdbPath()
                val pb = ProcessBuilder(adb, "logcat", "-v", "time")
                    .redirectErrorStream(true)
                val proc = pb.start()
                ownedProcess = proc
                logcatProcess = proc
                currentCoroutineContext().ensureActive()

                proc.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        currentCoroutineContext().ensureActive()
                        _logcatOutput.emit(line)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _logcatOutput.emit("[SYSTEM] Error streaming logcat: ${e.message}")
            } finally {
                ownedProcess?.let { if (it.isAlive) terminateProcessTree(it) }
                if (logcatProcess === ownedProcess) logcatProcess = null
            }
        }
        activeLogcatJob = replacement
        replacement.start()
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
        val connectExit = waitForProcess(connectProc, 10) { line -> _buildOutput.emit("[ADB] $line") }
        if (connectExit == null) {
            _buildOutput.emit("[SYSTEM] Warning: adb connect timed out. Attempting install anyway.")
        }
        if (connectExit != null && connectExit != 0) {
            _buildOutput.emit("[SYSTEM] Warning: adb connect returned non-zero exit code $connectExit. Attempting install anyway.")
        }

        // Try installing debug apk
        var apkPath = File(projectPath, "ftc-app/TeamCode/build/outputs/apk/debug/TeamCode-debug.apk")
        if (!apkPath.exists()) {
            apkPath = File(projectPath, "TeamCode/build/outputs/apk/debug/TeamCode-debug.apk")
        }
        val installPb = ProcessBuilder(adb, "install", "-r", apkPath.absolutePath).redirectErrorStream(true)
        val installProc = installPb.start()
        val installResult = waitForProcess(installProc, 30) { line -> _buildOutput.emit("[ADB] $line") }
        if (installResult == null) {
            _buildOutput.emit("[SYSTEM] Error: ADB Deploy timed out.")
        }
        val installExit = installResult ?: -1
        _buildOutput.emit("[SYSTEM] ADB Deploy finished with exit code $installExit")

        // Auto restart logcat on successful deploy
        if (installExit == 0) {
            startLogcat()
        }
    }

    fun killActiveBuild() {
        runBlocking { killActiveBuildAndJoin() }
    }

    internal suspend fun killActiveBuildAndJoin() {
        val requestId = buildRequestId.incrementAndGet()
        buildLifecycleMutex.withLock {
            if (requestId == buildRequestId.get()) stopActiveBuildLocked()
        }
    }

    private suspend fun stopActiveBuildLocked() = withContext(NonCancellable) {
        val ownership = synchronized(buildStateLock) {
            BuildOwnership(activeBuildGeneration, activeBuildKind, activeBuildJob, buildProcess)
        }
        ownership.job?.cancel()
        ownership.process?.let { terminateProcessTree(it) }
        ownership.job?.cancelAndJoin()

        val released = synchronized(buildStateLock) {
            if (activeBuildGeneration != ownership.generation || ownership.generation == 0L) {
                false
            } else {
                activeBuildGeneration = 0L
                activeBuildKind = null
                activeBuildJob = null
                buildProcess = null
                true
            }
        }
        if (released || ownership.job != null) _isBuildRunning.value = false
        if (ownership.kind == BuildOperationKind.GENERATION &&
            _aresGenerationState.value.phase == AresGenerationPhase.RUNNING
        ) {
            _aresGenerationState.value = AresGenerationState(AresGenerationPhase.FAILED, "Generation canceled.")
        }
    }

    fun killActiveLogcat() {
        runBlocking { stopLogcatAndJoin() }
    }

    fun killActiveSim() {
        runBlocking { stopSimulationAndJoin() }
    }

    fun shutdown() {
        runBlocking { shutdownAndJoin() }
    }

    internal suspend fun shutdownAndJoin() = withContext(NonCancellable) {
        shuttingDown.set(true)
        buildRequestId.incrementAndGet()
        buildLifecycleMutex.withLock { stopActiveBuildLocked() }
        stopLogcatAndJoin()
        stopSimulationAndJoin()
        adbMonitorJob?.cancelAndJoin()
        adbMonitorJob = null
        serviceScope.coroutineContext[Job]?.cancelAndJoin()
    }

    private suspend fun stopLogcatAndJoin() = withContext(NonCancellable) {
        val process = logcatProcess
        val job = activeLogcatJob
        job?.cancel()
        process?.let { terminateProcessTree(it) }
        job?.cancelAndJoin()
        if (logcatProcess === process) logcatProcess = null
        if (activeLogcatJob === job) activeLogcatJob = null
    }

    private suspend fun stopSimulationAndJoin() = withContext(NonCancellable) {
        val process = simProcess
        val job = activeSimJob
        job?.cancel()
        process?.let { terminateProcessTree(it) }
        job?.cancelAndJoin()
        if (simProcess === process) simProcess = null
        if (activeSimJob === job) activeSimJob = null
        _isSimRunning.value = false
    }

    /** Drains output concurrently so a verbose child cannot fill its pipe before the timeout. */
    private suspend fun waitForProcess(
        process: Process,
        timeoutSeconds: Long,
        onLine: suspend (String) -> Unit
    ): Int? = coroutineScope {
        runCatching { process.outputStream.close() }
        val drain = async(Dispatchers.IO) {
            runCatching {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) onLine(reader.readLine() ?: break)
                }
            }
        }
        val finished = try {
            withTimeoutOrNull(timeoutSeconds * 1_000L) {
                while (process.isAlive) delay(25)
                true
            } ?: false
        } catch (cancelled: CancellationException) {
            terminateProcessTree(process)
            throw cancelled
        }
        if (!finished) {
            terminateProcessTree(process)
            withTimeoutOrNull(2_000) {
                while (process.isAlive) delay(25)
            }
        }
        withTimeoutOrNull(2_000) { drain.await() } ?: drain.cancel()
        if (finished) process.exitValue() else null
    }

    private suspend fun terminateProcessTree(process: Process) {
        withContext(NonCancellable + Dispatchers.IO) {
            val handles = mutableListOf<ProcessHandle>()
            runCatching {
                process.descendants().use { descendants ->
                    descendants.forEach { handles.add(it) }
                }
            }
            handles.asReversed().forEach { child -> runCatching { child.destroyForcibly() } }
            runCatching { process.destroyForcibly() }

            val allHandles = handles + process.toHandle()
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROCESS_TREE_KILL_GRACE_MS)
            while (allHandles.any { it.isAlive } && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(PROCESS_TREE_POLL_MS)
                } catch (_: InterruptedException) {
                    // Coroutine cancellation cannot abandon process cleanup once it has begun.
                    Thread.interrupted()
                }
            }
            allHandles.filter { it.isAlive }.forEach { handle ->
                runCatching { handle.destroyForcibly() }
            }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }

    private fun requireSafeProjectRoot(projectPath: String): File {
        require(projectPath.isNotBlank()) { "Choose a robot project directory first" }
        val root = File(projectPath).canonicalFile
        require(root.isDirectory) { "The selected project directory does not exist" }
        return root
    }

    private fun withAresRepository(command: List<String>): List<String> =
        aresRepositoryArgument?.let { argument -> command + argument } ?: command

    private fun withAresRepositoryEnvironment(processBuilder: ProcessBuilder): ProcessBuilder =
        processBuilder.also { builder ->
            aresRepositoryFileUri?.let { uri ->
                builder.environment()[ARES_REPOSITORY_GRADLE_ENVIRONMENT] = uri
            }
        }

    /** Focused test seam for the shared command decoration used by build, generation, and sim. */
    internal fun configuredGradleCommandForTest(command: List<String>): List<String> =
        withAresRepository(command)

    /** Focused test seam for environment propagation into arbitrary child simulator commands. */
    internal fun configuredAresRepositoryEnvironmentForTest(): String? =
        withAresRepositoryEnvironment(ProcessBuilder("ares-environment-test"))
            .environment()[ARES_REPOSITORY_GRADLE_ENVIRONMENT]

    private fun validatedAresRepositoryUri(rawUri: String): String {
        val uri = runCatching { URI.create(rawUri) }.getOrElse {
            throw IllegalArgumentException("ARES repository override must be a valid file URI", it)
        }
        require(uri.scheme.equals("file", ignoreCase = true)) {
            "ARES repository override must use a file URI; remote and implicit local repositories are not forwarded"
        }
        val directory = runCatching { Paths.get(uri).toFile().canonicalFile }.getOrElse {
            throw IllegalArgumentException("ARES repository override must identify a local directory", it)
        }
        require(directory.isDirectory) { "ARES repository override directory does not exist: $directory" }
        return directory.toURI().toASCIIString()
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
        const val ARES_REPOSITORY_URI_PROPERTY = "ares.repository.uri"
        const val ARES_REPOSITORY_GRADLE_ENVIRONMENT = "ORG_GRADLE_PROJECT_aresRepository"
        const val GENERATION_DIAGNOSTIC_LINE_LIMIT = 24
        const val GENERATION_DIAGNOSTIC_CHARACTER_LIMIT = 4_000
        const val MAX_MONITOR_OUTPUT_CHARS = 64 * 1024
        const val PROCESS_TREE_KILL_GRACE_MS = 2_000L
        const val PROCESS_TREE_POLL_MS = 10L
        val GENERATED_CONTENT_HASH = Regex("CONTENT_SHA256:\\s*String\\s*=\\s*\"([0-9a-fA-F]{64})\"")
    }
}

