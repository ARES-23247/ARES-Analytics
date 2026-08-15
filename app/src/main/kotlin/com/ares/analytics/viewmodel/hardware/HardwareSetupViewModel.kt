package com.ares.analytics.viewmodel.hardware

import com.ares.analytics.service.hardware.HardwareReviewRequest
import com.ares.analytics.service.hardware.HardwareSetupService
import com.ares.analytics.service.hardware.HardwareSetupSnapshot
import com.ares.analytics.shared.League
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HardwareSetupState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val snapshot: HardwareSetupSnapshot? = null,
    val reviewerName: String = "",
    val wiringMatched: Boolean = false,
    val addressesChecked: Boolean = false,
    val directionsChecked: Boolean = false,
    val neutralOutputsChecked: Boolean = false,
    val limitsChecked: Boolean = false,
    val error: String? = null,
) {
    val checklistComplete: Boolean
        get() = wiringMatched && addressesChecked && directionsChecked && neutralOutputsChecked && limitsChecked

    val canSaveReview: Boolean
        get() = !loading && !saving && reviewerName.trim().length >= 2 && checklistComplete && snapshot?.canReview == true
}

/** State holder for the descriptor-backed physical hardware review workflow. */
class HardwareSetupViewModel(
    private val projectPath: String,
    private val league: League,
    private val service: HardwareSetupService,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(HardwareSetupState())
    val state: StateFlow<HardwareSetupState> = _state.asStateFlow()
    private var operation: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        operation?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        operation = scope.launch {
            runCatching { withContext(Dispatchers.IO) { service.inspect(projectPath, league) } }
                .onSuccess { snapshot ->
                    _state.value = _state.value.copy(
                        loading = false,
                        snapshot = snapshot,
                        reviewerName = _state.value.reviewerName.ifBlank { snapshot.reviewedBy.orEmpty() },
                        error = null,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loading = false,
                        snapshot = null,
                        error = error.message ?: "Hardware Setup could not inspect this project.",
                    )
                }
        }
    }

    fun setReviewerName(value: String) {
        _state.value = _state.value.copy(reviewerName = value.take(80), error = null)
    }

    fun setWiringMatched(value: Boolean) {
        _state.value = _state.value.copy(wiringMatched = value, error = null)
    }

    fun setAddressesChecked(value: Boolean) {
        _state.value = _state.value.copy(addressesChecked = value, error = null)
    }

    fun setDirectionsChecked(value: Boolean) {
        _state.value = _state.value.copy(directionsChecked = value, error = null)
    }

    fun setNeutralOutputsChecked(value: Boolean) {
        _state.value = _state.value.copy(neutralOutputsChecked = value, error = null)
    }

    fun setLimitsChecked(value: Boolean) {
        _state.value = _state.value.copy(limitsChecked = value, error = null)
    }

    fun saveReview() {
        val current = _state.value
        if (!current.canSaveReview) return
        operation?.cancel()
        _state.value = current.copy(saving = true, error = null)
        operation = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    service.saveReview(
                        projectPath,
                        league,
                        HardwareReviewRequest(
                            reviewerName = current.reviewerName,
                            wiringMatched = current.wiringMatched,
                            addressesChecked = current.addressesChecked,
                            directionsChecked = current.directionsChecked,
                            neutralOutputsChecked = current.neutralOutputsChecked,
                            limitsChecked = current.limitsChecked,
                        ),
                    )
                }
            }.onSuccess { snapshot ->
                _state.value = current.copy(
                    loading = false,
                    saving = false,
                    snapshot = snapshot,
                    error = null,
                )
            }.onFailure { error ->
                _state.value = current.copy(
                    saving = false,
                    error = error.message ?: "The hardware review could not be recorded.",
                )
            }
        }
    }
}
