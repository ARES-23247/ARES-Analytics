package com.ares.analytics.viewmodel.robotstudio

import com.ares.analytics.service.BuildExecutionPhase
import com.ares.analytics.service.BuildExecutionState
import com.ares.analytics.service.RobotProjectReadinessEvidence
import java.io.File

enum class RobotStudioStageId {
    WORKSPACE,
    PLATFORM,
    DRIVEBASE,
    MECHANISMS,
    LOCALIZATION,
    CAPABILITIES,
    CONTROLS,
    AUTONOMOUS,
    TUNING,
    GENERATE_VERIFY,
    SIMULATE,
    ANALYZE,
}

enum class RobotStudioStageStatus(val label: String) {
    READY("Ready"),
    NEEDS_ACTION("Needs action"),
    BLOCKED("Blocked"),
    INVALID("Invalid"),
    OPTIONAL("Optional"),
    CODE_REQUIRED("Code required"),
    RUNNING("Running now"),
}

enum class RobotStudioAction {
    OPEN_PROJECT_IDENTITY,
    OPEN_DRIVEBASE,
    OPEN_SUBSYSTEMS,
    OPEN_CONTROLS,
    OPEN_AUTONOMOUS,
    OPEN_TUNING,
    RUN_BUILD,
    RUN_SIMULATOR,
    OPEN_IMPORTS,
    OPEN_GUIDED_ANALYSIS,
}

data class RobotStudioStage(
    val id: RobotStudioStageId,
    val title: String,
    val outcome: String,
    val status: RobotStudioStageStatus,
    val explanation: String,
    val issues: List<String>,
    val storage: String,
    val consumer: String,
    val action: RobotStudioAction,
    val actionLabel: String,
)

data class RobotStudioRuntimeEvidence(
    val build: BuildExecutionState = BuildExecutionState(),
    val simulatorRunning: Boolean = false,
    val localSimulatorOnline: Boolean = false,
    val nt4Connected: Boolean = false,
)

data class RobotStudioState(
    val loading: Boolean = true,
    val projectName: String = "",
    val projectPath: String = "",
    val stages: List<RobotStudioStage> = emptyList(),
    val error: String? = null,
) {
    val readyCount: Int get() = stages.count { it.status == RobotStudioStageStatus.READY || it.status == RobotStudioStageStatus.RUNNING }
    val blockingCount: Int get() = stages.count {
        it.status == RobotStudioStageStatus.BLOCKED ||
            it.status == RobotStudioStageStatus.INVALID ||
            it.status == RobotStudioStageStatus.CODE_REQUIRED
    }
    val nextStage: RobotStudioStage? get() = stages.firstOrNull {
        it.status == RobotStudioStageStatus.INVALID ||
            it.status == RobotStudioStageStatus.BLOCKED ||
            it.status == RobotStudioStageStatus.NEEDS_ACTION
    } ?: stages.firstOrNull { it.status == RobotStudioStageStatus.CODE_REQUIRED }
}

