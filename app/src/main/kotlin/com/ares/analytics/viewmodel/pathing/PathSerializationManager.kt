package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.shared.*
import com.ares.analytics.service.TrajectoryEstimator
import com.ares.analytics.ui.components.pathplanner.Waypoint
import com.ares.analytics.ui.components.pathplanner.resolveHeading
import com.ares.analytics.viewmodel.PathPreview
import com.ares.analytics.viewmodel.PathPlannerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Handles serialization, parsing, path file loading, auto file loading, and disk persistence.
 */
class PathSerializationManager(
    private val scope: CoroutineScope,
    private val stateFlow: MutableStateFlow<PathPlannerState>,
    private val onTrajectoryChanged: () -> Unit
) {

    fun fetchAvailablePaths(projectPath: String?, league: League) {
        if (projectPath.isNullOrBlank()) return
        scope.launch(Dispatchers.IO) {
            val pathsDir = File(projectPath, if (league == League.FTC) {
                if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/paths"
                else "src/main/assets/pathplanner/paths"
            } else "src/main/deploy/pathplanner/paths")

            val autosDir = File(projectPath, if (league == League.FTC) {
                if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/autos"
                else "src/main/assets/pathplanner/autos"
            } else "src/main/deploy/pathplanner/autos")

            val pathFiles = pathsDir.listFiles { _, name -> name.endsWith(".path") }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
            val autoFiles = autosDir.listFiles { _, name -> name.endsWith(".auto") }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()

            val pathPreviews = pathFiles.map { pName ->
                PathPreview(pName, loadPathTrajectory(pName, projectPath, league))
            }
            val autoPreviews = autoFiles.map { aName ->
                PathPreview(aName, loadAutoTrajectory(aName, projectPath, league))
            }

            stateFlow.update {
                it.copy(
                    availablePaths = pathFiles,
                    availableAutos = autoFiles,
                    availablePathPreviews = pathPreviews,
                    availableAutoPreviews = autoPreviews
                )
            }
        }
    }

    fun loadPath(projectPath: String?, league: League, pathNameOverride: String? = null) {
        if (projectPath.isNullOrBlank()) return
        scope.launch(Dispatchers.IO) {
            val s = stateFlow.value
            val targetPathName = pathNameOverride ?: s.pathName
            val relativeDir = if (league == League.FTC) {
                if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/paths"
                else "src/main/assets/pathplanner/paths"
            } else "src/main/deploy/pathplanner/paths"

            val file = File(File(projectPath, relativeDir), "$targetPathName.path")
            if (!file.exists()) {
                stateFlow.update { it.copy(saveStatus = "Path file $targetPathName.path does not exist") }
                return@launch
            }
            try {
                val pathFile = AppJson.decodeFromString<PathPlannerFile>(file.readText())
                val loadedWps = pathFile.waypoints.map { pwp ->
                    val next = pwp.nextControl
                    val prev = pwp.prevControl
                    val heading = when {
                        next != null -> atan2(next.y - pwp.anchor.y, next.x - pwp.anchor.x)
                        prev != null -> atan2(pwp.anchor.y - prev.y, pwp.anchor.x - prev.x)
                        else -> 0.0
                    }
                    val prevLength = if (prev != null) hypot(pwp.anchor.x - prev.x, pwp.anchor.y - prev.y) else 0.5
                    val nextLength = if (next != null) hypot(next.x - pwp.anchor.x, next.y - pwp.anchor.y) else 0.5
                    Waypoint(pwp.anchor.x, pwp.anchor.y, heading, prevControlLength = prevLength, nextControlLength = nextLength)
                }
                val trajectory = TrajectoryEstimator.generateTrajectory(
                    waypoints = loadedWps,
                    globalConstraints = pathFile.globalConstraints,
                    constraintZones = pathFile.constraintZones,
                    rotationTargets = pathFile.rotationTargets,
                    idealStartingState = pathFile.idealStartingState,
                    goalEndState = pathFile.goalEndState
                )
                stateFlow.update {
                    it.copy(
                        pathName = targetPathName,
                        waypoints = loadedWps,
                        globalConstraints = pathFile.globalConstraints,
                        constraintZones = pathFile.constraintZones,
                        rotationTargets = pathFile.rotationTargets,
                        eventMarkers = pathFile.eventMarkers,
                        pointTowardsZones = pathFile.pointTowardsZones,
                        idealStartingState = pathFile.idealStartingState,
                        goalEndState = pathFile.goalEndState,
                        reversed = pathFile.reversed,
                        useDefaultConstraints = pathFile.useDefaultConstraints,
                        trajectory = trajectory,
                        estimatedDuration = trajectory.durationSeconds,
                        saveStatus = "Loaded path: $targetPathName"
                    )
                }
            } catch (e: Exception) {
                stateFlow.update { it.copy(saveStatus = "Failed to load path: ${e.message}") }
            }
        }
    }

    fun loadAuto(projectPath: String?, league: League, autoNameOverride: String? = null) {
        if (projectPath.isNullOrBlank()) return
        scope.launch(Dispatchers.IO) {
            val s = stateFlow.value
            val targetAutoName = autoNameOverride ?: s.pathName
            val relativeDir = if (league == League.FTC) {
                if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/autos"
                else "src/main/assets/pathplanner/autos"
            } else "src/main/deploy/pathplanner/autos"

            val file = File(File(projectPath, relativeDir), "$targetAutoName.auto")
            if (!file.exists()) {
                stateFlow.update { it.copy(saveStatus = "Auto file $targetAutoName.auto does not exist") }
                return@launch
            }
            try {
                val autoFile = AppJson.decodeFromString<AutoFile>(file.readText())
                val autoTrajectory = loadAutoTrajectory(targetAutoName, projectPath, league)
                stateFlow.update {
                    it.copy(
                        pathName = targetAutoName,
                        autoStartingPose = autoFile.startingPose,
                        currentAutoCommands = listOf(autoFile.command),
                        trajectory = autoTrajectory,
                        estimatedDuration = autoTrajectory?.durationSeconds ?: 0.0,
                        saveStatus = "Loaded auto: $targetAutoName"
                    )
                }
            } catch (e: Exception) {
                stateFlow.update { it.copy(saveStatus = "Failed to load auto: ${e.message}") }
            }
        }
    }

    fun loadPathTrajectory(pathName: String, projectPath: String, league: League): Trajectory? {
        try {
            val relativeDir = if (league == League.FTC) {
                if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/paths"
                else "src/main/assets/pathplanner/paths"
            } else {
                "src/main/deploy/pathplanner/paths"
            }
            val targetDir = File(projectPath, relativeDir)
            val file = File(targetDir, "$pathName.path")
            if (!file.exists()) return null
            val pathFile = AppJson.decodeFromString<PathPlannerFile>(file.readText())
            val loadedWps = pathFile.waypoints.map { pwp ->
                val next = pwp.nextControl
                val prev = pwp.prevControl
                val heading = when {
                    next != null -> atan2(next.y - pwp.anchor.y, next.x - pwp.anchor.x)
                    prev != null -> atan2(pwp.anchor.y - prev.y, pwp.anchor.x - prev.x)
                    else -> 0.0
                }
                val prevLength = if (prev != null) hypot(pwp.anchor.x - prev.x, pwp.anchor.y - prev.y) else 0.5
                val nextLength = if (next != null) hypot(next.x - pwp.anchor.x, next.y - pwp.anchor.y) else 0.5
                Waypoint(pwp.anchor.x, pwp.anchor.y, heading, prevControlLength = prevLength, nextControlLength = nextLength)
            }
            return TrajectoryEstimator.generateTrajectory(
                waypoints = loadedWps,
                globalConstraints = pathFile.globalConstraints,
                constraintZones = pathFile.constraintZones,
                rotationTargets = pathFile.rotationTargets,
                idealStartingState = pathFile.idealStartingState,
                goalEndState = pathFile.goalEndState
            )
        } catch (e: Exception) {
            return null
        }
    }

    fun loadPathWaypoints(pathName: String, projectPath: String, league: League): List<Waypoint>? {
        try {
            val relativeDir = if (league == League.FTC) {
                if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/paths"
                else "src/main/assets/pathplanner/paths"
            } else {
                "src/main/deploy/pathplanner/paths"
            }
            val targetDir = File(projectPath, relativeDir)
            val file = File(targetDir, "$pathName.path")
            if (!file.exists()) return null
            val pathFile = AppJson.decodeFromString<PathPlannerFile>(file.readText())
            return pathFile.waypoints.map { pwp ->
                val next = pwp.nextControl
                val prev = pwp.prevControl
                val heading = when {
                    next != null -> atan2(next.y - pwp.anchor.y, next.x - pwp.anchor.x)
                    prev != null -> atan2(pwp.anchor.y - prev.y, pwp.anchor.x - prev.x)
                    else -> 0.0
                }
                val prevLength = if (prev != null) hypot(pwp.anchor.x - prev.x, pwp.anchor.y - prev.y) else 0.5
                val nextLength = if (next != null) hypot(next.x - pwp.anchor.x, next.y - pwp.anchor.y) else 0.5
                Waypoint(pwp.anchor.x, pwp.anchor.y, heading, prevControlLength = prevLength, nextControlLength = nextLength)
            }
        } catch (e: Exception) {
            return null
        }
    }

    fun loadAutoTrajectory(autoName: String, projectPath: String, league: League): Trajectory? {
        try {
            val relativeDir = if (league == League.FTC) {
                if (File(projectPath, "TeamCode/src/main/assets").exists()) "TeamCode/src/main/assets/pathplanner/autos"
                else "src/main/assets/pathplanner/autos"
            } else {
                "src/main/deploy/pathplanner/autos"
            }
            val targetDir = File(projectPath, relativeDir)
            val file = File(targetDir, "$autoName.auto")
            if (!file.exists()) return null
            val autoFile = AppJson.decodeFromString<AutoFile>(file.readText())
            val pathNames = mutableListOf<String>()

            fun extractPaths(node: AutoCommandNode) {
                if (node.type == "path") {
                    val pName = node.data["pathName"]?.let { 
                        if (it is JsonPrimitive && it.isString) it.content else null 
                    }
                    if (!pName.isNullOrEmpty()) {
                        pathNames.add(pName)
                    }
                }
                val commandsArray = node.data["commands"] as? JsonArray
                commandsArray?.forEach { jsonElement ->
                    try {
                        val childNode = AppJson.decodeFromJsonElement(AutoCommandNode.serializer(), jsonElement)
                        extractPaths(childNode)
                    } catch (e: Exception) {}
                }
            }
            extractPaths(autoFile.command)

            var totalTime = 0.0
            val combinedStates = mutableListOf<TrajectoryState>()
            for (pName in pathNames) {
                val traj = loadPathTrajectory(pName, projectPath, league)
                if (traj != null && traj.states.isNotEmpty()) {
                    for (state in traj.states) {
                        combinedStates.add(state.copy(timeSeconds = state.timeSeconds + totalTime))
                    }
                    totalTime += traj.durationSeconds
                }
            }
            
            return if (combinedStates.isNotEmpty()) Trajectory(totalTime, combinedStates) else null
        } catch (e: Exception) {
            return null
        }
    }

    fun recalculateAutoTrajectory(projectPath: String?, league: League) {
        if (projectPath == null) return
        scope.launch(Dispatchers.IO) {
            val s = stateFlow.value
            val root = s.currentAutoCommands.firstOrNull() ?: return@launch
            val pathNames = mutableListOf<String>()

            fun extractPaths(node: AutoCommandNode) {
                if (node.type == "path") {
                    val pathName = node.data["pathName"]?.let { 
                        if (it is JsonPrimitive && it.isString) it.content else null 
                    }
                    if (!pathName.isNullOrEmpty()) {
                        pathNames.add(pathName)
                    }
                }
                val commandsArray = node.data["commands"] as? JsonArray
                commandsArray?.forEach { jsonElement ->
                    try {
                        val childNode = AppJson.decodeFromJsonElement(AutoCommandNode.serializer(), jsonElement)
                        extractPaths(childNode)
                    } catch (e: Exception) {}
                }
            }
            extractPaths(root)
            var totalTime = 0.0
            val combinedStates = mutableListOf<TrajectoryState>()
            for (pathName in pathNames) {
                val traj = loadPathTrajectory(pathName, projectPath, league)
                if (traj != null && traj.states.isNotEmpty()) {
                    for (state in traj.states) {
                        combinedStates.add(state.copy(timeSeconds = state.timeSeconds + totalTime))
                    }
                    totalTime += traj.durationSeconds
                }
            }
            val autoTrajectory = if (combinedStates.isNotEmpty()) Trajectory(totalTime, combinedStates) else null
            stateFlow.update { it.copy(trajectory = autoTrajectory, estimatedDuration = totalTime) }
        }
    }

    suspend fun savePath(projectPath: String, league: League, updateContextAuto: (String?, String?, League) -> Unit) = withContext(Dispatchers.IO) {
        val s = stateFlow.value
        try {
            val pWaypoints = s.waypoints.mapIndexed { idx, wp ->
                val theta = resolveHeading(s.waypoints, idx)
                val anchor = PathPoint(wp.x, wp.y)
                val nextControl = if (idx == s.waypoints.size - 1) null else PathPoint(
                    wp.x + cos(theta) * wp.nextControlLength,
                    wp.y + sin(theta) * wp.nextControlLength
                )
                val prevControl = if (idx == 0) null else PathPoint(
                    wp.x - cos(theta) * wp.prevControlLength,
                    wp.y - sin(theta) * wp.prevControlLength
                )
                PathPlannerWaypoint(
                    anchor = anchor,
                    prevControl = prevControl,
                    nextControl = nextControl
                )
            }
            val firstRot = s.waypoints.firstOrNull()?.rotationDeg
            val lastRot = s.waypoints.lastOrNull()?.rotationDeg
            val extractedStartingState = IdealStartingState(
                velocity = s.idealStartingState?.velocity ?: 0.0,
                rotation = firstRot ?: 0.0
            )
            val extractedGoalEndState = GoalEndState(
                velocity = s.goalEndState?.velocity ?: 0.0,
                rotation = lastRot ?: 0.0
            )
            val waypointRotationTargets = s.waypoints
                .mapIndexedNotNull { idx, wp ->
                    val rot = wp.rotationDeg ?: return@mapIndexedNotNull null
                    if (idx == 0 || idx == s.waypoints.size - 1) null
                    else RotationTarget(idx.toDouble(), rot)
                }
            val midSegmentTargets = s.rotationTargets.filter { rt ->
                val pos = rt.waypointRelativePos
                kotlin.math.abs(pos - kotlin.math.round(pos)) > 1e-3
            }
            val extractedRotationTargets = waypointRotationTargets + midSegmentTargets
            val pathFile = PathPlannerFile(
                version = "2025.0",
                waypoints = pWaypoints,
                rotationTargets = extractedRotationTargets,
                constraintZones = s.constraintZones,
                pointTowardsZones = s.pointTowardsZones,
                eventMarkers = s.eventMarkers,
                globalConstraints = s.globalConstraints,
                goalEndState = extractedGoalEndState,
                idealStartingState = extractedStartingState,
                reversed = s.reversed,
                useDefaultConstraints = s.useDefaultConstraints
            )
            val json = Json { 
                prettyPrint = true
                encodeDefaults = false
            }
            val serialized = json.encodeToString(pathFile)
            val relativeDir = if (league == League.FTC) {
                "TeamCode/src/main/assets/pathplanner/paths"
            } else {
                "src/main/deploy/pathplanner/paths"
            }
            val targetDir = File(projectPath, relativeDir)
            targetDir.mkdirs()
            val targetFile = File(targetDir, "${s.pathName}.path")
            targetFile.writeText(serialized)

            if (league == League.FTC) {
                val pushed = pushFileToRobot(targetFile, "/sdcard/FIRST/paths", "${s.pathName}.path")
                if (pushed) {
                    stateFlow.update { it.copy(saveStatus = "Saved locally & pushed to robot!") }
                } else {
                    stateFlow.update { it.copy(saveStatus = "Saved locally (robot push failed/no connection)") }
                }
            } else {
                stateFlow.update { it.copy(saveStatus = "Saved to ${targetFile.name}!") }
            }
            
            if (s.contextAutoName != null) {
                updateContextAuto(s.contextAutoName, projectPath, league)
            }
        } catch (e: Exception) {
            stateFlow.update { it.copy(saveStatus = "Save failed: ${e.message}") }
        }
    }

    suspend fun saveAuto(projectPath: String, league: League) = withContext(Dispatchers.IO) {
        val s = stateFlow.value
        try {
            val relativeDir = if (league == League.FTC) {
                "TeamCode/src/main/assets/pathplanner/autos"
            } else {
                "src/main/deploy/pathplanner/autos"
            }
            val targetDir = File(projectPath, relativeDir)
            targetDir.mkdirs()
            val rootCmd = s.currentAutoCommands.firstOrNull() ?: AutoCommandNode("sequential")
            val autoFile = AutoFile(
                version = "1.0",
                startingPose = s.autoStartingPose,
                command = rootCmd
            )
            val json = Json { prettyPrint = true; encodeDefaults = false }
            val serialized = json.encodeToString(autoFile)
            val targetFile = File(targetDir, "${s.pathName}.auto")
            targetFile.writeText(serialized)

            if (league == League.FTC) {
                pushFileToRobot(targetFile, "/sdcard/FIRST/autos", "${s.pathName}.auto")
            }
            stateFlow.update { it.copy(saveStatus = "Saved auto to ${targetFile.name}!") }
        } catch (e: Exception) {
            stateFlow.update { it.copy(saveStatus = "Auto save failed: ${e.message}") }
        }
    }

    private fun findAdbPath(): String {
        try {
            val proc = ProcessBuilder("adb", "--version").start()
            proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            return "adb"
        } catch (e: Exception) {
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

    fun pushFileToRobot(localFile: File, remoteDir: String, remoteFileName: String): Boolean {
        try {
            val adb = findAdbPath()
            ProcessBuilder(adb, "connect", "192.168.43.1:5555").start().waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            ProcessBuilder(adb, "shell", "mkdir", "-p", remoteDir).start().waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            val proc = ProcessBuilder(adb, "push", localFile.absolutePath, "$remoteDir/$remoteFileName").start()
            val finished = proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            return finished && proc.exitValue() == 0
        } catch (e: Exception) {
            System.err.println("Failed to push file via ADB: ${e.message}")
            return false
        }
    }
}
