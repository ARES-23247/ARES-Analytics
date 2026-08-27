package com.ares.analytics.service.project

import com.ares.analytics.service.ProcessManagerService
import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.util.ProjectLayout
import com.ares.analytics.service.project.persistence.ProjectDocumentRemovalPlan
import com.ares.analytics.service.project.persistence.RemovedProjectDocument
import com.ares.analytics.service.project.persistence.VersionedProjectDocumentStore
import com.areslib.controls.ControlSchemeDocument
import com.areslib.controls.ControllerInputPlatform
import com.areslib.controls.ControllerProfileDocument
import com.areslib.project.AresLeague
import com.areslib.project.model.ProjectModelSeverity
import com.areslib.simulation.SimulationProductId
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProjectSessionPhase { EMPTY, LOADING, READY, ERROR }

data class ProjectSessionSelection(
    val projectRoot: String,
    val targetPlatform: ControllerInputPlatform,
)

data class ProjectSessionRevision(
    val sequence: Long,
    val canonicalContentSha256: String,
)

data class ProjectSessionSnapshot(
    val selection: ProjectSessionSelection,
    val revision: ProjectSessionRevision,
    val documents: AresProjectDocumentSnapshot,
)

data class ProjectSessionState(
    val phase: ProjectSessionPhase = ProjectSessionPhase.EMPTY,
    val selection: ProjectSessionSelection? = null,
    val revision: ProjectSessionRevision? = null,
    val snapshot: ProjectSessionSnapshot? = null,
    val operation: String? = null,
    val error: String? = null,
)

sealed interface ProjectSessionMutationResult<out T> {
    data class Applied<T>(val value: T, val snapshot: ProjectSessionSnapshot) : ProjectSessionMutationResult<T>
    data class Stale(val expected: ProjectSessionRevision, val actual: ProjectSessionRevision?) :
        ProjectSessionMutationResult<Nothing>
    data class Conflict(val message: String) : ProjectSessionMutationResult<Nothing>
    data class Failed(val message: String) : ProjectSessionMutationResult<Nothing>
}

data class SavedControlDocuments(
    val changedRelativePaths: Set<String>,
)

enum class RemovableProjectDocumentKind {
    ROUTINE,
    CONTROL_SCHEME,
    CONTROLLER_PROFILE,
    SUBSYSTEM,
}

/**
 * Long-lived owner of the selected canonical robot project.
 *
 * Repositories continue to own codecs, atomic writes, history, and recovery. This session owns the
 * cross-feature selection, one immutable effective snapshot, stable content identity, and
 * revision-bound mutation boundary. It deliberately performs no robot or cloud IO.
 */
