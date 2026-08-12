package com.ares.analytics.viewmodel

import com.ares.analytics.service.AresGenerationPhase
import com.ares.analytics.service.AresProjectGenerator
import com.ares.analytics.shared.League
import com.ares.analytics.viewmodel.project.AresProjectDocuments
import com.ares.analytics.viewmodel.project.ProjectDocumentKind
import com.areslib.codegen.GeneratedSubsystemSourceSet
import com.areslib.codegen.SubsystemArtifact
import com.areslib.codegen.SubsystemArtifactGroup
import com.areslib.codegen.SubsystemArtifactOwnership
import com.areslib.codegen.SubsystemKotlinCodegenTarget
import com.areslib.codegen.SubsystemKotlinGenerator
import com.areslib.codegen.SubsystemStarterReconciler
import com.areslib.codegen.SubsystemStarterChangeKind
import com.areslib.subsystem.SubsystemControlLoopDocument
import com.areslib.subsystem.SubsystemControlStrategy
import com.areslib.subsystem.SubsystemDocument
import com.areslib.subsystem.SubsystemFieldRole
import com.areslib.subsystem.SubsystemHardwareConnection
import com.areslib.subsystem.SubsystemHardwareDocument
import com.areslib.subsystem.SubsystemHardwareKind
import com.areslib.subsystem.SubsystemImplementationDocument
import com.areslib.subsystem.SubsystemImplementationKind
import com.areslib.subsystem.SubsystemMeasurementSource
import com.areslib.subsystem.SubsystemPlatform
import com.areslib.subsystem.SubsystemSimulationDocument
import com.areslib.subsystem.SubsystemSimulationSupport
import com.areslib.subsystem.SubsystemSourceOwnership
import com.areslib.subsystem.SubsystemStateFieldDocument
import com.areslib.subsystem.SubsystemTeachingDocument
import com.areslib.subsystem.SubsystemTeachingLevel
import com.areslib.subsystem.SubsystemTemplate
import com.areslib.subsystem.SubsystemTemplates
import com.areslib.subsystem.SubsystemValueType
import com.areslib.subsystem.validateSubsystemDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class SubsystemProblemSeverity { WARNING, ERROR }

/** Novice-facing authoring stages. The order mirrors the questions a student can answer safely. */
enum class SubsystemBuilderStage(
    val displayName: String,
    val shortDescription: String,
) {
    PURPOSE("Purpose", "Choose what the subsystem does and how its source is owned."),
    HARDWARE("Hardware", "Describe each motor, servo, sensor, or other device."),
    STATE_AND_BEHAVIOR("State & behavior", "Define cached inputs, targets, and controller rules."),
    SAFETY("Safety", "Decide when outputs are permitted and how faults recover."),
    CAPABILITIES("Capabilities", "Review what drivers and autonomous routines can command."),
    SIMULATION_AND_TESTING("Simulation & testing", "Choose mock support and generated verification."),
    REVIEW("Review", "Check warnings, ownership, and generated files before saving."),
}

data class SubsystemProblem(
    val severity: SubsystemProblemSeverity,
    val path: String,
    val message: String,
)

data class SubsystemPreviewFile(
    val path: String,
    val sourceSet: GeneratedSubsystemSourceSet,
    val content: String,
    val artifact: SubsystemArtifact,
    val group: SubsystemArtifactGroup,
    val ownership: SubsystemArtifactOwnership,
    val description: String,
    val moduleName: String,
    val projectRelativePath: String,
    val change: SubsystemFileChange,
    val diff: List<SubsystemDiffLine> = emptyList(),
)

enum class SubsystemFileChange { CREATE, UNCHANGED, UPDATE_GENERATED, REPLACE_STARTER, PROTECTED_USER_OWNED }

enum class SubsystemDiffLineKind { CONTEXT, ADDED, REMOVED }

data class SubsystemDiffLine(val kind: SubsystemDiffLineKind, val text: String)

data class SubsystemTemplateOption(
    val template: SubsystemTemplate,
    val label: String,
    val description: String,
)

