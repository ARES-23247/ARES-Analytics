package com.ares.analytics.viewmodel

import com.ares.analytics.service.AresGenerationPhase
import com.ares.analytics.service.AresProjectGenerator
import com.ares.analytics.shared.League
import com.ares.analytics.viewmodel.project.AresProjectDocuments
import com.ares.analytics.viewmodel.project.ProjectDocumentKind
import com.areslib.codegen.GeneratedSubsystemSourceSet
import com.areslib.codegen.SubsystemKotlinCodegenTarget
import com.areslib.codegen.SubsystemKotlinGenerator
import com.areslib.subsystem.SubsystemControlLoopDocument
import com.areslib.subsystem.SubsystemControlStrategy
import com.areslib.subsystem.SubsystemDocument
import com.areslib.subsystem.SubsystemFieldRole
import com.areslib.subsystem.SubsystemHardwareConnection
import com.areslib.subsystem.SubsystemHardwareDocument
import com.areslib.subsystem.SubsystemHardwareKind
import com.areslib.subsystem.SubsystemMeasurementSource
import com.areslib.subsystem.SubsystemPlatform
import com.areslib.subsystem.SubsystemStateFieldDocument
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

enum class SubsystemProblemSeverity { WARNING, ERROR }

data class SubsystemProblem(
    val severity: SubsystemProblemSeverity,
    val path: String,
    val message: String,
)

data class SubsystemPreviewFile(
    val path: String,
    val sourceSet: GeneratedSubsystemSourceSet,
    val content: String,
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
    val previewFiles: List<SubsystemPreviewFile> = emptyList(),
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
}

/**
 * Project-backed subsystem editor. GUI documents and hand-authored subsystem DSL use the same
 * shared model, so students can move between visual, DSL, and fully custom IO levels safely.
 */
class SubsystemGeneratorViewModel(
    projectPath: String,
    league: League,
    private val documents: AresProjectDocuments = AresProjectDocuments(),
    private val projectGenerator: AresProjectGenerator? = null,
) : AutoCloseable {
    private val platform = when (league) {
        League.FTC -> SubsystemPlatform.FTC
        League.FRC -> SubsystemPlatform.FRC
    }
    private val basePackage = when (league) {
        League.FTC -> "org.firstinspires.ftc.teamcode.generated.subsystems"
        League.FRC -> "com.areslib.frc.generated.subsystems"
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
        val document = defaultDocument(id, if (suffix == 1) "NewSubsystem" else "NewSubsystem$suffix")
        _state.update { current ->
            current.copy(
                documents = current.documents + document,
                selectedDocumentId = document.documentId,
                draft = document,
                selectedHardwareId = document.hardware.first().hardwareId,
                selectedFieldId = null,
                selectedLoopId = null,
                dirty = true,
                status = "New subsystem draft created."
            ).revalidated()
        }
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
                if (it.measurementFieldId == id) {
                    it.copy(
                        measurementFieldId = null,
                        measurementSource = null,
                        measurementScale = 1.0,
                        measurementOffset = 0.0,
                    )
                } else {
                    it
                }
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
        if (_state.value.dirty) {
            save(generateAfterSave = true)
        } else if (_state.value.canGenerate) {
            projectGenerator?.generateAresProject(_state.value.projectPath, _state.value.league)
        }
    }

    private fun SubsystemGeneratorState.revalidated(
        external: List<SubsystemProblem> = problems.filter { it.path.startsWith("project:") },
    ): SubsystemGeneratorState {
        val document = draft ?: return copy(previewFiles = emptyList(), problems = external)
        val validation = validateSubsystemDocument(document).map {
            SubsystemProblem(SubsystemProblemSeverity.ERROR, it.path, it.message)
        }
        val generated = if (validation.isEmpty()) {
            SubsystemKotlinGenerator.generate(document, SubsystemKotlinCodegenTarget(platform, basePackage)).map {
                SubsystemPreviewFile(it.relativePath, it.sourceSet, it.content)
            }
        } else emptyList()
        return copy(
            previewFiles = generated,
            problems = (external + validation).distinctBy { Triple(it.severity, it.path, it.message) },
        )
    }

    private fun defaultDocument(id: String = "new-subsystem", name: String = "NewSubsystem"): SubsystemDocument {
        val target = SubsystemStateFieldDocument(
            "target", "Target", SubsystemValueType.DOUBLE, SubsystemFieldRole.TARGET,
            defaultNumber = 0.0,
        )
        val position = SubsystemStateFieldDocument(
            "position", "Position", SubsystemValueType.DOUBLE, SubsystemFieldRole.MEASUREMENT,
            defaultNumber = 0.0,
        )
        val motor = SubsystemHardwareDocument(
            "motor", "Motor", SubsystemHardwareKind.MOTOR,
            connection = when (platform) {
                SubsystemPlatform.FTC -> SubsystemHardwareConnection(hardwareMapName = "motor")
                SubsystemPlatform.FRC -> SubsystemHardwareConnection(canId = 1)
            },
            measurementFieldId = position.fieldId,
            measurementSource = SubsystemMeasurementSource.MOTOR_POSITION_NATIVE,
        )
        return SubsystemDocument(
            documentId = id,
            name = name,
            description = "A generated mechanism that remains editable through the ARES subsystem DSL.",
            platform = platform,
            hardware = listOf(motor),
            stateFields = listOf(target, position),
            controlLoops = listOf(
                SubsystemControlLoopDocument(
                    "controller", "Position controller", SubsystemControlStrategy.POSITION_PID,
                    actuatorId = motor.hardwareId,
                    targetFieldId = target.fieldId,
                    measurementFieldId = position.fieldId,
                    kP = 1.0,
                )
            ),
        )
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

private fun SubsystemHardwareKind.isActuator(): Boolean = this == SubsystemHardwareKind.MOTOR ||
    this == SubsystemHardwareKind.POSITIONAL_SERVO || this == SubsystemHardwareKind.CONTINUOUS_SERVO

private fun SubsystemValueType.isNumeric(): Boolean = this == SubsystemValueType.DOUBLE || this == SubsystemValueType.INT

private fun SubsystemControlStrategy.requiresMeasurement(): Boolean = this == SubsystemControlStrategy.POSITION_PID ||
    this == SubsystemControlStrategy.VELOCITY_PID || this == SubsystemControlStrategy.BANG_BANG
