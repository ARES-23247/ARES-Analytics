package com.ares.analytics.viewmodel

import com.ares.analytics.service.SubsystemTemplateEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class HardwareType(val displayName: String) {
    DC_MOTOR_EX("DcMotorEx (Encoder/Velocity)"),
    SERVO("Servo (Positional)"),
    CONTINUOUS_SERVO("CRServo (Continuous)"),
    DIGITAL_CHANNEL("Digital Channel (Limit Switch/Touch)"),
    ANALOG_INPUT("Analog Input (Potentiometer/Distance)"),
    COLOR_SENSOR("Color Sensor")
}

enum class FieldType(val kotlinType: String, val defaultLiteral: String) {
    DOUBLE("Double", "0.0"),
    BOOLEAN("Boolean", "false"),
    INT("Int", "0"),
    STRING("String", "\"\"")
}

data class HardwareEntry(
    val name: String = "",
    val type: HardwareType = HardwareType.DC_MOTOR_EX,
    val hasEncoder: Boolean = false,
    val reverseDirection: Boolean = false
)

data class StateFieldEntry(
    val name: String = "",
    val type: FieldType = FieldType.DOUBLE,
    val defaultValue: String = "0.0"
)

data class GenerationResult(
    val success: Boolean,
    val message: String,
    val createdFiles: List<String> = emptyList()
)

data class SubsystemGeneratorState(
    val subsystemName: String = "",
    val packageName: String = "org.firstinspires.ftc.teamcode",
    val hardwareEntries: List<HardwareEntry> = listOf(HardwareEntry("motor", HardwareType.DC_MOTOR_EX)),
    val stateFields: List<StateFieldEntry> = listOf(
        StateFieldEntry("targetPosition", FieldType.DOUBLE, "0.0"),
        StateFieldEntry("currentPosition", FieldType.DOUBLE, "0.0")
    ),
    val generateMockIO: Boolean = true,
    val generateTestSkeleton: Boolean = false,
    val generateDedicatedActions: Boolean = false,
    val previewFiles: Map<String, String> = emptyMap(),
    val validationErrors: List<String> = emptyList(),
    val generationResult: GenerationResult? = null
)

sealed class SubsystemGeneratorIntent {
    data class SetName(val name: String) : SubsystemGeneratorIntent()
    data class SetPackageName(val pkg: String) : SubsystemGeneratorIntent()
    data class AddHardware(val entry: HardwareEntry = HardwareEntry()) : SubsystemGeneratorIntent()
    data class RemoveHardware(val index: Int) : SubsystemGeneratorIntent()
    data class UpdateHardware(val index: Int, val entry: HardwareEntry) : SubsystemGeneratorIntent()
    data class AddStateField(val entry: StateFieldEntry = StateFieldEntry()) : SubsystemGeneratorIntent()
    data class RemoveStateField(val index: Int) : SubsystemGeneratorIntent()
    data class UpdateStateField(val index: Int, val entry: StateFieldEntry) : SubsystemGeneratorIntent()
    data class SetOption(val optionKey: String, val value: Boolean) : SubsystemGeneratorIntent()
    object RefreshPreview : SubsystemGeneratorIntent()
    data class GenerateFiles(val projectPath: String) : SubsystemGeneratorIntent()
    object ClearResult : SubsystemGeneratorIntent()
}

