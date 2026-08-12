package com.ares.analytics.ui.help

import com.ares.analytics.ui.components.NavigationTarget

enum class LearningLevel(val label: String, val explanation: String) {
    STARTER("Start here", "First wins with plain-language guidance"),
    BUILDER("Build skills", "Create and inspect robot behavior"),
    ADVANCED("Go deeper", "Understand tuning, architecture, and diagnostics"),
}

enum class LearningTrack(val label: String) {
    GETTING_STARTED("Getting started"),
    OPERATE("Operate & review"),
    BUILD("Build robot behavior"),
    UNDERSTAND("Understand the system"),
}

enum class LearningAction { OPEN_SCREEN, START_SIMULATOR }

data class LearningLesson(
    val id: String,
    val level: LearningLevel,
    val track: LearningTrack,
    val title: String,
    val outcome: String,
    val durationMinutes: Int,
    val destination: NavigationTarget,
    val action: LearningAction = LearningAction.OPEN_SCREEN,
    val requiresRobot: Boolean = false,
    val beforeYouStart: List<String>,
    val steps: List<String>,
    val successLooksLike: String,
    val safetyNote: String? = null,
    val keywords: Set<String> = emptySet(),
)

object LearningCatalog {
    val lessons: List<LearningLesson> = listOf(
        LearningLesson(
            id = "start-simulator",
            level = LearningLevel.STARTER,
            track = LearningTrack.GETTING_STARTED,
            title = "Explore ARES without a robot",
            outcome = "Start the desktop simulator and recognize a healthy connection.",
            durationMinutes = 5,
            destination = NavigationTarget.DASHBOARD,
            action = LearningAction.START_SIMULATOR,
            beforeYouStart = listOf("Open a configured FTC or FRC project workspace.", "No robot hardware is required."),
            steps = listOf(
                "Select Local Sim in the execution toolbar.",
                "Choose Start simulator and wait for the status to become connected.",
                "Open Dashboard and find pose, motor, and system-health telemetry.",
                "Stop the simulator when you finish so no background process remains.",
            ),
            successLooksLike = "The app reports a local simulator connection and dashboard values change over time.",
            safetyNote = "Simulator commands never authorize a physical robot. Confirm the target says Local Sim.",
            keywords = setOf("offline", "practice", "connect", "simulation"),
        ),
        LearningLesson(
            id = "read-connection-state",
            level = LearningLevel.STARTER,
            track = LearningTrack.GETTING_STARTED,
            title = "Understand connection status",
            outcome = "Tell the difference between simulator, robot, replay, and disconnected data.",
            durationMinutes = 4,
            destination = NavigationTarget.DASHBOARD,
            beforeYouStart = listOf("The app may be disconnected; that is a valid state for this lesson."),
            steps = listOf(
                "Read the NT4 status in the left sidebar.",
                "Check whether the execution target is Local Sim or Live Robot.",
                "If data is not live, open Run History to distinguish replayed data from a connection.",
            ),
            successLooksLike = "You can state where the displayed data came from before changing robot behavior.",
            safetyNote = "Never assume a disconnected indicator means a robot is disabled; Driver Station owns enable state.",
            keywords = setOf("nt4", "disconnected", "live", "replay"),
        ),
        LearningLesson(
            id = "bring-in-run",
            level = LearningLevel.STARTER,
            track = LearningTrack.OPERATE,
            title = "Find and review a robot run",
            outcome = "Locate imported evidence and open a run for review.",
            durationMinutes = 8,
            destination = NavigationTarget.IMPORT_CENTER,
            beforeYouStart = listOf("Have a simulator or robot log available, or inspect existing imported runs."),
            steps = listOf(
                "Open Log Imports and read the source and status of each entry.",
                "If an import is quarantined, open its reason before retrying.",
                "Open Run History and select the newest successful run.",
                "Compare alerts and charts with what happened during the run.",
            ),
            successLooksLike = "You can identify the run source, timestamp, robot, and any warnings without guessing.",
            keywords = setOf("log", "import", "quarantine", "history", "replay"),
        ),
        LearningLesson(
            id = "first-routine",
            level = LearningLevel.BUILDER,
            track = LearningTrack.BUILD,
            title = "Build a safe first autonomous routine",
            outcome = "Create a small routine, validate it, and preview it before generation.",
            durationMinutes = 15,
            destination = NavigationTarget.PATH_PLANNER,
            beforeYouStart = listOf("Use the simulator first.", "Confirm robot dimensions and field selection."),
            steps = listOf(
                "Create a routine with one short Drive To step.",
                "Choose a safe motion preset and keep the route clear of obstacles.",
                "Resolve every validation warning before saving.",
                "Preview the route, then generate project code only after the preview is correct.",
            ),
            successLooksLike = "The routine saves, validates, and previews without bounds or capability warnings.",
            safetyNote = "A valid preview is not physical clearance proof. Recheck on a real field before enabling a robot.",
            keywords = setOf("auto", "routine", "path", "drive to"),
        ),
        LearningLesson(
            id = "map-one-control",
            level = LearningLevel.BUILDER,
            track = LearningTrack.BUILD,
            title = "Map one TeleOp control",
            outcome = "Connect a visible gamepad input to one named robot action.",
            durationMinutes = 10,
            destination = NavigationTarget.CONTROLS,
            beforeYouStart = listOf("Know whether the control belongs to Driver or Operator."),
            steps = listOf(
                "Choose Driver or Operator.",
                "Select the physical button or axis on the controller diagram.",
                "Choose a named action and a simple timing mode.",
                "Review conflicts, save, and inspect the generated summary.",
            ),
            successLooksLike = "The binding has no conflicts and its generated destination is clearly identified.",
            safetyNote = "Test mechanism controls with the robot disabled and lifted or mechanically secured as appropriate.",
            keywords = setOf("teleop", "gamepad", "button", "binding"),
        ),
        LearningLesson(
            id = "safe-subsystem",
            level = LearningLevel.BUILDER,
            track = LearningTrack.BUILD,
            title = "Design a safe subsystem",
            outcome = "Use a capability template and understand every generated responsibility.",
            durationMinutes = 20,
            destination = NavigationTarget.SUBSYSTEM_GEN,
            beforeYouStart = listOf("Choose the mechanism’s real control mode and sensors.", "Know its safe neutral output."),
            steps = listOf(
                "Choose the closest capability template instead of minimizing file count.",
                "Configure feedback validity, homing, soft limits, current monitoring, and safe output.",
                "Review user-owned versus generated artifacts and their runtime flow.",
                "Preview the structured diff; replace starters only after reviewing every change.",
            ),
            successLooksLike = "No safety warning remains and you can name the state, controller, IO, mock, and lifecycle roles.",
            keywords = setOf("redux", "io", "generator", "motor", "sensor", "homing"),
        ),
        LearningLesson(
            id = "pit-readiness",
            level = LearningLevel.ADVANCED,
            track = LearningTrack.OPERATE,
            title = "Read a pit readiness report",
            outcome = "Interpret observed telemetry without mistaking it for an actuator test.",
            durationMinutes = 10,
            destination = NavigationTarget.PIT_DIAGNOSTICS,
            requiresRobot = true,
            beforeYouStart = listOf("Connect to the robot network.", "Keep the robot disabled."),
            steps = listOf(
                "Open Pit Self-Test and wait for fresh observations.",
                "Separate OBSERVED, WAITING, and WARNING results.",
                "Resolve missing or stale telemetry at its source.",
                "Record anything that still needs a supervised physical check.",
            ),
            successLooksLike = "You can explain which evidence was observed and which checks still require hardware.",
            safetyNote = "This page is read-only telemetry readiness. It does not prove that motors or mechanisms moved safely.",
            keywords = setOf("pit", "self test", "hardware", "freshness"),
        ),
        LearningLesson(
            id = "tuning-evidence",
            level = LearningLevel.ADVANCED,
            track = LearningTrack.UNDERSTAND,
            title = "Understand tuning evidence",
            outcome = "Recognize setpoint, measurement, error, and safe experiment boundaries.",
            durationMinutes = 15,
            destination = NavigationTarget.TUNING,
            requiresRobot = true,
            beforeYouStart = listOf("Practice in simulation.", "Have a mentor approve any physical SysId motion."),
            steps = listOf(
                "Identify the mechanism, units, and current configuration source.",
                "Inspect live values without writing project constants.",
                "Review signed SysId samples and the zero reference.",
                "Save changes only after checking limits and rollback options.",
            ),
            successLooksLike = "You can explain the evidence for a change and how to restore the previous configuration.",
            safetyNote = "SysId intentionally moves mechanisms. Use physical testing only with supervision, clear space, and an emergency stop plan.",
            keywords = setOf("sysid", "pid", "feedforward", "closed loop"),
        ),
        LearningLesson(
            id = "developer-reference",
            level = LearningLevel.ADVANCED,
            track = LearningTrack.UNDERSTAND,
            title = "Navigate ARES source concepts",
            outcome = "Find the current source of truth for a core ARES concept without relying on stale examples.",
            durationMinutes = 8,
            destination = NavigationTarget.KDOC_VIEWER,
            beforeYouStart = listOf("Enable Developer Mode in Profile.", "Open the ARES workspace source on this laptop."),
            steps = listOf(
                "Open Developer Reference and search for the concept, responsibility, or source filename.",
                "Read the units and invariant before copying an API name.",
                "Open the listed source path and verify its current declaration and KDoc.",
                "Run the closest focused test before using the concept in robot code.",
            ),
            successLooksLike = "You can identify the owning module, source path, units, and safety invariant for the concept.",
            safetyNote = "The reference is a curated map, not generated API documentation. Current source and tests remain authoritative.",
            keywords = setOf("developer", "source", "kdoc", "api", "architecture", "units"),
        ),
    )

