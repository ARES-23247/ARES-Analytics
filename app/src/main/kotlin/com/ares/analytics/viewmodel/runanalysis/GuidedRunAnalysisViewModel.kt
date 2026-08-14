package com.ares.analytics.viewmodel.runanalysis

import com.ares.analytics.service.GuidedRunAnalysisReport
import com.ares.analytics.service.GuidedRunAnalysisRepository
import com.ares.analytics.shared.Session
import com.ares.analytics.shared.WorkspaceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class GuidedRunAnalysisState(
    val loadingSessions: Boolean = true,
    val analyzing: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val selectedSessionId: String? = null,
    val report: GuidedRunAnalysisReport? = null,
    val error: String? = null,
    val exportMessage: String? = null,
)

/** Generation-safe state holder for the read-only guided run review. */
class GuidedRunAnalysisViewModel(
    private val service: GuidedRunAnalysisRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(GuidedRunAnalysisState())
    val state: StateFlow<GuidedRunAnalysisState> = _state.asStateFlow()

    private var workspace: WorkspaceConfig? = null
    private var loadJob: Job? = null
    private var analysisJob: Job? = null
    private var generation = 0L

    fun load(selectedWorkspace: WorkspaceConfig) {
        workspace = selectedWorkspace
        refreshSessions()
    }

    fun refreshSessions() {
        val selectedWorkspace = workspace ?: return
        val request = ++generation
        loadJob?.cancel()
        analysisJob?.cancel()
        _state.value = _state.value.copy(loadingSessions = true, analyzing = false, error = null, exportMessage = null)
        loadJob = scope.launch {
            runCatching { service.listWorkspaceSessions(selectedWorkspace) }
                .onSuccess { sessions ->
                    if (request != generation || workspace != selectedWorkspace) return@onSuccess
                    val selectedId = _state.value.selectedSessionId?.takeIf { id -> sessions.any { it.sessionId == id } }
                        ?: sessions.firstOrNull()?.sessionId
                    _state.value = _state.value.copy(
                        loadingSessions = false,
                        sessions = sessions,
                        selectedSessionId = selectedId,
                        report = null,
                        error = null,
                    )
                    selectedId?.let { analyze(it, selectedWorkspace, request) }
                }
                .onFailure { failure ->
                    if (request != generation || workspace != selectedWorkspace) return@onFailure
                    _state.value = _state.value.copy(
                        loadingSessions = false,
                        analyzing = false,
                        sessions = emptyList(),
                        report = null,
                        error = failure.actionableMessage("ARES could not list runs for this workspace"),
                    )
                }
        }
    }

    fun selectSession(sessionId: String) {
        val selectedWorkspace = workspace ?: return
        if (_state.value.sessions.none { it.sessionId == sessionId }) {
            _state.value = _state.value.copy(error = "That run is not part of the selected workspace. Refresh the run list.")
            return
        }
        val request = ++generation
        _state.value = _state.value.copy(selectedSessionId = sessionId, report = null, error = null, exportMessage = null)
        analyze(sessionId, selectedWorkspace, request)
    }

    fun refreshAnalysis() {
        val selectedWorkspace = workspace ?: return
        val sessionId = _state.value.selectedSessionId ?: return
        val request = ++generation
        analyze(sessionId, selectedWorkspace, request)
    }

    fun export(destination: File) {
        val report = _state.value.report ?: return
        _state.value = _state.value.copy(exportMessage = "Saving evidence report…")
        scope.launch {
            runCatching { service.exportMarkdown(report, destination) }
                .onSuccess {
                    _state.value = _state.value.copy(exportMessage = "Saved ${destination.name}")
                }
                .onFailure { failure ->
                    _state.value = _state.value.copy(
                        exportMessage = failure.actionableMessage("The evidence report was not saved"),
                    )
                }
        }
    }

    fun clearExportMessage() {
        _state.value = _state.value.copy(exportMessage = null)
    }

    private fun analyze(sessionId: String, selectedWorkspace: WorkspaceConfig, request: Long) {
        analysisJob?.cancel()
        _state.value = _state.value.copy(analyzing = true, report = null, error = null)
        analysisJob = scope.launch {
            runCatching { service.analyze(selectedWorkspace, sessionId) }
                .onSuccess { report ->
                    if (request != generation || workspace != selectedWorkspace || _state.value.selectedSessionId != sessionId) return@onSuccess
                    _state.value = _state.value.copy(analyzing = false, report = report, error = null)
                }
                .onFailure { failure ->
                    if (request != generation || workspace != selectedWorkspace || _state.value.selectedSessionId != sessionId) return@onFailure
                    _state.value = _state.value.copy(
                        analyzing = false,
                        report = null,
                        error = failure.actionableMessage("ARES could not build the guided review"),
                    )
                }
        }
    }
}

private fun Throwable.actionableMessage(prefix: String): String {
    val detail = message?.replace(Regex("[\\r\\n]+"), " ")?.trim().orEmpty()
    return if (detail.isBlank()) "$prefix. Refresh the run or choose another import." else "$prefix: $detail. Refresh the run or choose another import."
}