/** Collects a subsystem schema and delegates source generation to [SubsystemTemplateEngine]. */
class SubsystemGeneratorViewModel(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(SubsystemGeneratorState())
    val state: StateFlow<SubsystemGeneratorState> = _state.asStateFlow()

    init {
        updatePreview()
    }

    fun onIntent(intent: SubsystemGeneratorIntent) {
        scope.launch {
            when (intent) {
                is SubsystemGeneratorIntent.SetName -> {
                    _state.update { it.copy(subsystemName = intent.name) }
                    validateAndUpdatePreview()
                }
                is SubsystemGeneratorIntent.SetPackageName -> {
                    _state.update { it.copy(packageName = intent.pkg) }
                    validateAndUpdatePreview()
                }
                is SubsystemGeneratorIntent.AddHardware -> {
                    _state.update { it.copy(hardwareEntries = it.hardwareEntries + intent.entry) }
                    validateAndUpdatePreview()
                }
                is SubsystemGeneratorIntent.RemoveHardware -> {
                    if (_state.value.hardwareEntries.size > 1) {
                        _state.update { it.copy(hardwareEntries = it.hardwareEntries.filterIndexed { i, _ -> i != intent.index }) }
                        validateAndUpdatePreview()
                    }
                }
                is SubsystemGeneratorIntent.UpdateHardware -> {
                    val updated = _state.value.hardwareEntries.toMutableList()
                    if (intent.index in updated.indices) {
                        updated[intent.index] = intent.entry
                        _state.update { it.copy(hardwareEntries = updated) }
                        validateAndUpdatePreview()
                    }
                }
                is SubsystemGeneratorIntent.AddStateField -> {
                    _state.update { it.copy(stateFields = it.stateFields + intent.entry) }
                    validateAndUpdatePreview()
                }
                is SubsystemGeneratorIntent.RemoveStateField -> {
                    if (_state.value.stateFields.size > 1) {
                        _state.update { it.copy(stateFields = it.stateFields.filterIndexed { i, _ -> i != intent.index }) }
                        validateAndUpdatePreview()
                    }
                }
                is SubsystemGeneratorIntent.UpdateStateField -> {
                    val updated = _state.value.stateFields.toMutableList()
                    if (intent.index in updated.indices) {
                        updated[intent.index] = intent.entry
                        _state.update { it.copy(stateFields = updated) }
                        validateAndUpdatePreview()
                    }
                }
                is SubsystemGeneratorIntent.SetOption -> {
                    when (intent.optionKey) {
                        "mockIO" -> _state.update { it.copy(generateMockIO = intent.value) }
                        "testSkeleton" -> _state.update { it.copy(generateTestSkeleton = intent.value) }
                        "dedicatedActions" -> _state.update { it.copy(generateDedicatedActions = intent.value) }
                    }
                    validateAndUpdatePreview()
                }
                is SubsystemGeneratorIntent.RefreshPreview -> {
                    validateAndUpdatePreview()
                }
                is SubsystemGeneratorIntent.GenerateFiles -> {
                    writeSubsystemFiles(intent.projectPath)
                }
                is SubsystemGeneratorIntent.ClearResult -> {
                    _state.update { it.copy(generationResult = null) }
                }
            }
        }
    }

    private fun validateAndUpdatePreview() {
        val currentState = _state.value
        val errors = mutableListOf<String>()

        val rawName = currentState.subsystemName.trim()
        when {
            rawName.isEmpty() -> errors.add("Subsystem name is required.")
            !rawName[0].isUpperCase() -> errors.add("Subsystem name should start with a capital letter (PascalCase).")
            rawName.any { !it.isLetterOrDigit() } -> errors.add("Subsystem name must contain only letters and numbers.")
        }

        if (currentState.packageName.isBlank()) {
            errors.add("Package name cannot be empty.")
        }

        val hwNames = currentState.hardwareEntries.map { it.name.trim() }
        if (hwNames.any { it.isEmpty() }) {
            errors.add("All hardware components must have a valid name.")
        }
        if (hwNames.distinct().size != hwNames.size) {
            errors.add("Hardware names must be unique.")
        }

        val fieldNames = currentState.stateFields.map { it.name.trim() }
        if (fieldNames.any { it.isEmpty() }) {
            errors.add("All state fields must have a valid name.")
        }
        if (fieldNames.distinct().size != fieldNames.size) {
            errors.add("State field names must be unique.")
        }

        val previews = if (errors.isEmpty()) {
            SubsystemTemplateEngine.generateFiles(currentState)
        } else {
            emptyMap()
        }

        _state.update {
            it.copy(
                validationErrors = errors,
                previewFiles = previews
            )
        }
    }

    private fun updatePreview() {
        validateAndUpdatePreview()
    }

    private fun writeSubsystemFiles(projectPath: String) {
        validateAndUpdatePreview()
        val current = _state.value

        if (current.validationErrors.isNotEmpty()) {
            _state.update {
                it.copy(generationResult = GenerationResult(false, "Cannot generate files with validation errors."))
            }
            return
        }

        val baseDir = if (projectPath.isNotBlank()) {
            val root = File(projectPath)
            val teamCodeSrc = File(root, "TeamCode/src/main/java/org/firstinspires/ftc/teamcode")
            if (teamCodeSrc.exists()) teamCodeSrc else root
        } else {
            File(System.getProperty("user.home"), "ARES-Subsystems")
        }

        try {
            val created = mutableListOf<String>()
            current.previewFiles.forEach { (relPath, content) ->
                val targetFile = File(baseDir, relPath)
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(content)
                created.add(targetFile.absolutePath)
            }

            _state.update {
                it.copy(
                    generationResult = GenerationResult(
                        success = true,
                        message = "Successfully generated ${created.size} subsystem files!",
                        createdFiles = created
                    )
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    generationResult = GenerationResult(
                        success = false,
                        message = "Failed to write files: ${e.message}"
                    )
                )
            }
        }
    }
}
