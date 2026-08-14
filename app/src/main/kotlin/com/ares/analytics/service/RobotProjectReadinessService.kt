package com.ares.analytics.service

import com.ares.analytics.service.drivebase.DrivebaseKind
import com.ares.analytics.service.drivebase.DrivebaseProjectRepository
import com.ares.analytics.service.drivebase.DrivebaseIssueSeverity
import com.ares.analytics.service.drivebase.LocalizationKind
import com.ares.analytics.service.drivebase.validateDrivebase
import com.ares.analytics.service.tuning.TuningProfileRepository
import com.ares.analytics.shared.League
import com.ares.analytics.shared.models.WorkspaceConfig
import com.ares.analytics.util.ProjectLayout
import com.ares.analytics.viewmodel.project.AresProjectDocuments
import com.ares.analytics.viewmodel.project.ProjectDocumentKind
import com.areslib.project.AresLeague
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Canonical project evidence used by Robot Studio. No field represents physical validation. */
data class RobotProjectReadinessEvidence(
    val projectPath: String,
    val league: League,
    val projectError: String? = null,
    val metadataPresent: Boolean = false,
    val metadataLeagueMatches: Boolean = false,
    val metadataErrors: List<String> = emptyList(),
    val documentErrors: List<String> = emptyList(),
    val drivebaseKind: DrivebaseKind? = null,
    val drivebaseErrors: List<String> = emptyList(),
    val drivebaseNoCodeSupported: Boolean = false,
    val localizationConfigured: Boolean = false,
    val subsystemCount: Int = 0,
    val subsystemErrors: List<String> = emptyList(),
    val capabilityActionCount: Int = 0,
    val capabilityErrors: List<String> = emptyList(),
    val controlSchemeCount: Int = 0,
    val controllerProfileCount: Int = 0,
    val controlErrors: List<String> = emptyList(),
    val routineCount: Int = 0,
    val autonomousCatalogPresent: Boolean = false,
    val autonomousErrors: List<String> = emptyList(),
    val tuningDeclarationCount: Int = 0,
    val tuningProfileCount: Int = 0,
    val tuningError: String? = null,
    val generatedProjectSourcePresent: Boolean = false,
    val importedRunCount: Int = 0,
)

/**
 * Reads the existing canonical project documents and local run database without changing either.
 * The Studio consumes this evidence through its own pure stage evaluator.
 */
class RobotProjectReadinessService(
    private val databaseService: DatabaseService,
    private val projectDocuments: AresProjectDocuments = AresProjectDocuments(),
    private val drivebaseRepository: DrivebaseProjectRepository = DrivebaseProjectRepository(),
    private val tuningRepository: TuningProfileRepository = TuningProfileRepository(),
) {
    suspend fun inspect(config: WorkspaceConfig): RobotProjectReadinessEvidence = withContext(Dispatchers.IO) {
        val projectError = ProjectLayout.validationError(config.projectPath, config.league)
        if (projectError != null) {
            return@withContext RobotProjectReadinessEvidence(
                projectPath = config.projectPath,
                league = config.league,
                projectError = projectError,
            )
        }

        val projectSnapshot = runCatching { projectDocuments.load(config.projectPath) }
        val snapshot = projectSnapshot.getOrNull()
        val snapshotFailure = projectSnapshot.exceptionOrNull()?.message
        val diagnostics = snapshot?.diagnostics.orEmpty()

        val drivebaseResult = drivebaseRepository.load(config.projectPath)
        val drivebase = drivebaseResult.getOrNull()
        val drivebaseIssues = drivebase?.let(::validateDrivebase).orEmpty()
        val drivebaseErrors = buildList {
            drivebaseResult.exceptionOrNull()?.message?.let(::add)
            addAll(drivebaseIssues.filter { it.severity == DrivebaseIssueSeverity.ERROR }.map { it.message })
        }.distinct()

        val tuningResult = tuningRepository.load(config.projectPath)
        val tuning = tuningResult.getOrNull()
        val matchingRuns = databaseService.getSessions().count { session ->
            session.teamId == config.teamId &&
                session.seasonId == config.seasonId &&
                session.robotId == config.robotId
        }
        val expectedLeague = when (config.league) {
            League.FTC -> AresLeague.FTC
            League.FRC -> AresLeague.FRC
        }
        val subsystemErrors = diagnostics.filter { it.kind == ProjectDocumentKind.SUBSYSTEM }
            .map { "${it.file.name}: ${it.message}" }
        val metadataFilePresent = projectDocuments.metadata.file(config.projectPath).isFile
        val metadataErrors = diagnostics.filter {
            it.kind == ProjectDocumentKind.PROJECT_METADATA && metadataFilePresent
        }.map { "${it.file.name}: ${it.message}" }
        val capabilityErrors = diagnostics.filter { it.kind == ProjectDocumentKind.CAPABILITY_CATALOG }
            .map { "${it.file.name}: ${it.message}" }
        val controlErrors = diagnostics.filter {
            it.kind == ProjectDocumentKind.CONTROL_SCHEME || it.kind == ProjectDocumentKind.CONTROLLER_PROFILE
        }.map { "${it.file.name}: ${it.message}" }
        val autonomousErrors = diagnostics.filter {
            it.kind == ProjectDocumentKind.ROUTINE || it.kind == ProjectDocumentKind.AUTONOMOUS_CATALOG
        }.map { "${it.file.name}: ${it.message}" }

        RobotProjectReadinessEvidence(
            projectPath = config.projectPath,
            league = config.league,
            projectError = snapshotFailure,
            metadataPresent = snapshot?.projectMetadata != null,
            metadataLeagueMatches = snapshot?.projectMetadata?.league == expectedLeague,
            metadataErrors = metadataErrors,
            documentErrors = diagnostics.map { "${it.file.name}: ${it.message}" },
            drivebaseKind = drivebase?.kind,
            drivebaseErrors = drivebaseErrors,
            drivebaseNoCodeSupported = when (config.league) {
                League.FTC -> drivebase?.kind == DrivebaseKind.FTC_MECANUM
                League.FRC -> drivebase?.kind == DrivebaseKind.FRC_CTRE_SWERVE
            },
            localizationConfigured = drivebase != null && drivebaseErrors.isEmpty() &&
                drivebase.localization.count { it != LocalizationKind.VISION_FUSION } == 1,
            subsystemCount = snapshot?.subsystems?.size ?: 0,
            subsystemErrors = subsystemErrors,
            capabilityActionCount = snapshot?.capabilityCatalog?.actions?.size ?: 0,
            capabilityErrors = capabilityErrors,
            controlSchemeCount = snapshot?.controlSchemes?.size ?: 0,
            controllerProfileCount = snapshot?.controllerProfiles?.size ?: 0,
            controlErrors = controlErrors,
            routineCount = snapshot?.routines?.size ?: 0,
            autonomousCatalogPresent = snapshot?.autonomousCatalog != null,
            autonomousErrors = autonomousErrors,
            tuningDeclarationCount = tuning?.catalog?.size ?: 0,
            tuningProfileCount = tuning?.profiles?.size ?: 0,
            tuningError = tuningResult.exceptionOrNull()?.message,
            generatedProjectSourcePresent = generatedProjectFile(config).isFile,
            importedRunCount = matchingRuns,
        )
    }

    private fun generatedProjectFile(config: WorkspaceConfig): File = File(
        config.projectPath,
        when (config.league) {
            League.FTC -> "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/generated/GeneratedAresProject.kt"
            League.FRC -> "src/main/kotlin/com/areslib/frc/generated/GeneratedAresProject.kt"
        },
    )
}