class ProjectSession(
    private val projectDocuments: AresProjectDocuments = AresProjectDocuments(),
) {
    private val lock = ReentrantLock()
    private var nextRevisionSequence = 0L
    private val _state = MutableStateFlow(ProjectSessionState())
    val state: StateFlow<ProjectSessionState> = _state.asStateFlow()

    fun snapshot(
        projectPath: String,
        targetPlatform: ControllerInputPlatform,
        forceReload: Boolean = false,
    ): ProjectSessionSnapshot = lock.withLock {
        val selection = selection(projectPath, targetPlatform)
        val current = _state.value.snapshot
        if (!forceReload && current?.selection == selection) return current
        loadLocked(selection, operation = if (forceReload) "Reloading project" else "Opening project")
    }

    fun reload(expectedRevision: ProjectSessionRevision? = null): ProjectSessionMutationResult<Unit> = lock.withLock {
        val current = _state.value.snapshot
            ?: return ProjectSessionMutationResult.Failed("No robot project is selected.")
        if (expectedRevision != null && expectedRevision != current.revision) {
            return ProjectSessionMutationResult.Stale(expectedRevision, current.revision)
        }
        runCatching { loadLocked(current.selection, "Reloading project") }
            .fold(
                onSuccess = { ProjectSessionMutationResult.Applied(Unit, it) },
                onFailure = { ProjectSessionMutationResult.Failed(it.message ?: "Project reload failed.") },
            )
    }

    fun clear() = lock.withLock {
        _state.value = ProjectSessionState()
    }

    fun metadataFile(projectPath: String): File = projectDocuments.metadata.file(projectPath)

    /**
     * Representative typed mutation used by the controller editor. Both document families commit
     * under one session revision, and any external byte change is rejected before the first write.
     */
    fun saveControls(
        expectedRevision: ProjectSessionRevision,
        profiles: Collection<ControllerProfileDocument>,
        schemes: Collection<ControlSchemeDocument>,
    ): ProjectSessionMutationResult<SavedControlDocuments> = mutate(expectedRevision, "Saving controller bindings") {
        val root = File(it.selection.projectRoot).canonicalFile
        val paths = linkedSetOf<String>()
        profiles.sortedBy(ControllerProfileDocument::documentId).forEach { profile ->
            val saved = projectDocuments.controllers.save(root.path, profile)
            paths += saved.currentFile.relativeTo(root).invariantSeparatorsPath
            paths += saved.historyFile.relativeTo(root).invariantSeparatorsPath
        }
        schemes.sortedBy(ControlSchemeDocument::documentId).forEach { scheme ->
            val saved = projectDocuments.controls.save(root.path, scheme)
            paths += saved.currentFile.relativeTo(root).invariantSeparatorsPath
            paths += saved.historyFile.relativeTo(root).invariantSeparatorsPath
        }
        SavedControlDocuments(paths)
    }

    fun removalPlan(
        expectedRevision: ProjectSessionRevision,
        kind: RemovableProjectDocumentKind,
        documentId: String,
    ): ProjectSessionMutationResult<ProjectDocumentRemovalPlan> = lock.withLock {
        val current = currentForMutation(expectedRevision)
            ?: return ProjectSessionMutationResult.Stale(expectedRevision, _state.value.revision)
        if (fingerprint(current.selection) != current.revision.canonicalContentSha256) {
            return ProjectSessionMutationResult.Conflict(
                "The project changed outside this Studio session. Reload before reviewing removal.",
            )
        }
        runCatching {
            removableStore(kind).removalPlan(current.selection.projectRoot, documentId)
        }.fold(
            onSuccess = { ProjectSessionMutationResult.Applied(it, current) },
            onFailure = { ProjectSessionMutationResult.Failed(it.message ?: "Removal review failed.") },
        )
    }

    fun remove(
        expectedRevision: ProjectSessionRevision,
        kind: RemovableProjectDocumentKind,
        documentId: String,
        expectedContentHash: String,
    ): ProjectSessionMutationResult<RemovedProjectDocument> = mutate(expectedRevision, "Removing project document") {
        removableStore(kind).remove(it.selection.projectRoot, documentId, expectedContentHash)
    }

    private fun <T> mutate(
        expectedRevision: ProjectSessionRevision,
        operation: String,
        mutation: (ProjectSessionSnapshot) -> T,
    ): ProjectSessionMutationResult<T> = lock.withLock {
        val current = currentForMutation(expectedRevision)
            ?: return ProjectSessionMutationResult.Stale(expectedRevision, _state.value.revision)
        if (fingerprint(current.selection) != current.revision.canonicalContentSha256) {
            return ProjectSessionMutationResult.Conflict(
                "The canonical project changed after this form was loaded. Reload before saving.",
            )
        }
        _state.value = _state.value.copy(operation = operation, error = null)
        runCatching {
            val value = mutation(current)
            value to loadLocked(current.selection, "Refreshing project after $operation")
        }.fold(
            onSuccess = { (value, refreshed) -> ProjectSessionMutationResult.Applied(value, refreshed) },
            onFailure = { error ->
                _state.value = _state.value.copy(operation = null, error = error.message)
                ProjectSessionMutationResult.Failed(error.message ?: "$operation failed.")
            },
        )
    }

    private fun currentForMutation(expected: ProjectSessionRevision): ProjectSessionSnapshot? =
        _state.value.snapshot?.takeIf { it.revision == expected }

    private fun loadLocked(selection: ProjectSessionSelection, operation: String): ProjectSessionSnapshot {
        val previousSnapshot = _state.value.snapshot
        _state.value = ProjectSessionState(
            phase = ProjectSessionPhase.LOADING,
            selection = selection,
            operation = operation,
        )
        return runCatching { loadStable(selection) }
            .onFailure { error ->
                _state.value = ProjectSessionState(
                    phase = ProjectSessionPhase.ERROR,
                    selection = selection,
                    error = error.message ?: "Project could not be loaded.",
                )
            }
            .getOrThrow()
            .let { (documents, contentHash) ->
                val revision = previousSnapshot
                    ?.takeIf { it.selection == selection && it.revision.canonicalContentSha256 == contentHash }
                    ?.revision
                    ?: ProjectSessionRevision(++nextRevisionSequence, contentHash)
                val snapshot = ProjectSessionSnapshot(
                    selection = selection,
                    revision = revision,
                    documents = documents,
                )
                _state.value = ProjectSessionState(
                    phase = ProjectSessionPhase.READY,
                    selection = selection,
                    revision = snapshot.revision,
                    snapshot = snapshot,
                )
                snapshot
            }
    }

    private fun loadStable(selection: ProjectSessionSelection): Pair<AresProjectDocumentSnapshot, String> {
        repeat(2) { attempt ->
            val before = fingerprint(selection)
            val loaded = projectDocuments.load(selection.projectRoot, selection.targetPlatform)
            val after = fingerprint(selection)
            if (before == after) return loaded to after
            if (attempt == 1) {
                error("The canonical project kept changing while ARES was loading it. Wait for the other edit to finish, then reload.")
            }
        }
        error("Project load did not produce a stable snapshot.")
    }

    private fun selection(projectPath: String, targetPlatform: ControllerInputPlatform): ProjectSessionSelection {
        require(projectPath.isNotBlank()) { "Choose a robot project directory." }
        val root = File(projectPath).canonicalFile
        require(root.isDirectory) { "Project directory does not exist: ${root.path}" }
        return ProjectSessionSelection(root.path, targetPlatform)
    }

    private fun fingerprint(selection: ProjectSessionSelection): String {
        val root = File(selection.projectRoot).canonicalFile
        val canonicalFiles = buildList {
            val aresRoot = File(root, ".ares")
            if (aresRoot.isDirectory) {
                addAll(
                    aresRoot.walkTopDown()
                        .filter(File::isFile)
                        .filterNot { file ->
                            val relative = file.relativeTo(aresRoot).invariantSeparatorsPath
                            relative.startsWith("history/") ||
                                relative.startsWith("recovery/") ||
                                relative.startsWith("drafts/") ||
                                relative.startsWith("backups/") ||
                                relative.startsWith("evidence/") ||
                                relative.startsWith("local/") ||
                                relative.startsWith("verification/")
                        },
                )
            }
            val league = when (selection.targetPlatform) {
                ControllerInputPlatform.FTC -> League.FTC
                ControllerInputPlatform.FRC -> League.FRC
                ControllerInputPlatform.DESKTOP_GLFW -> error("Desktop input is not a robot project target.")
            }
            ProjectLayout.fieldDefinitionFile(root.path, league).takeIf(File::isFile)?.let(::add)
        }.distinctBy { it.canonicalPath }.sortedBy { it.relativeTo(root).invariantSeparatorsPath }

        val digest = MessageDigest.getInstance("SHA-256")
        canonicalFiles.forEach { file ->
            digest.update(file.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
            digest.update(0)
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.update(0)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun removableStore(kind: RemovableProjectDocumentKind): VersionedProjectDocumentStore<*> = when (kind) {
        RemovableProjectDocumentKind.ROUTINE -> projectDocuments.routines
        RemovableProjectDocumentKind.CONTROL_SCHEME -> projectDocuments.controls
        RemovableProjectDocumentKind.CONTROLLER_PROFILE -> projectDocuments.controllers
        RemovableProjectDocumentKind.SUBSYSTEM -> projectDocuments.subsystems
    }
}

enum class ProjectExecutionCommand { GENERATE, VERIFY_AND_BUILD, SIMULATE, DEPLOY }

data class ProjectExecutionDecision(
    val accepted: Boolean,
    val message: String,
)

/**
 * One authorization boundary for project-owned external processes. It derives league and simulator
 * identity from the same effective snapshot used by authoring, never from an unrelated UI guess.
 */
class ProjectExecutionCoordinator(
    private val session: ProjectSession,
    private val processGateway: ProjectProcessGateway,
) {
    fun execute(
        workspace: WorkspaceConfig,
        command: ProjectExecutionCommand,
    ): ProjectExecutionDecision {
        val targetPlatform = when (workspace.league) {
            League.FTC -> ControllerInputPlatform.FTC
            League.FRC -> ControllerInputPlatform.FRC
        }
        val snapshot = runCatching {
            session.snapshot(workspace.projectPath, targetPlatform, forceReload = true)
        }
            .getOrElse { return ProjectExecutionDecision(false, it.message ?: "Project could not be opened.") }
        val project = snapshot.documents.effectiveProject
        val expectedLeague = when (workspace.league) {
            League.FTC -> AresLeague.FTC
            League.FRC -> AresLeague.FRC
        }
        if (project.raw.metadata?.league != expectedLeague) {
            return ProjectExecutionDecision(false, "The selected workspace league does not match .ares/project.json.")
        }
        val errors = project.issues.filter { it.severity == ProjectModelSeverity.ERROR }
        if (errors.isNotEmpty()) {
            return ProjectExecutionDecision(false, errors.joinToString(" ") { it.message })
        }
        val simulationPlan = project.simulationPlan
        if (command == ProjectExecutionCommand.SIMULATE && simulationPlan?.isSupported != true) {
            return ProjectExecutionDecision(
                false,
                simulationPlan?.issues?.joinToString(" ") { it.message }
                    ?: "ARES could not select a simulator for this project.",
            )
        }

        when (command) {
            ProjectExecutionCommand.GENERATE ->
                processGateway.generate(workspace.projectPath, workspace.league)
            ProjectExecutionCommand.VERIFY_AND_BUILD ->
                processGateway.verifyAndBuild(workspace.projectPath, workspace.league)
            ProjectExecutionCommand.SIMULATE -> processGateway.simulate(
                workspace.projectPath,
                requireNotNull(simulationPlan).product.id,
                workspace.simulatorCommand,
            )
            ProjectExecutionCommand.DEPLOY ->
                processGateway.deploy(workspace.projectPath, workspace.league)
        }
        return ProjectExecutionDecision(
            true,
            when (command) {
                ProjectExecutionCommand.GENERATE -> "Project generation started."
                ProjectExecutionCommand.VERIFY_AND_BUILD -> "Verification and build started."
                ProjectExecutionCommand.SIMULATE -> "${requireNotNull(simulationPlan).product.id.displayName} started."
                ProjectExecutionCommand.DEPLOY -> "Robot deployment started."
            },
        )
    }
}

interface ProjectProcessGateway {
    fun generate(projectPath: String, league: League)
    fun verifyAndBuild(projectPath: String, league: League)
    fun simulate(projectPath: String, product: SimulationProductId, simulatorCommand: String?)
    fun deploy(projectPath: String, league: League)
}

class ProcessManagerProjectGateway(
    private val processManager: ProcessManagerService,
) : ProjectProcessGateway {
    override fun generate(projectPath: String, league: League) =
        processManager.generateAresProject(projectPath, league)

    override fun verifyAndBuild(projectPath: String, league: League) =
        processManager.runBuild(projectPath, league)

    override fun simulate(projectPath: String, product: SimulationProductId, simulatorCommand: String?) =
        processManager.runSimulation(projectPath, product, simulatorCommand)

    override fun deploy(projectPath: String, league: League) =
        processManager.deployToRobot(projectPath, league)
}
