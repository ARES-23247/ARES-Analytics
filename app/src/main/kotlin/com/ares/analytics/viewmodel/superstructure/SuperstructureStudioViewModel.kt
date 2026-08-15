package com.ares.analytics.viewmodel.superstructure

import com.ares.analytics.viewmodel.project.AresProjectDocuments
import com.ares.analytics.viewmodel.project.ProjectDocumentDiagnostic
import com.ares.analytics.viewmodel.project.SuperstructureProjectRepository
import com.areslib.catalog.ActionDescriptor
import com.areslib.subsystem.SubsystemDocument
import com.areslib.subsystem.SubsystemFieldRole
import com.areslib.subsystem.InterlockComparison
import com.areslib.subsystem.SubsystemImplementationKind
import com.areslib.subsystem.SubsystemStateFieldDocument
import com.areslib.subsystem.SubsystemValueType
import com.areslib.superstructure.LutControlPoint
import com.areslib.superstructure.LutInterpolationMethod
import com.areslib.superstructure.StateTransitionEdge
import com.areslib.superstructure.SuperstructureDocument
import com.areslib.superstructure.SuperstructureDocumentCodec
import com.areslib.superstructure.SuperstructureDynamicLut
import com.areslib.superstructure.SuperstructureFieldReference
import com.areslib.superstructure.SuperstructureInterlockRule
import com.areslib.superstructure.SuperstructureIssueSeverity
import com.areslib.superstructure.SuperstructureStatePreset
import com.areslib.superstructure.SuperstructureSubsystemTarget
import com.areslib.superstructure.SuperstructureTargetMode
import com.areslib.superstructure.TransitionTriggerKind
import com.areslib.superstructure.validateSuperstructureProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SuperstructureStudioStep {
    OVERVIEW,
    STATE_PRESETS,
    TRANSITIONS,
    INTERLOCKS,
    LOOKUP_TABLES,
    REVIEW,
}

data class SuperstructureFieldOption(
    val subsystem: SubsystemDocument,
    val field: SubsystemStateFieldDocument,
) {
    val reference: SuperstructureFieldReference = SuperstructureFieldReference(subsystem.documentId, field.fieldId)
    val label: String = "${subsystem.displayName} · ${field.displayName}${field.unit?.let { " ($it)" }.orEmpty()}"
}

data class SuperstructureSaveReview(
    val expectedContentHash: String?,
    val candidateContentHash: String,
    val confirmationToken: String,
    val summary: List<String>,
)

data class SuperstructureStudioState(
    val projectPath: String,
    val documents: List<SuperstructureDocument> = emptyList(),
    val selectedId: String? = null,
    val saved: SuperstructureDocument? = null,
    val savedContentHash: String? = null,
    val draft: SuperstructureDocument? = null,
    val subsystems: List<SubsystemDocument> = emptyList(),
    val actions: List<ActionDescriptor> = emptyList(),
    val diagnostics: List<ProjectDocumentDiagnostic> = emptyList(),
    val step: SuperstructureStudioStep = SuperstructureStudioStep.OVERVIEW,
    val selectedStateId: String? = null,
    val validationErrors: List<String> = emptyList(),
    val validationWarnings: List<String> = emptyList(),
    val editorErrors: Map<String, String> = emptyMap(),
    val review: SuperstructureSaveReview? = null,
    val loading: Boolean = true,
    val dirty: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val pendingSelectionId: String? = null,
) {
    val generatedSubsystems: List<SubsystemDocument>
        get() = subsystems.filter { it.implementation.kind == SubsystemImplementationKind.GENERATED_STARTER }
    val targetFields: List<SuperstructureFieldOption>
        get() = generatedSubsystems.flatMap { subsystem ->
            subsystem.stateFields.filter { it.role == SubsystemFieldRole.TARGET }
                .map { SuperstructureFieldOption(subsystem, it) }
        }
    val sourceFields: List<SuperstructureFieldOption>
        get() = generatedSubsystems.flatMap { subsystem ->
            subsystem.stateFields.map { SuperstructureFieldOption(subsystem, it) }
        }
    val parameterlessActions: List<ActionDescriptor>
        get() = actions.filter { it.parameters.isEmpty() }
    val canSave: Boolean
        get() = draft != null && dirty && validationErrors.isEmpty() && editorErrors.isEmpty() && review == null
}

