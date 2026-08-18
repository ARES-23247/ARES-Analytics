package com.ares.analytics.viewmodel.robotstudio

import com.ares.analytics.service.BuildExecutionPhase
import com.ares.analytics.service.BuildExecutionState
import com.ares.analytics.service.DeployExecutionPhase
import com.ares.analytics.service.DeployExecutionState
import com.ares.analytics.service.RobotProjectReadinessEvidence
import com.ares.analytics.service.hardware.HardwareReviewStatus
import java.io.File

enum class RobotStudioStageId {
    PROJECT_IDENTITY,
    HARDWARE,
    COORDINATION,
    AUTONOMOUS,
    CONTROLS,
    TUNING,
    GENERATE_VERIFY,
    SIMULATE,
    DEPLOY,
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
    OPEN_SUPERSTRUCTURES,
    OPEN_HARDWARE_SETUP,
    OPEN_CONTROLS,
    OPEN_AUTONOMOUS,
    OPEN_TUNING,
    RUN_BUILD,
    RUN_SIMULATOR,
    DEPLOY_ROBOT,
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
    val deploy: DeployExecutionState = DeployExecutionState(),
    val simulatorRunning: Boolean = false,
    val simulatorProjectPath: String? = null,
    val simulatorLeague: com.ares.analytics.shared.League? = null,
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