val subsystemTemplateOptions = listOf(
    SubsystemTemplateOption(
        SubsystemTemplate.SIMPLE_ACTUATOR,
        "Simple actuator",
        "Bounded open-loop motor output with a declared neutral state and fault monitoring.",
    ),
    SubsystemTemplateOption(
        SubsystemTemplate.POSITION_CONTROLLED_MECHANISM,
        "Position-controlled mechanism",
        "Closed-loop position control with cached feedback, soft limits, and stale-signal handling.",
    ),
    SubsystemTemplateOption(
        SubsystemTemplate.VELOCITY_CONTROLLED_MECHANISM,
        "Velocity-controlled mechanism",
        "Closed-loop velocity control with current monitoring and a safe spin-down path.",
    ),
    SubsystemTemplateOption(
        SubsystemTemplate.SENSOR_ONLY_SUBSYSTEM,
        "Sensor-only subsystem",
        "A cached, validity-aware input snapshot with telemetry and no actuator output.",
    ),
    SubsystemTemplateOption(
        SubsystemTemplate.HOMED_MECHANISM,
        "Homed mechanism",
        "Position control gated on an explicit home reference, calibration health, and soft limits.",
    ),
    SubsystemTemplateOption(
        SubsystemTemplate.COMPOSITE_MECHANISM,
        "Composite mechanism",
        "Coordinated devices with one atomic snapshot, neutral policy, and partial-failure handling.",
    ),
    SubsystemTemplateOption(
        SubsystemTemplate.ADVANCED_CUSTOM,
        "Advanced/custom",
        "An explicit starting point that requires every applicable hardware and safety choice.",
    ),
)

data class SubsystemGeneratorState(
    val projectPath: String,
    val league: League,
    val documents: List<SubsystemDocument> = emptyList(),
    val selectedDocumentId: String? = null,
    val draft: SubsystemDocument? = null,
    val selectedHardwareId: String? = null,
    val selectedFieldId: String? = null,
    val selectedLoopId: String? = null,
    val activeStage: SubsystemBuilderStage = SubsystemBuilderStage.PURPOSE,
    val selectedTemplate: SubsystemTemplate = SubsystemTemplate.POSITION_CONTROLLED_MECHANISM,
    val previewFiles: List<SubsystemPreviewFile> = emptyList(),
    val generatedPlumbingExpanded: Boolean = false,
    val pendingStarterReplacements: List<SubsystemPreviewFile> = emptyList(),
    val starterConfirmationToken: String? = null,
    val problems: List<SubsystemProblem> = emptyList(),
    val dirty: Boolean = false,
    val generationPhase: AresGenerationPhase = AresGenerationPhase.IDLE,
    val generationMessage: String? = null,
    val generatedContentHash: String? = null,
    val status: String? = null,
    val loadError: String? = null,
) {
    val canSave: Boolean
        get() = dirty && loadError == null && problems.none { it.severity == SubsystemProblemSeverity.ERROR }

    val canGenerate: Boolean
        get() = !dirty && draft != null && loadError == null &&
            generationPhase != AresGenerationPhase.RUNNING &&
            problems.none { it.severity == SubsystemProblemSeverity.ERROR }

    val hasProtectedUserOwnedConflict: Boolean
        get() = previewFiles.any { it.change == SubsystemFileChange.PROTECTED_USER_OWNED }
}

/**
 * Project-backed subsystem editor. GUI documents and hand-authored subsystem DSL use the same
 * shared model, so students can move between visual, DSL, and fully custom IO levels safely.
 */
