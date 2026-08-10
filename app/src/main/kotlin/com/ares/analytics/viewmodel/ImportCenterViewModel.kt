package com.ares.analytics.viewmodel

import com.ares.analytics.service.ImportArchiveEntry
import com.ares.analytics.service.ImportArchiveService
import com.ares.analytics.service.ImportArchiveSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportCenterState(
    val snapshot: ImportArchiveSnapshot = ImportArchiveSnapshot(),
    val isLoading: Boolean = true,
    val retryingId: String? = null,
    val message: String? = null,
    val error: String? = null
)

sealed class ImportCenterIntent {
    data object Refresh : ImportCenterIntent()
    data class Retry(val entry: ImportArchiveEntry) : ImportCenterIntent()
    data object ClearNotice : ImportCenterIntent()
}

class ImportCenterViewModel(
    private val archiveService: ImportArchiveService,
    private val projectPath: String,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ImportCenterState())
    val state: StateFlow<ImportCenterState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onIntent(intent: ImportCenterIntent) {
        when (intent) {
            ImportCenterIntent.Refresh -> refresh()
            ImportCenterIntent.ClearNotice -> _state.update { it.copy(message = null, error = null) }
            is ImportCenterIntent.Retry -> retry(intent.entry)
        }
    }

    private fun refresh() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = withContext(Dispatchers.IO) { archiveService.load(projectPath) }
                _state.update { it.copy(snapshot = snapshot, isLoading = false) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = failure.message ?: "Failed to read import reports")
                }
            }
        }
    }

    private fun retry(entry: ImportArchiveEntry) {
        if (_state.value.retryingId != null) return
        scope.launch {
            _state.update { it.copy(retryingId = entry.id, message = null, error = null) }
            try {
                val requeued = withContext(Dispatchers.IO) { archiveService.retry(projectPath, entry) }
                val snapshot = withContext(Dispatchers.IO) { archiveService.load(projectPath) }
                _state.update {
                    it.copy(
                        snapshot = snapshot,
                        retryingId = null,
                        message = "Requeued ${entry.report?.sourceName ?: requeued.name}; auto-import will process it shortly"
                    )
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                _state.update {
                    it.copy(retryingId = null, error = failure.message ?: "Failed to retry quarantined log")
                }
            }
        }
    }
}