    private val contextualLessonIds = mapOf(
        NavigationTarget.DASHBOARD to "read-connection-state",
        NavigationTarget.IMPORT_CENTER to "bring-in-run",
        NavigationTarget.RUN_HISTORY to "bring-in-run",
        NavigationTarget.PATH_PLANNER to "first-routine",
        NavigationTarget.CONTROLS to "map-one-control",
        NavigationTarget.SUBSYSTEM_GEN to "safe-subsystem",
        NavigationTarget.PIT_DIAGNOSTICS to "pit-readiness",
        NavigationTarget.TUNING to "tuning-evidence",
        NavigationTarget.KDOC_VIEWER to "developer-reference",
    )

    fun lessonFor(target: NavigationTarget): LearningLesson? =
        contextualLessonIds[target]?.let { id -> lessons.firstOrNull { it.id == id } }

    fun search(query: String, level: LearningLevel? = null): List<LearningLesson> {
        val normalized = query.trim().lowercase()
        return lessons.filter { lesson ->
            (level == null || lesson.level == level) &&
                (normalized.isBlank() || listOf(
                    lesson.title,
                    lesson.outcome,
                    lesson.track.label,
                    lesson.level.label,
                    lesson.keywords.joinToString(" "),
                ).any { normalized in it.lowercase() })
        }
    }
}