class SubsystemGeneratorViewModel(
    projectPath: String,
    private val league: League,
    private val documents: AresProjectDocuments = AresProjectDocuments(),
    private val projectGenerator: AresProjectGenerator? = null,
) : AutoCloseable {
    private val platform = when (league) {
        League.FTC -> SubsystemPlatform.FTC
        League.FRC -> SubsystemPlatform.FRC
    }
    private val basePackage = when (league) {
        League.FTC -> "org.firstinspires.ftc.teamcode.subsystems"
        League.FRC -> "com.areslib.frc.subsystems"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(SubsystemGeneratorState(projectPath, league))
    val state: StateFlow<SubsystemGeneratorState> = _state.asStateFlow()

    init {
        projectGenerator?.let { generator ->
            scope.launch {
                generator.aresGenerationState.collect { generation ->
                    _state.update {
                        it.copy(
                            generationPhase = generation.phase,
                            generationMessage = generation.message.ifBlank { null },
                            generatedContentHash = generation.contentHash,
                        )
                    }
                }
            }
        }
        reload()
    }

    fun reload() {
        val current = _state.value
        if (current.projectPath.isBlank()) {
            _state.value = current.copy(loadError = "Choose a robot project directory to edit subsystems.")
            return
        }
        runCatching { documents.load(current.projectPath) }
            .onSuccess { snapshot ->
                val matching = snapshot.subsystems.filter { it.platform == platform }
                val first = matching.firstOrNull() ?: defaultDocument()
                val projectProblems = snapshot.diagnostics.filter {
                    it.kind == ProjectDocumentKind.SUBSYSTEM || it.kind == ProjectDocumentKind.PROJECT_METADATA
                }.map { SubsystemProblem(SubsystemProblemSeverity.WARNING, "project:${it.file.name}", it.message) }
                _state.value = current.copy(
                    documents = if (matching.isEmpty()) listOf(first) else matching,
                    selectedDocumentId = first.documentId,
                    draft = first,
                    selectedHardwareId = first.hardware.firstOrNull()?.hardwareId,
                    selectedFieldId = null,
                    selectedLoopId = null,
                    selectedTemplate = first.template,
                    dirty = matching.isEmpty(),
                    status = if (matching.isEmpty()) "Create your first subsystem, then save it as a project revision." else null,
                    loadError = null,
                ).revalidated(projectProblems)
            }
            .onFailure { error ->
                _state.value = current.copy(loadError = error.message ?: "Subsystem documents could not be loaded.")
            }
    }

    fun newSubsystem() {
        val used = _state.value.documents.mapTo(hashSetOf()) { it.documentId }
        var suffix = 1
        var id = "new-subsystem"
        while (id in used) id = "new-subsystem-${++suffix}"
        val name = if (suffix == 1) "NewSubsystem" else "NewSubsystem$suffix"
        val document = SubsystemTemplates.create(_state.value.selectedTemplate, id, name, platform)
        _state.update { current ->
            current.copy(
                documents = current.documents + document,
                selectedDocumentId = document.documentId,
                draft = document,
                selectedHardwareId = document.hardware.first().hardwareId,
                selectedFieldId = null,
                selectedLoopId = null,
                activeStage = SubsystemBuilderStage.PURPOSE,
                selectedTemplate = document.template,
                dirty = true,
                status = "New subsystem draft created."
            ).revalidated()
        }
    }

    fun selectTemplate(template: SubsystemTemplate) = _state.update { it.copy(selectedTemplate = template) }

    fun selectStage(stage: SubsystemBuilderStage) = _state.update { it.copy(activeStage = stage) }

    fun previousStage() = _state.update { state ->
        val stages = SubsystemBuilderStage.entries
        state.copy(activeStage = stages[(state.activeStage.ordinal - 1).coerceAtLeast(0)])
    }

    fun registerHandAuthoredSubsystem() {
        val used = _state.value.documents.mapTo(hashSetOf()) { it.documentId }
        var suffix = 1
        var id = "existing-subsystem"
        while (id in used) id = "existing-subsystem-${++suffix}"
        val name = if (suffix == 1) "ExistingSubsystem" else "ExistingSubsystem$suffix"
        val packageName = "$basePackage.${id.replace('-', '_')}"
        val sourceRoot = when (league) {
            League.FTC -> "TeamCode/src/main/java"
            League.FRC -> "src/main/kotlin"
        }
        val document = SubsystemTemplates.create(SubsystemTemplate.ADVANCED_CUSTOM, id, name, platform).copy(
            generateMockIo = false,
            generateTest = false,
            implementation = SubsystemImplementationDocument(
                kind = SubsystemImplementationKind.HAND_AUTHORED,
                ownership = SubsystemSourceOwnership.USER_OWNED,
                modulePath = if (league == League.FTC) ":TeamCode" else ":",
                sourceFiles = listOf("$sourceRoot/${packageName.replace('.', '/')}/${name}Subsystem.kt"),
                subsystemClassName = "$packageName.${name}Subsystem",
                ioContractClassName = "$packageName.${name}IO",
                hardwareAdapterClassName = "$packageName.${if (league == League.FTC) "Ftc" else "Frc"}${name}IO",
                simulation = SubsystemSimulationDocument(SubsystemSimulationSupport.UNAVAILABLE),
                teaching = SubsystemTeachingDocument(
                    level = SubsystemTeachingLevel.INTERMEDIATE,
                    summary = "Existing team-owned subsystem registered with ARES.",
                ),
            ),
        )
        _state.update { current ->
            current.copy(
                documents = current.documents + document,
                selectedDocumentId = id,
                draft = document,
                selectedHardwareId = null,
                selectedFieldId = null,
                selectedLoopId = null,
                activeStage = SubsystemBuilderStage.PURPOSE,
                selectedTemplate = SubsystemTemplate.ADVANCED_CUSTOM,
                dirty = true,
                status = "Hand-authored subsystem registration created. Review its source and runtime contract.",
            ).revalidated()
        }
    }

    fun nextStage() = _state.update { state ->
        val stages = SubsystemBuilderStage.entries
        state.copy(activeStage = stages[(state.activeStage.ordinal + 1).coerceAtMost(stages.lastIndex)])
    }

    fun setGeneratedPlumbingExpanded(expanded: Boolean) = _state.update {
        it.copy(generatedPlumbingExpanded = expanded)
    }

    fun selectDocument(documentId: String) {
        _state.update { current ->
            if (current.dirty) return@update current.copy(status = "Save or reload the current draft before switching subsystems.")
            val document = current.documents.firstOrNull { it.documentId == documentId } ?: return@update current
            current.copy(
                selectedDocumentId = document.documentId,
                draft = document,
                selectedHardwareId = document.hardware.firstOrNull()?.hardwareId,
                selectedFieldId = null,
                selectedLoopId = null,
                activeStage = SubsystemBuilderStage.PURPOSE,
                selectedTemplate = document.template,
                status = null,
            ).revalidated()
        }
    }

    fun edit(transform: (SubsystemDocument) -> SubsystemDocument) {
        _state.update { current ->
            val draft = current.draft ?: return@update current
            current.copy(draft = transform(draft), dirty = true, status = null).revalidated()
        }
    }

    fun selectHardware(id: String?) = _state.update { it.copy(selectedHardwareId = id, selectedFieldId = null, selectedLoopId = null) }
    fun selectField(id: String?) = _state.update { it.copy(selectedFieldId = id, selectedHardwareId = null, selectedLoopId = null) }
    fun selectLoop(id: String?) = _state.update { it.copy(selectedLoopId = id, selectedHardwareId = null, selectedFieldId = null) }

    fun addHardware() {
        val id = uniqueId("device", _state.value.draft?.hardware.orEmpty().map { it.hardwareId })
        edit { document ->
        val device = SubsystemHardwareDocument(
            hardwareId = id,
            displayName = "New device",
            kind = SubsystemHardwareKind.DIGITAL_INPUT,
            connection = when (platform) {
                SubsystemPlatform.FTC -> SubsystemHardwareConnection(hardwareMapName = id)
                SubsystemPlatform.FRC -> SubsystemHardwareConnection(channel = nextChannel(document))
            },
        )
        document.copy(hardware = document.hardware + device)
        }
        selectHardware(id)
    }

    fun removeHardware(id: String) = edit { document ->
        document.copy(
            hardware = document.hardware.filterNot { it.hardwareId == id },
            controlLoops = document.controlLoops.filterNot { it.actuatorId == id },
        )
    }.also { selectHardware(null) }

    fun updateHardware(id: String, transform: (SubsystemHardwareDocument) -> SubsystemHardwareDocument) = edit { document ->
        document.copy(hardware = document.hardware.map { if (it.hardwareId == id) transform(it) else it })
    }

    fun addStateField() {
        val id = uniqueId("value", _state.value.draft?.stateFields.orEmpty().map { it.fieldId })
        edit { document ->
        val field = SubsystemStateFieldDocument(
            fieldId = id,
            displayName = "New value",
            type = SubsystemValueType.DOUBLE,
            role = SubsystemFieldRole.STATUS,
            defaultNumber = 0.0,
        )
        document.copy(stateFields = document.stateFields + field)
        }
        selectField(id)
    }

    fun removeStateField(id: String) = edit { document ->
        document.copy(
            stateFields = document.stateFields.filterNot { it.fieldId == id },
            hardware = document.hardware.map {
                it.copy(measurements = it.measurements.filterNot { measurement -> measurement.fieldId == id })
            },
            controlLoops = document.controlLoops.filterNot { it.targetFieldId == id || it.measurementFieldId == id },
        )
    }.also { selectField(null) }

    fun updateStateField(id: String, transform: (SubsystemStateFieldDocument) -> SubsystemStateFieldDocument) = edit { document ->
        document.copy(stateFields = document.stateFields.map { if (it.fieldId == id) transform(it) else it })
    }

    fun addControlLoop() {
        val current = _state.value.draft ?: return
        val actuator = current.hardware.firstOrNull { it.kind.isActuator() } ?: return
        val target = current.stateFields.firstOrNull { it.role == SubsystemFieldRole.TARGET && it.type.isNumeric() } ?: return
        val id = uniqueId("control", current.controlLoops.map { it.loopId })
        edit { document ->
        val actuator = document.hardware.firstOrNull { it.kind.isActuator() } ?: return@edit document
        val target = document.stateFields.firstOrNull { it.role == SubsystemFieldRole.TARGET && it.type.isNumeric() }
            ?: return@edit document
        val measurement = document.stateFields.firstOrNull { it.role == SubsystemFieldRole.MEASUREMENT && it.type.isNumeric() }
        val strategy = when {
            actuator.kind == SubsystemHardwareKind.POSITIONAL_SERVO -> SubsystemControlStrategy.SERVO_POSITION
            measurement != null -> SubsystemControlStrategy.POSITION_PID
            else -> SubsystemControlStrategy.DIRECT
        }
        val loop = SubsystemControlLoopDocument(
            loopId = id,
            displayName = "New control",
            strategy = strategy,
            actuatorId = actuator.hardwareId,
            targetFieldId = target.fieldId,
            measurementFieldId = if (strategy.requiresMeasurement()) measurement?.fieldId else null,
            minimumOutput = if (actuator.kind == SubsystemHardwareKind.MOTOR) -12.0 else -1.0,
            maximumOutput = if (actuator.kind == SubsystemHardwareKind.MOTOR) 12.0 else 1.0,
        )
        document.copy(controlLoops = document.controlLoops + loop)
        }
        selectLoop(id)
    }

    fun removeControlLoop(id: String) = edit { document ->
        document.copy(controlLoops = document.controlLoops.filterNot { it.loopId == id })
    }.also { selectLoop(null) }

    fun updateControlLoop(id: String, transform: (SubsystemControlLoopDocument) -> SubsystemControlLoopDocument) = edit { document ->
        document.copy(controlLoops = document.controlLoops.map { if (it.loopId == id) transform(it) else it })
    }

    fun save(generateAfterSave: Boolean = false) {
        val current = _state.value
        val draft = current.draft ?: return
        if (!current.canSave) {
            _state.update { it.copy(status = "Fix validation errors before saving.") }
            return
        }
        runCatching { documents.subsystems.save(current.projectPath, draft) }
            .onSuccess { saved ->
                _state.update { state ->
                    val persisted = saved.document
                    state.copy(
                        documents = state.documents.filterNot { it.documentId == persisted.documentId } + persisted,
                        selectedDocumentId = persisted.documentId,
                        draft = persisted,
                        dirty = false,
                        status = "Saved revision ${persisted.revision} (${saved.contentHash.take(12)}…).",
                    ).revalidated()
                }
                if (generateAfterSave) projectGenerator?.generateAresProject(current.projectPath, current.league)
            }
            .onFailure { error -> _state.update { it.copy(status = error.message ?: "Subsystem could not be saved.") } }
    }

    fun generate() {
        if (_state.value.dirty) save()
        val current = _state.value
        if (current.dirty) return
        if (current.hasProtectedUserOwnedConflict) {
            _state.update {
                it.copy(status = "Generation stopped: a USER-OWNED file differs from the preview and cannot be replaced.")
            }
            return
        }
        val replacements = current.previewFiles.filter { it.change == SubsystemFileChange.REPLACE_STARTER }
        if (replacements.isNotEmpty()) {
            _state.update { it.copy(pendingStarterReplacements = replacements, status = null) }
            return
        }
        projectGenerator?.applySubsystemStarters(current.projectPath, current.league)
    }

    fun cancelStarterReplacement() = _state.update { it.copy(pendingStarterReplacements = emptyList()) }

    fun confirmStarterReplacement() {
        val current = _state.value
        val token = current.starterConfirmationToken
        if (current.pendingStarterReplacements.isEmpty() || token == null) return
        _state.update { it.copy(pendingStarterReplacements = emptyList(), starterConfirmationToken = null) }
        runCatching { projectGenerator?.applySubsystemStarters(current.projectPath, current.league, token) }
            .onFailure { error ->
                _state.update { it.copy(status = error.message ?: "The starter proposal changed; review it again.") }
            }
    }

    private fun SubsystemGeneratorState.revalidated(
        external: List<SubsystemProblem> = problems.filter { it.path.startsWith("project:") },
    ): SubsystemGeneratorState {
        val document = draft ?: return copy(previewFiles = emptyList(), problems = external)
        val validation = validateSubsystemDocument(document).map {
            SubsystemProblem(SubsystemProblemSeverity.ERROR, it.path, it.message)
        }
        val generated = if (validation.isEmpty() && document.implementation.kind == SubsystemImplementationKind.GENERATED_STARTER) {
            val sourceFiles = SubsystemKotlinGenerator.generate(document, SubsystemKotlinCodegenTarget(platform, basePackage))
            val starterPlan = SubsystemStarterReconciler.plan(starterRoot().toPath(), sourceFiles)
            val starterChanges = starterPlan.changes.associateBy { it.relativePath }
            sourceFiles.map { file ->
                val destination = artifactDestination(file.relativePath, file.sourceSet, file.ownership)
                val existing = safeExistingFile(destination)?.takeIf(File::isFile)?.readText()
                val planned = starterChanges[file.relativePath.replace('\\', '/')]
                val change = when (planned?.kind) {
                    SubsystemStarterChangeKind.ADD -> SubsystemFileChange.CREATE
                    SubsystemStarterChangeKind.UNCHANGED -> SubsystemFileChange.UNCHANGED
                    SubsystemStarterChangeKind.REPLACE -> SubsystemFileChange.REPLACE_STARTER
                    SubsystemStarterChangeKind.PROTECTED -> SubsystemFileChange.PROTECTED_USER_OWNED
                    null -> when {
                        existing == null -> SubsystemFileChange.CREATE
                        existing == file.content -> SubsystemFileChange.UNCHANGED
                        file.ownership == SubsystemArtifactOwnership.USER_OWNED -> SubsystemFileChange.PROTECTED_USER_OWNED
                        else -> SubsystemFileChange.UPDATE_GENERATED
                    }
                }
                SubsystemPreviewFile(
                    path = file.relativePath,
                    sourceSet = file.sourceSet,
                    content = file.content,
                    artifact = file.artifact,
                    group = file.group,
                    ownership = file.ownership,
                    description = file.description,
                    moduleName = if (league == League.FTC) "ARES-FTC · :TeamCode" else "ARES-FRC · root",
                    projectRelativePath = destination,
                    change = change,
                    diff = planned?.diff?.takeIf(String::isNotBlank)?.let(::parseUnifiedDiff)
                        ?: existing?.takeIf { it != file.content }?.let { structuredLineDiff(it, file.content) }.orEmpty(),
                )
            }
        } else emptyList()
        val token = if (validation.isEmpty() && document.implementation.kind == SubsystemImplementationKind.GENERATED_STARTER) {
            val sources = SubsystemKotlinGenerator.generate(document, SubsystemKotlinCodegenTarget(platform, basePackage))
            SubsystemStarterReconciler.plan(starterRoot().toPath(), sources).confirmationToken
        } else null
        return copy(
            previewFiles = generated,
            starterConfirmationToken = token,
            problems = (external + validation + safetyWarnings(document))
                .distinctBy { Triple(it.severity, it.path, it.message) },
        )
    }

    private fun defaultDocument(id: String = "new-subsystem", name: String = "NewSubsystem"): SubsystemDocument {
        return SubsystemTemplates.create(SubsystemTemplate.POSITION_CONTROLLED_MECHANISM, id, name, platform)
    }

    private fun artifactDestination(
        relativePath: String,
        sourceSet: GeneratedSubsystemSourceSet,
        ownership: SubsystemArtifactOwnership,
    ): String {
        val packagePath = basePackage.replace('.', '/')
        val sourceKind = if (sourceSet == GeneratedSubsystemSourceSet.TEST) "test" else "main"
        val root = when {
            ownership == SubsystemArtifactOwnership.GENERATED_DO_NOT_EDIT && league == League.FTC ->
                "TeamCode/build/generated/ares/$sourceKind/kotlin/$packagePath"
            ownership == SubsystemArtifactOwnership.GENERATED_DO_NOT_EDIT ->
                "build/generated/ares/$sourceKind/kotlin/$packagePath"
            league == League.FTC && sourceSet == GeneratedSubsystemSourceSet.TEST -> "TeamCode/src/test/java/$packagePath"
            league == League.FTC -> "TeamCode/src/main/java/$packagePath"
            sourceSet == GeneratedSubsystemSourceSet.TEST -> "src/test/kotlin/$packagePath"
            else -> "src/main/kotlin/$packagePath"
        }
        return "$root/${relativePath.replace('\\', '/')}"
    }

    private fun safeExistingFile(projectRelativePath: String): File? {
        val root = File(_state.value.projectPath).canonicalFile
        val candidate = File(root, projectRelativePath).canonicalFile
        return candidate.takeIf { it.toPath().startsWith(root.toPath()) }
    }

    private fun starterRoot(): File {
        val relative = if (league == League.FTC) {
            "TeamCode/src/main/java/${basePackage.replace('.', '/')}"
        } else {
            "src/main/kotlin/${basePackage.replace('.', '/')}"
        }
        val root = File(_state.value.projectPath).canonicalFile
        return File(root, relative).canonicalFile.also {
            require(it.toPath().startsWith(root.toPath())) { "Subsystem starter root escaped the project" }
        }
    }

    override fun close() = scope.cancel()

    private companion object {
        fun uniqueId(base: String, used: List<String>): String {
            if (base !in used) return base
            var suffix = 2
            while ("$base$suffix" in used) suffix++
            return "$base$suffix"
        }

        fun nextChannel(document: SubsystemDocument): Int {
            val used = document.hardware.mapNotNullTo(hashSetOf()) { it.connection.channel }
            return (0..31).firstOrNull { it !in used } ?: 0
        }
    }
}

private fun safetyWarnings(document: SubsystemDocument): List<SubsystemProblem> = buildList {
    fun warn(path: String, message: String) = add(SubsystemProblem(SubsystemProblemSeverity.WARNING, path, message))
    val hasActuators = document.hardware.any { it.kind.isActuator() }
    if (!hasActuators) return@buildList
    if (!document.safety.requiresConfigurationHealth) {
        warn("safety.requiresConfigurationHealth", "Configuration health is not gating actuator output.")
    }
    if (!document.safety.latchOutputFaults) {
        warn("safety.latchOutputFaults", "Failed output writes will not latch a fault; verify this is intentional.")
    }
    if (!document.safety.requiresExplicitNeutralRecovery) {
        warn("safety.requiresExplicitNeutralRecovery", "Fault recovery does not require a successful explicit neutral command.")
    }
    if (!document.safety.zeroAllocationPeriodic) {
        warn("safety.zeroAllocationPeriodic", "The periodic-path zero-allocation contract is disabled.")
    }
    if (!document.safety.telemetryEnabled) {
        warn("safety.telemetryEnabled", "Safety telemetry is disabled, reducing pit-side fault visibility.")
    }
    if (document.safety.requiresCurrentMonitoring && document.hardware.none { device ->
            device.measurements.any { it.source == SubsystemMeasurementSource.MOTOR_CURRENT_AMPS }
        }
    ) {
        warn("safety.requiresCurrentMonitoring", "Current monitoring is required but no cached current measurement is configured.")
    }
}

private fun SubsystemHardwareKind.isActuator(): Boolean = this == SubsystemHardwareKind.MOTOR ||
    this == SubsystemHardwareKind.POSITIONAL_SERVO || this == SubsystemHardwareKind.CONTINUOUS_SERVO

private fun SubsystemValueType.isNumeric(): Boolean = this == SubsystemValueType.DOUBLE || this == SubsystemValueType.INT

private fun SubsystemControlStrategy.requiresMeasurement(): Boolean = this == SubsystemControlStrategy.POSITION_PID ||
    this == SubsystemControlStrategy.VELOCITY_PID || this == SubsystemControlStrategy.BANG_BANG

/**
 * Small deterministic line diff for starter replacement review. Common context is intentionally
 * bounded so a large generated file cannot bury the customization that would be replaced.
 */
internal fun structuredLineDiff(existing: String, proposed: String, contextLines: Int = 3): List<SubsystemDiffLine> {
    val before = existing.lines()
    val after = proposed.lines()
    var prefix = 0
    while (prefix < before.size && prefix < after.size && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (
        suffix < before.size - prefix && suffix < after.size - prefix &&
        before[before.lastIndex - suffix] == after[after.lastIndex - suffix]
    ) suffix++

    val leadingStart = (prefix - contextLines.coerceAtLeast(0)).coerceAtLeast(0)
    val trailingCount = suffix.coerceAtMost(contextLines.coerceAtLeast(0))
    return buildList {
        before.subList(leadingStart, prefix).forEach { add(SubsystemDiffLine(SubsystemDiffLineKind.CONTEXT, it)) }
        before.subList(prefix, before.size - suffix).forEach { add(SubsystemDiffLine(SubsystemDiffLineKind.REMOVED, it)) }
        after.subList(prefix, after.size - suffix).forEach { add(SubsystemDiffLine(SubsystemDiffLineKind.ADDED, it)) }
        if (trailingCount > 0) {
            after.subList(after.size - suffix, after.size - suffix + trailingCount)
                .forEach { add(SubsystemDiffLine(SubsystemDiffLineKind.CONTEXT, it)) }
        }
    }
}

internal fun parseUnifiedDiff(diff: String): List<SubsystemDiffLine> = diff.lineSequence()
    .filterNot { it.startsWith("@@") }
    .map { line ->
        when {
            line.startsWith("+") -> SubsystemDiffLine(SubsystemDiffLineKind.ADDED, line.drop(1))
            line.startsWith("-") -> SubsystemDiffLine(SubsystemDiffLineKind.REMOVED, line.drop(1))
            else -> SubsystemDiffLine(SubsystemDiffLineKind.CONTEXT, line.removePrefix(" "))
        }
    }
    .toList()