/** Converts validated project facts into the one novice-facing Studio sequence. */
internal fun evaluateRobotStudioStages(
    evidence: RobotProjectReadinessEvidence,
    runtime: RobotStudioRuntimeEvidence,
): List<RobotStudioStage> {
    val projectBlocked = evidence.projectError != null
    fun stage(
        id: RobotStudioStageId,
        title: String,
        outcome: String,
        status: RobotStudioStageStatus,
        explanation: String,
        issues: List<String> = emptyList(),
        storage: String,
        consumer: String,
        action: RobotStudioAction,
        actionLabel: String,
    ) = RobotStudioStage(id, title, outcome, status, explanation, issues, storage, consumer, action, actionLabel)

    val workspaceStatus = when {
        projectBlocked -> RobotStudioStageStatus.INVALID
        evidence.metadataErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.metadataPresent -> RobotStudioStageStatus.READY
        else -> RobotStudioStageStatus.NEEDS_ACTION
    }
    val platformStatus = when {
        projectBlocked || !evidence.metadataPresent -> RobotStudioStageStatus.BLOCKED
        evidence.metadataLeagueMatches -> RobotStudioStageStatus.READY
        else -> RobotStudioStageStatus.INVALID
    }
    val drivebaseStatus = when {
        projectBlocked || platformStatus != RobotStudioStageStatus.READY -> RobotStudioStageStatus.BLOCKED
        evidence.drivebaseKind == null && evidence.drivebaseErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.drivebaseKind == null -> RobotStudioStageStatus.NEEDS_ACTION
        evidence.drivebaseErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        !evidence.drivebaseNoCodeSupported -> RobotStudioStageStatus.CODE_REQUIRED
        else -> RobotStudioStageStatus.READY
    }
    val mechanismsStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.subsystemErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.subsystemCount == 0 -> RobotStudioStageStatus.OPTIONAL
        else -> RobotStudioStageStatus.READY
    }
    val localizationStatus = when {
        drivebaseStatus == RobotStudioStageStatus.BLOCKED -> RobotStudioStageStatus.BLOCKED
        drivebaseStatus == RobotStudioStageStatus.INVALID -> RobotStudioStageStatus.BLOCKED
        evidence.localizationConfigured -> RobotStudioStageStatus.READY
        else -> RobotStudioStageStatus.NEEDS_ACTION
    }
    val capabilitiesStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.capabilityErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.capabilityActionCount == 0 -> RobotStudioStageStatus.OPTIONAL
        else -> RobotStudioStageStatus.READY
    }
    val controlsStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.controlErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.controlSchemeCount == 0 || evidence.controllerProfileCount == 0 -> RobotStudioStageStatus.NEEDS_ACTION
        else -> RobotStudioStageStatus.READY
    }
    val autonomousStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.autonomousErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.routineCount == 0 || !evidence.autonomousCatalogPresent -> RobotStudioStageStatus.OPTIONAL
        else -> RobotStudioStageStatus.READY
    }
    val tuningStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.tuningError != null -> RobotStudioStageStatus.INVALID
        evidence.tuningDeclarationCount == 0 -> RobotStudioStageStatus.OPTIONAL
        evidence.tuningProfileCount == 0 -> RobotStudioStageStatus.NEEDS_ACTION
        else -> RobotStudioStageStatus.READY
    }
    val requiredStagesReady = listOf(
        workspaceStatus,
        platformStatus,
        drivebaseStatus,
        localizationStatus,
        controlsStatus,
    ).all { it == RobotStudioStageStatus.READY }
    val optionalStagesSafe = listOf(
        mechanismsStatus,
        capabilitiesStatus,
        autonomousStatus,
        tuningStatus,
    ).all { it == RobotStudioStageStatus.READY || it == RobotStudioStageStatus.OPTIONAL }
    val authoredStagesReady = requiredStagesReady && optionalStagesSafe
    val selectedBuild = runtime.build.takeIf { build ->
        build.league == evidence.league && sameProjectPath(build.projectPath, evidence.projectPath)
    }
    val buildStatus = when {
        selectedBuild?.phase == BuildExecutionPhase.RUNNING -> RobotStudioStageStatus.RUNNING
        !authoredStagesReady -> RobotStudioStageStatus.BLOCKED
        selectedBuild?.phase == BuildExecutionPhase.SUCCEEDED -> RobotStudioStageStatus.READY
        selectedBuild?.phase == BuildExecutionPhase.FAILED -> RobotStudioStageStatus.INVALID
        else -> RobotStudioStageStatus.NEEDS_ACTION
    }
    val buildExplanation = when {
        selectedBuild?.phase == BuildExecutionPhase.RUNNING -> selectedBuild.message
        !authoredStagesReady -> "Resolve the blocked or invalid authoring stages before running project verification."
        selectedBuild?.phase == BuildExecutionPhase.SUCCEEDED -> selectedBuild.message
        selectedBuild?.phase == BuildExecutionPhase.FAILED -> selectedBuild.message
        selectedBuild?.phase == BuildExecutionPhase.CANCELED -> selectedBuild.message
        evidence.generatedProjectSourcePresent -> "Generated project source exists, but it is not proof of a current verified build. This app session has not run the tests and packaging yet."
        else -> "Run project verification after the canonical documents and generated source are ready."
    }
    val simulationStatus = when {
        runtime.simulatorRunning || (runtime.localSimulatorOnline && runtime.nt4Connected) -> RobotStudioStageStatus.RUNNING
        !authoredStagesReady -> RobotStudioStageStatus.BLOCKED
        else -> RobotStudioStageStatus.NEEDS_ACTION
    }
    val analysisStatus = if (evidence.importedRunCount > 0) RobotStudioStageStatus.READY else RobotStudioStageStatus.NEEDS_ACTION

    return listOf(
        stage(
            RobotStudioStageId.WORKSPACE,
            "Workspace & robot identity",
            "Select the repository and give this robot one stable identity.",
            workspaceStatus,
            if (evidence.metadataPresent) "Canonical project metadata was found." else "Create or repair .ares/project.json before generation.",
            listOfNotNull(evidence.projectError) + evidence.metadataErrors,
            ".ares/project.json",
            "Analytics, code generation, simulators, and the season robot project",
            RobotStudioAction.OPEN_PROJECT_IDENTITY,
            if (evidence.metadataPresent) "Review project identity" else "Set up project identity",
        ),
        stage(
            RobotStudioStageId.PLATFORM,
            "League & platform",
            "Keep the workspace, descriptor, generator, and runtime on the same FTC or FRC platform.",
            platformStatus,
            if (evidence.metadataLeagueMatches) "Workspace and canonical metadata agree on ${evidence.league.name}." else "The selected workspace league and canonical metadata do not agree.",
            if (platformStatus == RobotStudioStageStatus.INVALID) listOf("Choose the correct workspace league or repair the project metadata before continuing.") else emptyList(),
            ".ares/project.json",
            "Every builder and platform-specific generated adapter",
            RobotStudioAction.OPEN_PROJECT_IDENTITY,
            "Review project identity",
        ),
        stage(
            RobotStudioStageId.DRIVEBASE,
            "Drivebase",
            "Describe the hardware identity, geometry, inversion, safety, and supported runtime adapter.",
            drivebaseStatus,
            when (drivebaseStatus) {
                RobotStudioStageStatus.CODE_REQUIRED -> "This descriptor type is valid documentation, but the selected season project has no no-code runtime adapter for it."
                RobotStudioStageStatus.READY -> "A platform-supported ${evidence.drivebaseKind?.name?.lowercase()?.replace('_', ' ')} drivebase passed validation."
                else -> "Configure one platform-supported drivebase and resolve every validation error."
            },
            evidence.drivebaseErrors,
            ".ares/drivetrains/*.aresdrivetrain",
            "Generated typed configuration plus the FTC/FRC season drivetrain adapter",
            RobotStudioAction.OPEN_DRIVEBASE,
            "Open Drivebase Builder",
        ),
        stage(
            RobotStudioStageId.MECHANISMS,
            "Mechanisms & subsystems",
            "Add motors, servos, sensors, safe outputs, controllers, and simulation behavior.",
            mechanismsStatus,
            if (evidence.subsystemCount == 0) "A drive-only robot is valid. Add a subsystem when the robot has another mechanism." else "${evidence.subsystemCount} subsystem definition(s) passed project loading.",
            evidence.subsystemErrors,
            ".ares/subsystems/*.aressubsystem and user-owned starter source",
            "Generated registry, controller, IO, mock/simulator, and verification",
            RobotStudioAction.OPEN_SUBSYSTEMS,
            "Open Subsystem Builder",
        ),
        stage(
            RobotStudioStageId.LOCALIZATION,
            "Sensors & localization",
            "Choose one primary pose source and optional vision fusion with explicit units and direction conventions.",
            localizationStatus,
            if (evidence.localizationConfigured) "One validated primary localization source is configured." else "Choose a compatible primary localization source in the Drivebase Builder.",
            emptyList(),
            ".ares/drivetrains/*.aresdrivetrain",
            "Pose estimator, simulator, field preview, and autonomous validation",
            RobotStudioAction.OPEN_DRIVEBASE,
            "Configure localization",
        ),
        stage(
            RobotStudioStageId.CAPABILITIES,
            "Capabilities & actions",
            "Expose named robot behavior that controls and autonomous routines can request through Redux.",
            capabilitiesStatus,
            if (evidence.capabilityActionCount == 0) "No named actions are currently available. Sensor-only and drive-only robots may not need mechanism actions." else "${evidence.capabilityActionCount} named action(s) are available to controls and routines.",
            evidence.capabilityErrors,
            ".ares/action-catalog.json plus subsystem capability metadata",
            "Controller Bindings, autonomous routines, tasks, and Redux",
            RobotStudioAction.OPEN_SUBSYSTEMS,
            "Review subsystem capabilities",
        ),
        stage(
            RobotStudioStageId.CONTROLS,
            "Driver & operator controls",
            "Map real controller inputs to named actions, routines, and safe timing behavior.",
            controlsStatus,
            if (controlsStatus == RobotStudioStageStatus.READY) "${evidence.controlSchemeCount} control scheme(s) and ${evidence.controllerProfileCount} controller profile(s) loaded." else "Create a controller profile and conflict-free control scheme.",
            evidence.controlErrors,
            ".ares/controllers/*.arescontroller and .ares/controls/*.arescontrols",
            "Generated project bindings and the platform TeleOp runtime",
            RobotStudioAction.OPEN_CONTROLS,
            "Open TeleOp Controls",
        ),
        stage(
            RobotStudioStageId.AUTONOMOUS,
            "Autonomous routines",
            "Build bounded routines from named actions, resources, conditions, and drive steps.",
            autonomousStatus,
            if (evidence.routineCount == 0) "Autonomous is optional while learning TeleOp. Add a short simulator-first routine when ready." else "${evidence.routineCount} routine(s) and an autonomous catalog loaded.",
            evidence.autonomousErrors,
            ".ares/routines/*.aresroutine and .ares/autonomous-catalog.json",
            "Generated routine runtime and autonomous chooser",
            RobotStudioAction.OPEN_AUTONOMOUS,
            "Open Auto Builder",
        ),
        stage(
            RobotStudioStageId.TUNING,
            "Tuning & calibration",
            "Keep structural identity separate from reviewed gains, feedforward, calibration, and local experiments.",
            tuningStatus,
            when {
                evidence.tuningError != null -> "The canonical tuning graph is invalid."
                evidence.tuningDeclarationCount == 0 -> "No tunable parameters are declared yet."
                else -> "${evidence.tuningDeclarationCount} parameter(s) and ${evidence.tuningProfileCount} canonical profile(s) loaded."
            },
            listOfNotNull(evidence.tuningError),
            ".ares/tuning/*.arestuning; experiments stay in .ares/local/tuning",
            "Typed tuning runtime, simulator, and reviewed promotion workflow",
            RobotStudioAction.OPEN_TUNING,
            "Open Tuning",
        ),
        stage(
            RobotStudioStageId.GENERATE_VERIFY,
            "Verify & build",
            "Check generated ownership, run project tests, and build a package without deploying to hardware.",
            buildStatus,
            buildExplanation,
            if (selectedBuild?.phase == BuildExecutionPhase.FAILED) listOf(selectedBuild.message) else emptyList(),
            "Canonical source remains unchanged; disposable plumbing and build products stay under build/generated and build outputs",
            "Gradle ownership verification, generated contract tests, project unit tests, simulator tests, and packaging",
            RobotStudioAction.RUN_BUILD,
            when (selectedBuild?.phase) {
                BuildExecutionPhase.RUNNING -> "Verification running"
                BuildExecutionPhase.SUCCEEDED -> "Verify again"
                BuildExecutionPhase.FAILED, BuildExecutionPhase.CANCELED -> "Retry verification"
                else -> "Verify & build"
            },
        ),
        stage(
            RobotStudioStageId.SIMULATE,
            "Simulate",
            "Run the actual robot project against desktop adapters before touching hardware.",
            simulationStatus,
            if (simulationStatus == RobotStudioStageStatus.RUNNING) "The local simulator is running${if (runtime.nt4Connected) " and telemetry is connected" else ""}." else "Start the local simulator, identify the data source, and stop it cleanly when finished.",
            emptyList(),
            "No canonical source is changed by simulation",
            "FTC/FRC simulator, NT4 telemetry, Dashboard, and Academy",
            RobotStudioAction.RUN_SIMULATOR,
            if (simulationStatus == RobotStudioStageStatus.RUNNING) "Simulator running" else "Start simulator",
        ),
        stage(
            RobotStudioStageId.ANALYZE,
            "Import & analyze a run",
            "Preserve evidence, identify its source, and compare expected versus observed behavior.",
            analysisStatus,
            if (evidence.importedRunCount > 0) "${evidence.importedRunCount} run(s) for this robot are available for review." else "Import a simulator or robot log before drawing conclusions from run data.",
            emptyList(),
            "Local DuckDB; optional workspace-scoped Drive synchronization",
            "Run History, replay, deterministic diagnostics, and optional AI explanation",
            if (evidence.importedRunCount > 0) RobotStudioAction.OPEN_GUIDED_ANALYSIS else RobotStudioAction.OPEN_IMPORTS,
            if (evidence.importedRunCount > 0) "Review run evidence" else "Import a run",
        ),
    )
}

private fun sameProjectPath(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return false
    fun normalized(path: String): String = File(path).absoluteFile.normalize().path.replace('\\', '/')
    return normalized(first).equals(normalized(second), ignoreCase = File.separatorChar == '\\')
}
