package com.ares.analytics.service

import com.ares.analytics.shared.League
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ProcessManagerService {

    private val _buildOutput = MutableSharedFlow<String>(replay = 200)
    val buildOutput: SharedFlow<String> = _buildOutput.asSharedFlow()

    private val _logcatOutput = MutableSharedFlow<String>(replay = 200)
    val logcatOutput: SharedFlow<String> = _logcatOutput.asSharedFlow()

    private val _isSimRunning = MutableStateFlow(false)
    val isSimRunning: StateFlow<Boolean> = _isSimRunning.asStateFlow()

    private val _isBuildRunning = MutableStateFlow(false)
    val isBuildRunning: StateFlow<Boolean> = _isBuildRunning.asStateFlow()

    private val _adbConnected = MutableStateFlow(false)
    val adbConnected: StateFlow<Boolean> = _adbConnected.asStateFlow()

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
        adbMonitorJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val pb = ProcessBuilder("adb", "devices")
                    val proc = pb.start()
                    val output = proc.inputStream.bufferedReader().readText()
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

        activeBuildJob = CoroutineScope(Dispatchers.IO).launch {
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

                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        _buildOutput.emit(line!!)
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

    private fun killOrphanedSimulators() {
        // 1. Clean by Port
        val portsToClean = listOf(5810, 1735)
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        for (port in portsToClean) {
            try {
                if (isWindows) {
                    val proc = ProcessBuilder("cmd.exe", "/c", "netstat -ano").start()
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    val pids = mutableSetOf<Long>()
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.contains("LISTENING") && line!!.contains(":$port")) {
                            val parts = line!!.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                            if (parts.size >= 5) {
                                val pidStr = parts[4]
                                pidStr.toLongOrNull()?.let { pids.add(it) }
                            }
                        }
                    }
                    proc.waitFor()
                    for (pid in pids) {
                        if (pid != ProcessHandle.current().pid()) {
                            ProcessHandle.of(pid).ifPresent { handle ->
                                handle.destroyForcibly()
                            }
                        }
                    }
                } else {
                    val proc = ProcessBuilder("sh", "-c", "lsof -t -i :$port").start()
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line!!.trim().toLongOrNull()?.let { pid ->
                            if (pid != ProcessHandle.current().pid()) {
                                ProcessHandle.of(pid).ifPresent { it.destroyForcibly() }
                            }
                        }
                    }
                    proc.waitFor()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 2. Clean by JPS (fallback/redundancy)
        try {
            val jpsProc = ProcessBuilder("jps", "-l").start()
            val reader = BufferedReader(InputStreamReader(jpsProc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    val pidString = parts[0]
                    val mainClass = parts[1]
                    if (mainClass.contains("com.areslib.sim") || mainClass.contains("DesktopSimLauncher")) {
                        val pid = pidString.toLongOrNull()
                        if (pid != null && pid != ProcessHandle.current().pid()) {
                            ProcessHandle.of(pid).ifPresent { handle ->
                                handle.destroyForcibly()
                            }
                        }
                    }
                }
            }
            jpsProc.waitFor()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun runSimulation(projectPath: String, league: League, simulatorCommand: String? = null) {
        killActiveSim()

        activeSimJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                _isSimRunning.value = true
                _buildOutput.emit("[SYSTEM] Terminating any orphaned simulator processes...")
                killOrphanedSimulators()
                
                val isWindows = System.getProperty("os.name").contains("win", ignoreCase = true)
                val userCmd = simulatorCommand?.takeIf { it.isNotBlank() }
                
                val cmd = if (userCmd != null) {
                    if (isWindows) listOf("cmd.exe", "/c") + userCmd.trim().split("\\s+".toRegex())
                    else userCmd.trim().split("\\s+".toRegex())
                } else {
                    if (isWindows) {
                        when (league) {
                            League.FTC -> listOf("cmd.exe", "/c", "gradlew.bat", ":TeamCode:runSim")
                            League.FRC -> listOf("cmd.exe", "/c", "gradlew.bat", "simulateJava")
                        }
                    } else {
                        when (league) {
                            League.FTC -> listOf("./gradlew", ":TeamCode:runSim")
                            League.FRC -> listOf("./gradlew", "simulateJava")
                        }
                    }
                }

                _buildOutput.emit("[SYSTEM] Starting Simulation: ${cmd.joinToString(" ")}")

                val pb = ProcessBuilder(cmd)
                    .directory(File(projectPath))
                    .redirectErrorStream(true)

                val proc = pb.start()
                simProcess = proc

                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        _buildOutput.emit(line!!)
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

        activeLogcatJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                _logcatOutput.emit("[SYSTEM] Starting ADB logcat stream...")
                val adb = resolveAdbPath()
                val pb = ProcessBuilder(adb, "logcat", "-v", "time")
                    .redirectErrorStream(true)

                val proc = pb.start()
                logcatProcess = proc

                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        _logcatOutput.emit(line!!)
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
        val connectPb = ProcessBuilder(adb, "connect", "192.168.43.1:5555")
        val connectProc = connectPb.start()
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
        val installPb = ProcessBuilder(adb, "install", "-r", apkPath.absolutePath)
        val installProc = installPb.start()

        BufferedReader(InputStreamReader(installProc.inputStream)).use { reader ->
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
    }
}