class SuperstructureStudioViewModel(
    projectPath: String,
    private val scope: CoroutineScope,
    private val projectDocuments: AresProjectDocuments = AresProjectDocuments(),
    private val repository: SuperstructureProjectRepository = projectDocuments.superstructures,
) {
    private val _state = MutableStateFlow(SuperstructureStudioState(projectPath = projectPath))
    val state: StateFlow<SuperstructureStudioState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload(force: Boolean = false) {
        if (_state.value.dirty && !force) {
            _state.update { it.copy(pendingSelectionId = it.selectedId, error = "Choose Reload again after discarding or save the current draft first.") }
            return
        }
        scope.launch {
            _state.update { it.copy(loading = true, error = null, status = "") }
            val result = withContext(Dispatchers.IO) { runCatching { projectDocuments.load(_state.value.projectPath) } }
            result.onSuccess { snapshot ->
                val selected = snapshot.superstructures.firstOrNull { it.superstructureId == _state.value.selectedId }
                    ?: snapshot.superstructures.firstOrNull()
                _state.value = validate(
                    _state.value.copy(
                        documents = snapshot.superstructures,
                        selectedId = selected?.superstructureId,
                        saved = selected,
                        savedContentHash = selected?.let(SuperstructureDocumentCodec::contentHash),
                        draft = selected,
                        subsystems = snapshot.subsystems,
                        actions = snapshot.capabilityCatalog?.actions.orEmpty(),
                        diagnostics = snapshot.diagnostics,
                        selectedStateId = selected?.initialStateId,
                        loading = false,
                        dirty = false,
                        review = null,
                        pendingSelectionId = null,
                        editorErrors = emptyMap(),
                    )
                )
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "Project documents could not be loaded") }
            }
        }
    }

    fun create(rawId: String, displayName: String) {
        val id = rawId.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
        if (_state.value.dirty) {
            _state.update { it.copy(error = "Save or discard the current draft before creating another coordinator.") }
            return
        }
        if (!id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}"))) {
            _state.update { it.copy(error = "Use a short ID containing lowercase letters, numbers, and hyphens.") }
            return
        }
        if (_state.value.documents.any { it.superstructureId == id }) {
            select(id)
            return
        }
        val initial = SuperstructureStatePreset("idle", "Idle", "Safe neutral starting posture")
        val fault = SuperstructureStatePreset("fault", "Fault / neutral", "Fail-closed neutral posture")
        val draft = SuperstructureDocument(
            superstructureId = id,
            displayName = displayName.trim().ifBlank { id.replace('-', ' ').replaceFirstChar(Char::uppercase) },
            description = "Coordinates generated mechanisms through Redux without writing hardware directly.",
            initialStateId = initial.stateId,
            states = listOf(initial, fault),
            faultStateId = fault.stateId,
        )
        _state.value = validate(
            _state.value.copy(
                selectedId = id,
                saved = null,
                savedContentHash = null,
                draft = draft,
                selectedStateId = initial.stateId,
                step = SuperstructureStudioStep.OVERVIEW,
                dirty = true,
                status = "New draft created. Nothing has been written yet.",
                error = null,
                review = null,
                editorErrors = emptyMap(),
            )
        )
    }

    fun select(id: String, force: Boolean = false) {
        if (_state.value.dirty && !force && id != _state.value.selectedId) {
            _state.update { it.copy(pendingSelectionId = id) }
            return
        }
        val selected = _state.value.documents.singleOrNull { it.superstructureId == id } ?: return
        _state.value = validate(
            _state.value.copy(
                selectedId = id,
                saved = selected,
                savedContentHash = SuperstructureDocumentCodec.contentHash(selected),
                draft = selected,
                selectedStateId = selected.initialStateId,
                dirty = false,
                review = null,
                pendingSelectionId = null,
                status = "Loaded ${selected.displayName}.",
                error = null,
                editorErrors = emptyMap(),
            )
        )
    }

    fun confirmDiscard() {
        val target = _state.value.pendingSelectionId
        _state.update { it.copy(dirty = false, pendingSelectionId = null, error = null) }
        if (target != null && target != _state.value.selectedId) select(target, force = true) else reload(force = true)
    }

    fun cancelDiscard() = _state.update { it.copy(pendingSelectionId = null) }

    fun selectStep(step: SuperstructureStudioStep) = _state.update { it.copy(step = step) }
    fun selectState(stateId: String) = _state.update { it.copy(selectedStateId = stateId) }
    fun setEditorError(key: String, message: String?) = _state.update { state ->
        state.copy(editorErrors = if (message == null) state.editorErrors - key else state.editorErrors + (key to message))
    }

    fun updateMetadata(displayName: String, description: String) = edit { document ->
        document.copy(displayName = displayName.take(80), description = description.take(500))
    }

    fun addState(rawId: String, displayName: String) = edit { document ->
        val id = rawId.trim().replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_')
        require(id.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) { "State ID must start with a letter and contain only letters, digits, or underscores." }
        require(document.states.none { it.stateId == id }) { "State '$id' already exists." }
        val targets = document.states.firstOrNull()?.subsystemTargets.orEmpty().map { target ->
            neutralTarget(target.subsystemId, target.fieldId)
        }
        document.copy(states = document.states + SuperstructureStatePreset(id, displayName.ifBlank { id }, subsystemTargets = targets))
            .also { _state.update { state -> state.copy(selectedStateId = id) } }
    }

    fun removeSelectedState() = edit { document ->
        val id = _state.value.selectedStateId ?: return@edit document
        require(id != document.initialStateId) { "Choose a different initial state before removing this state." }
        require(id != document.faultStateId) { "Choose a different fault state before removing this state." }
        val remaining = document.states.filterNot { it.stateId == id }
        _state.update { it.copy(selectedStateId = remaining.firstOrNull()?.stateId) }
        document.copy(
            states = remaining.map { state ->
                if (state.timeoutTargetStateId == id) state.copy(timeoutSeconds = null, timeoutTargetStateId = null) else state
            },
            transitions = document.transitions.filterNot {
                it.sourceStateId == id || it.targetStateId == id || it.timeoutTargetStateId == id
            },
        )
    }

    fun setInitialState(id: String) = edit { it.copy(initialStateId = id) }
    fun setFaultState(id: String) = edit { document ->
        val safeState = document.states.single { it.stateId == id }.copy(
            subsystemTargets = document.states.single { it.stateId == id }.subsystemTargets.map { target ->
                neutralTarget(target.subsystemId, target.fieldId)
            }
        )
        document.copy(faultStateId = id, states = document.states.map { if (it.stateId == id) safeState else it })
    }

    fun addTarget(reference: SuperstructureFieldReference) = edit { document ->
        require(document.states.none { state -> state.subsystemTargets.any { it.subsystemId == reference.subsystemId && it.fieldId == reference.fieldId } }) {
            "That target is already part of every state."
        }
        document.copy(states = document.states.map { state ->
            state.copy(subsystemTargets = state.subsystemTargets + neutralTarget(reference.subsystemId, reference.fieldId))
        })
    }

    fun removeTarget(reference: SuperstructureFieldReference) {
        _state.update { state -> state.copy(editorErrors = state.editorErrors.filterKeys { !it.contains(":${reference.subsystemId}.${reference.fieldId}") }) }
        edit { document ->
        document.copy(states = document.states.map { state ->
            state.copy(subsystemTargets = state.subsystemTargets.filterNot {
                it.subsystemId == reference.subsystemId && it.fieldId == reference.fieldId
            })
        })
        }
    }

    fun updateSelectedTarget(target: SuperstructureSubsystemTarget) = edit { document ->
        val stateId = _state.value.selectedStateId ?: return@edit document
        require(stateId != document.faultStateId || target == neutralTarget(target.subsystemId, target.fieldId)) {
            "The fault state must retain each subsystem's declared safe neutral value."
        }
        document.copy(states = document.states.map { state ->
            if (state.stateId != stateId) state else state.copy(
                subsystemTargets = state.subsystemTargets.map { existing ->
                    if (existing.subsystemId == target.subsystemId && existing.fieldId == target.fieldId) target else existing
                }
            )
        })
    }

    fun addActionTransition(source: String, target: String, actionKey: String) = addTransition(
        StateTransitionEdge(
            transitionId = uniqueId("request-${source.lowercase()}-${target.lowercase()}"),
            sourceStateId = source,
            targetStateId = target,
            triggerKind = TransitionTriggerKind.ACTION_REQUEST,
            actionKey = actionKey,
        )
    )

    fun addSensorTransition(source: String, target: String, field: SuperstructureFieldOption) {
        val guard = typedGuard(field)
        addTransition(
            StateTransitionEdge(
                transitionId = uniqueId("auto-${source.lowercase()}-${target.lowercase()}"),
                sourceStateId = source,
                targetStateId = target,
                triggerKind = TransitionTriggerKind.SENSOR_CONDITION_AUTO,
                guards = listOf(guard),
                debounceMs = 100,
            )
        )
    }

    fun addTimedTransition(source: String, target: String, seconds: Double) = addTransition(
        StateTransitionEdge(
            transitionId = uniqueId("wait-${source.lowercase()}-${target.lowercase()}"),
            sourceStateId = source,
            targetStateId = target,
            triggerKind = TransitionTriggerKind.TIME_ELAPSED,
            timeoutSeconds = seconds,
        )
    )

    fun updateTransition(edge: StateTransitionEdge) = edit { document ->
        document.copy(transitions = document.transitions.map { if (it.transitionId == edge.transitionId) edge else it })
    }
    fun removeTransition(id: String) {
        _state.update { state -> state.copy(editorErrors = state.editorErrors.filterKeys { !it.startsWith("transition:$id:") }) }
        edit { it.copy(transitions = it.transitions.filterNot { edge -> edge.transitionId == id }) }
    }

    fun addInterlock(source: SuperstructureFieldOption, constrained: SuperstructureFieldOption) = edit { document ->
        require(source.field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT)) { "Interlock evidence must be numeric." }
        require(constrained.field.role == SubsystemFieldRole.TARGET && constrained.field.type in setOf(SubsystemValueType.DOUBLE, SubsystemValueType.INT)) {
            "Interlocks can clamp only numeric target fields."
        }
        document.copy(interlocks = document.interlocks + SuperstructureInterlockRule(
            ruleId = uniqueId("limit-${constrained.subsystem.documentId}-${constrained.field.fieldId}"),
            description = "Clamp ${constrained.label} while ${source.label} is below the reviewed threshold.",
            primary = source.reference,
            constrainedSubsystemId = constrained.subsystem.documentId,
            constrainedFieldId = constrained.field.fieldId,
            conditionThreshold = 0.0,
            clampMinimum = constrained.field.minimum,
            clampMaximum = constrained.field.defaultNumber ?: constrained.field.defaultInt?.toDouble() ?: 0.0,
        ))
    }
    fun updateInterlock(rule: SuperstructureInterlockRule) = edit { document ->
        document.copy(interlocks = document.interlocks.map { if (it.ruleId == rule.ruleId) rule else it })
    }
    fun removeInterlock(id: String) {
        _state.update { state -> state.copy(editorErrors = state.editorErrors.filterKeys { !it.startsWith("interlock:$id:") }) }
        edit { it.copy(interlocks = it.interlocks.filterNot { rule -> rule.ruleId == id }) }
    }

    fun addLut() = edit { document ->
        document.copy(luts = document.luts + SuperstructureDynamicLut(
            lutId = uniqueId("lookup"),
            displayName = "New lookup table",
            interpolation = LutInterpolationMethod.LINEAR,
            controlPoints = listOf(LutControlPoint(0.0, 0.0), LutControlPoint(1.0, 1.0)),
        ))
    }
    fun updateLut(lut: SuperstructureDynamicLut) = edit { document ->
        document.copy(luts = document.luts.map { if (it.lutId == lut.lutId) lut else it })
    }
    fun removeLut(id: String) {
        _state.update { state -> state.copy(editorErrors = state.editorErrors.filterKeys { !it.startsWith("lut:$id:") }) }
        edit { it.copy(luts = it.luts.filterNot { lut -> lut.lutId == id }) }
    }

    fun reviewSave() {
        val state = validate(_state.value)
        val draft = state.draft ?: return
        if (state.validationErrors.isNotEmpty() || state.editorErrors.isNotEmpty()) {
            _state.value = state.copy(error = "Resolve the listed errors before reviewing a save.")
            return
        }
        val candidateHash = SuperstructureDocumentCodec.contentHash(draft)
        val token = "${state.savedContentHash.orEmpty()}:$candidateHash"
        val summary = listOf(
            "${draft.states.size} complete state presets",
            "${draft.transitions.size} transitions (${draft.transitions.count { it.triggerKind == TransitionTriggerKind.ACTION_REQUEST }} driver/autonomous actions)",
            "${draft.interlocks.size} cross-mechanism clamps",
            "${draft.luts.size} lookup tables",
            "Fault destination: ${draft.faultStateId}",
        )
        _state.value = state.copy(review = SuperstructureSaveReview(state.savedContentHash, candidateHash, token, summary), error = null)
    }

    fun dismissReview() = _state.update { it.copy(review = null) }

    fun confirmSave(token: String) {
        val state = _state.value
        val draft = state.draft ?: return
        val review = state.review
        if (review == null || review.confirmationToken != token || review.candidateContentHash != SuperstructureDocumentCodec.contentHash(draft)) {
            _state.update { it.copy(review = null, error = "The draft changed after review. Review it again before saving.") }
            return
        }
        scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repository.save(
                        state.projectPath,
                        draft,
                        review.expectedContentHash,
                        state.subsystems,
                        state.actions.mapTo(linkedSetOf()) { it.key },
                    )
                }
            }
            result.onSuccess { saved ->
                val documents = (state.documents.filterNot { it.superstructureId == saved.document.superstructureId } + saved.document)
                    .sortedBy { it.displayName.lowercase() }
                _state.value = validate(
                    state.copy(
                        documents = documents,
                        selectedId = saved.document.superstructureId,
                        saved = saved.document,
                        savedContentHash = saved.contentHash,
                        draft = saved.document,
                        loading = false,
                        dirty = false,
                        review = null,
                        status = "Saved ${saved.currentFile.path} and retained immutable history ${saved.historyFile.name}.",
                        error = null,
                    )
                )
            }.onFailure { error ->
                _state.update { it.copy(loading = false, review = null, error = error.message ?: "Superstructure could not be saved") }
            }
        }
    }

    private fun addTransition(edge: StateTransitionEdge) = edit { it.copy(transitions = it.transitions + edge) }

    private fun edit(transform: (SuperstructureDocument) -> SuperstructureDocument) {
        val current = _state.value.draft ?: return
        runCatching { transform(current) }
            .onSuccess { draft ->
                _state.value = validate(_state.value.copy(draft = draft, dirty = draft != _state.value.saved, review = null, status = "", error = null))
            }
            .onFailure { error -> _state.update { it.copy(error = error.message ?: "That edit is not valid") } }
    }

    private fun validate(state: SuperstructureStudioState): SuperstructureStudioState {
        val document = state.draft ?: return state.copy(validationErrors = emptyList(), validationWarnings = emptyList())
        val issues = validateSuperstructureProject(document, state.subsystems, state.actions.mapTo(linkedSetOf()) { it.key })
        return state.copy(
            validationErrors = issues.filter { it.severity == SuperstructureIssueSeverity.ERROR }.map { "${it.path}: ${it.message}" }.distinct(),
            validationWarnings = issues.filter { it.severity == SuperstructureIssueSeverity.WARNING }.map { "${it.path}: ${it.message}" }.distinct(),
        )
    }

    private fun neutralTarget(subsystemId: String, fieldId: String): SuperstructureSubsystemTarget {
        val field = _state.value.subsystems.single { it.documentId == subsystemId }.stateFields.single { it.fieldId == fieldId }
        return when (field.type) {
            SubsystemValueType.DOUBLE -> SuperstructureSubsystemTarget(subsystemId, fieldId, constantDoubleValue = field.defaultNumber ?: 0.0)
            SubsystemValueType.INT -> SuperstructureSubsystemTarget(subsystemId, fieldId, constantDoubleValue = (field.defaultInt ?: 0).toDouble())
            SubsystemValueType.BOOLEAN -> SuperstructureSubsystemTarget(subsystemId, fieldId, constantBooleanValue = field.defaultBoolean ?: false)
            SubsystemValueType.STRING -> SuperstructureSubsystemTarget(subsystemId, fieldId, constantStringValue = field.defaultText.orEmpty())
        }
    }

    private fun typedGuard(field: SuperstructureFieldOption) = when (field.field.type) {
        SubsystemValueType.DOUBLE, SubsystemValueType.INT -> com.areslib.superstructure.TransitionGuard(
            guardId = uniqueId("guard-${field.field.fieldId}"), source = field.reference, comparison = InterlockComparison.GREATER_THAN, expectedDoubleValue = 0.0
        )
        SubsystemValueType.BOOLEAN -> com.areslib.superstructure.TransitionGuard(
            guardId = uniqueId("guard-${field.field.fieldId}"), source = field.reference, expectedBooleanValue = true
        )
        SubsystemValueType.STRING -> com.areslib.superstructure.TransitionGuard(
            guardId = uniqueId("guard-${field.field.fieldId}"), source = field.reference, expectedStringValue = field.field.defaultText.orEmpty()
        )
    }

    private fun uniqueId(base: String): String {
        val normalized = base.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-').ifBlank { "item" }
        val document = _state.value.draft
        val used = buildSet {
            document?.transitions?.forEach { add(it.transitionId) }
            document?.interlocks?.forEach { add(it.ruleId) }
            document?.luts?.forEach { add(it.lutId) }
            document?.transitions?.flatMap { it.guards }?.forEach { add(it.guardId) }
        }
        if (normalized !in used) return normalized
        var suffix = 2
        while ("$normalized-$suffix" in used) suffix++
        return "$normalized-$suffix"
    }
}