    val projectIdentityStatus = when {
        projectBlocked -> RobotStudioStageStatus.INVALID
        evidence.metadataErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        !evidence.metadataPresent -> RobotStudioStageStatus.NEEDS_ACTION
        !evidence.metadataLeagueMatches -> RobotStudioStageStatus.INVALID
        else -> RobotStudioStageStatus.READY
    }
    val drivebaseStatus = when {
        projectBlocked || projectIdentityStatus != RobotStudioStageStatus.READY -> RobotStudioStageStatus.BLOCKED
        evidence.drivebaseKind == null && evidence.drivebaseErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.drivebaseKind == null -> RobotStudioStageStatus.NEEDS_ACTION
        evidence.drivebaseErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        !evidence.drivebaseNoCodeSupported -> RobotStudioStageStatus.CODE_REQUIRED
        else -> RobotStudioStageStatus.READY
    }
    val localizationStatus = when {
        drivebaseStatus == RobotStudioStageStatus.BLOCKED -> RobotStudioStageStatus.BLOCKED
        drivebaseStatus == RobotStudioStageStatus.INVALID -> RobotStudioStageStatus.BLOCKED
        evidence.localizationConfigured -> RobotStudioStageStatus.READY
        else -> RobotStudioStageStatus.NEEDS_ACTION
    }
    val mechanismsStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.subsystemErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.subsystemCount == 0 -> RobotStudioStageStatus.OPTIONAL
        else -> RobotStudioStageStatus.READY
    }
    val hardwareReviewStatus = when {
        projectBlocked || projectIdentityStatus != RobotStudioStageStatus.READY -> RobotStudioStageStatus.BLOCKED
        evidence.hardwareErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.hardwareItemCount == 0 -> RobotStudioStageStatus.NEEDS_ACTION
        evidence.hardwareReviewStatus == HardwareReviewStatus.CURRENT -> RobotStudioStageStatus.READY
        else -> RobotStudioStageStatus.NEEDS_ACTION
    }
    val capabilitiesStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.capabilityErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.capabilityActionCount == 0 -> RobotStudioStageStatus.OPTIONAL
        else -> RobotStudioStageStatus.READY
    }

    val hardwareStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        drivebaseStatus == RobotStudioStageStatus.INVALID || mechanismsStatus == RobotStudioStageStatus.INVALID ||
            hardwareReviewStatus == RobotStudioStageStatus.INVALID || capabilitiesStatus == RobotStudioStageStatus.INVALID ||
            evidence.drivebaseErrors.isNotEmpty() || evidence.subsystemErrors.isNotEmpty() ||
            evidence.hardwareErrors.isNotEmpty() || evidence.capabilityErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        projectIdentityStatus != RobotStudioStageStatus.READY -> RobotStudioStageStatus.BLOCKED
        drivebaseStatus == RobotStudioStageStatus.CODE_REQUIRED -> RobotStudioStageStatus.CODE_REQUIRED
        drivebaseStatus == RobotStudioStageStatus.NEEDS_ACTION || localizationStatus == RobotStudioStageStatus.NEEDS_ACTION ||
            (evidence.hardwareItemCount > 0 && evidence.hardwareReviewStatus == HardwareReviewStatus.NOT_REVIEWED) -> RobotStudioStageStatus.NEEDS_ACTION
        else -> RobotStudioStageStatus.READY
    }

    val coordinationStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.superstructureErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.superstructureCount == 0 -> RobotStudioStageStatus.OPTIONAL
        else -> RobotStudioStageStatus.READY
    }
    val autonomousStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.autonomousErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.routineCount == 0 || !evidence.autonomousCatalogPresent -> RobotStudioStageStatus.OPTIONAL
        else -> RobotStudioStageStatus.READY
    }
    val controlsStatus = when {
        projectBlocked -> RobotStudioStageStatus.BLOCKED
        evidence.controlErrors.isNotEmpty() -> RobotStudioStageStatus.INVALID
        evidence.controlSchemeCount == 0 && evidence.controllerProfileCount == 0 -> RobotStudioStageStatus.OPTIONAL
        evidence.controlSchemeCount == 0 || evidence.controllerProfileCount == 0 -> RobotStudioStageStatus.NEEDS_ACTION
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
        projectIdentityStatus,
        hardwareStatus,
    ).all { it == RobotStudioStageStatus.READY }

    val optionalStagesSafe = listOf(
        coordinationStatus,
        autonomousStatus,
        controlsStatus,
        tuningStatus,
    ).all { it == RobotStudioStageStatus.READY || it == RobotStudioStageStatus.OPTIONAL }

    val authoredStagesReady = requiredStagesReady && optionalStagesSafe
    val selectedBuild = runtime.build.takeIf { build ->
        build.league == evidence.league && sameProjectPath(build.projectPath, evidence.projectPath)
    }
    val selectedSimulatorRunning = runtime.simulatorRunning &&
        runtime.simulatorLeague == evidence.league &&
        runtime.simulatorProjectPath?.let { sameProjectPath(it, evidence.projectPath) } == true
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
        !authoredStagesReady -> RobotStudioStageStatus.BLOCKED
        selectedSimulatorRunning -> RobotStudioStageStatus.RUNNING
        selectedBuild?.phase != BuildExecutionPhase.SUCCEEDED -> RobotStudioStageStatus.BLOCKED
        else -> RobotStudioStageStatus.NEEDS_ACTION
    }
    val deployStatus = when {
        runtime.deploy.phase == DeployExecutionPhase.CONNECTING ||
            runtime.deploy.phase == DeployExecutionPhase.BUILDING ||
            runtime.deploy.phase == DeployExecutionPhase.INSTALLING -> RobotStudioStageStatus.RUNNING
        runtime.deploy.phase == DeployExecutionPhase.SUCCEEDED -> RobotStudioStageStatus.READY
        runtime.deploy.phase == DeployExecutionPhase.FAILED -> RobotStudioStageStatus.INVALID
        evidence.physicalDeploymentBlockReason != null -> RobotStudioStageStatus.BLOCKED
        !authoredStagesReady -> RobotStudioStageStatus.BLOCKED
        else -> RobotStudioStageStatus.NEEDS_ACTION
    }
    val analysisStatus = if (evidence.importedRunCount > 0) RobotStudioStageStatus.READY else RobotStudioStageStatus.NEEDS_ACTION

    val hardwareIssues = evidence.drivebaseErrors + evidence.subsystemErrors + evidence.hardwareErrors + evidence.capabilityErrors
    val hardwareExplanation = when {
        hardwareStatus == RobotStudioStageStatus.CODE_REQUIRED -> "This drivebase descriptor type is valid documentation, but the selected season project has no no-code runtime adapter for it."
        evidence.drivebaseKind == null -> "Configure one platform-supported drivebase and add any needed mechanism subsystems."
        !evidence.localizationConfigured -> "Configure a compatible primary localization source in the Drivebase settings."
        evidence.hardwareReviewStatus == HardwareReviewStatus.NOT_REVIEWED && evidence.hardwareItemCount > 0 -> "${evidence.hardwareItemCount} physical device(s) are declared. Review the hardware port and CAN map before physical testing."
        evidence.subsystemCount > 0 -> "A platform-supported ${evidence.drivebaseKind.name.lowercase().replace('_', ' ')} drivetrain and ${evidence.subsystemCount} subsystem(s) passed validation."
        else -> "A platform-supported ${evidence.drivebaseKind.name.lowercase().replace('_', ' ')} drive-only robot passed validation."
    }

    val hardwareAction = when {
        evidence.drivebaseKind == null -> RobotStudioAction.OPEN_DRIVEBASE
        evidence.hardwareReviewStatus == HardwareReviewStatus.NOT_REVIEWED && evidence.hardwareItemCount > 0 -> RobotStudioAction.OPEN_HARDWARE_SETUP
        evidence.subsystemCount == 0 -> RobotStudioAction.OPEN_SUBSYSTEMS
        else -> RobotStudioAction.OPEN_HARDWARE_SETUP
    }
    val hardwareActionLabel = when {
        evidence.drivebaseKind == null -> "Open Drivebase Builder"
        evidence.hardwareReviewStatus == HardwareReviewStatus.NOT_REVIEWED && evidence.hardwareItemCount > 0 -> "Open Hardware Setup"
        evidence.subsystemCount == 0 -> "Add Subsystems"
        else -> "Open Hardware Setup"
    }

    return listOf(
        stage(
            RobotStudioStageId.PROJECT_IDENTITY,
            "Project & robot identity",
            "Select the repository, assign team identity, and choose the FTC/FRC platform.",
            projectIdentityStatus,
            if (projectIdentityStatus == RobotStudioStageStatus.READY) "Workspace and canonical metadata agree on ${evidence.league.name}."
            else if (!evidence.metadataPresent) "Create or repair .ares/project.json before generation."
            else "Workspace league and canonical metadata do not agree.",
            listOfNotNull(evidence.projectError) + evidence.metadataErrors +
                if (!evidence.metadataLeagueMatches && evidence.metadataPresent) listOf("Choose the correct workspace league or repair project metadata.") else emptyList(),
            ".ares/project.json",
            "Analytics, code generation, simulators, and platform runtime adapters",
            RobotStudioAction.OPEN_PROJECT_IDENTITY,
            if (evidence.metadataPresent) "Review identity" else "Set up identity",
        ),
        stage(
            RobotStudioStageId.HARDWARE,
            "Robot hardware & mechanisms",
            "Configure drivetrain kinematics, localization, mechanism subsystems, and physical port/CAN topology.",
            hardwareStatus,
            hardwareExplanation,
            hardwareIssues,
            ".ares/drivetrains/*.aresdrivetrain, .ares/subsystems/*.aressubsystem, .ares/hardware-review.json",
            "Generated hardware registry, drivetrain & subsystem IO controllers, and simulator",
            hardwareAction,
            hardwareActionLabel,
        ),
        stage(
            RobotStudioStageId.COORDINATION,
            "Superstructure coordination",
            "Coordinate multiple generated mechanisms through complete presets, guarded transitions, interlocks, and lookup tables.",
            coordinationStatus,
            if (evidence.superstructureCount == 0) {
                "Optional. Add a coordinator only when mechanisms must move together; a single mechanism stays in Subsystem Builder."
            } else {
                "${evidence.superstructureCount} generated coordinator definition(s) passed project validation."
            },
            evidence.superstructureErrors,
            ".ares/superstructures/*.aressuperstructure",
            "Generated Redux coordinator runtime, subsystem target tasks, and contract tests",
            RobotStudioAction.OPEN_SUPERSTRUCTURES,
            "Open Superstructure Studio",
        ),
        stage(
            RobotStudioStageId.AUTONOMOUS,
            "Autonomous catalog & routines",
            "Build bounded routines from named actions, path segments, triggers, and scoring sequences.",
            autonomousStatus,
            if (evidence.routineCount == 0) "Autonomous is optional while learning TeleOp. Add a short simulator-first routine when ready." else "${evidence.routineCount} routine(s) and an autonomous catalog loaded.",
            evidence.autonomousErrors,
            ".ares/routines/*.aresroutine and .ares/autonomous-catalog.json",
            "Generated routine runtime, autonomous chooser, and TeleOp routine bindings",
            RobotStudioAction.OPEN_AUTONOMOUS,
            "Open Auto Builder",
        ),
        stage(
            RobotStudioStageId.CONTROLS,
            "Driver & operator controls",
            "Map real controller inputs to drive axes, named mechanism actions, and automated TeleOp sub-routines.",
            controlsStatus,
            when (controlsStatus) {
                RobotStudioStageStatus.READY -> "${evidence.controlSchemeCount} control scheme(s) and ${evidence.controllerProfileCount} controller profile(s) loaded."
                RobotStudioStageStatus.OPTIONAL -> "The reviewed season project supplies safe baseline driving controls. Add GUI bindings when you want controller buttons to run named mechanism actions or auto-routines."
                else -> "A controller profile and control scheme must be created together; finish or remove the incomplete pair."
            },
            evidence.controlErrors,
            ".ares/controllers/*.arescontroller and .ares/controls/*.arescontrols",
            "Generated project bindings and the platform TeleOp runtime",
            RobotStudioAction.OPEN_CONTROLS,
            "Open TeleOp Controls",
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
            when {
                simulationStatus == RobotStudioStageStatus.RUNNING -> "The local simulator is running${if (runtime.nt4Connected) " and telemetry is connected" else ""}."
                !authoredStagesReady -> "Resolve the blocked authoring stages before simulation."
                selectedBuild?.phase != BuildExecutionPhase.SUCCEEDED -> "Run Verify & build successfully first so simulation uses current generated code and tested project artifacts."
                else -> "Start the verified local simulator, identify the data source, and stop it cleanly when finished."
            },
            emptyList(),
            "No canonical source is changed by simulation",
            "FTC/FRC simulator, NT4 telemetry, Dashboard, and Academy",
            RobotStudioAction.RUN_SIMULATOR,
            when {
                simulationStatus == RobotStudioStageStatus.RUNNING -> "Simulator running"
                simulationStatus == RobotStudioStageStatus.BLOCKED -> "Verify & build first"
                else -> "Start simulator"
            },
        ),
        stage(
            RobotStudioStageId.DEPLOY,
            "1-Click Deploy to robot",
            "Connect over Wi-Fi, compile, and flash APK/binary directly to the robot.",
            deployStatus,
            evidence.physicalDeploymentBlockReason ?: runtime.deploy.message,
            listOfNotNull(evidence.physicalDeploymentBlockReason),
            "Wireless connection (192.168.43.1:5555 / SSH)",
            "Physical FTC Control Hub / FRC RoboRIO",
            RobotStudioAction.DEPLOY_ROBOT,
            when (runtime.deploy.phase) {
                DeployExecutionPhase.CONNECTING, DeployExecutionPhase.BUILDING, DeployExecutionPhase.INSTALLING -> "Deploying now..."
                DeployExecutionPhase.SUCCEEDED -> "Deploy again"
                DeployExecutionPhase.FAILED, DeployExecutionPhase.CANCELED -> "Retry deploy"
                else -> "1-Click Deploy"
            },
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
