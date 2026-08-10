package com.ares.analytics.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Grid layout configuration for a single dashboard widget card.
 *
 * @property id Unique widget instance identifier.
 * @property type Widget view type string (`"runs_index"`, `"alerts"`, `"telemetry_chart"`, `"motor_health"`, `"vision_quality"`, `"ai_coach"`, `"match_schedule"`, `"console_viewer"`).
 * @property row Zero-indexed grid row position.
 * @property col Zero-indexed grid column position.
 * @property rowSpan Row span count ($1 \dots N$).
 * @property colSpan Column span count ($1 \dots N$).
 * @property isLocked `true` if widget position is locked against user dragging.
 * @property properties Custom key-value properties dictionary for the widget.
 */
@Serializable
data class WidgetConfig(
    val id: String,
    val type: String, // "runs_index", "alerts", "telemetry_chart", "motor_health", "vision_quality", "ai_coach", "match_schedule", "console_viewer"
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val isLocked: Boolean = false,
    val properties: Map<String, String> = emptyMap()
)

/**
 * Dashboard layout container holding configured widgets.
 *
 * @property widgets List of [WidgetConfig] records.
 */
@Serializable
data class DashboardLayoutConfig(
    val widgets: List<WidgetConfig>
)

class LayoutPreferenceService(
    private val baseDir: String = System.getProperty("user.home") + "/.ares-analytics/layouts"
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        File(baseDir).mkdirs()
    }

    private fun getFileForProfile(profileName: String): File {
        return File(baseDir, "${profileName.lowercase().replace(" ", "_")}.json")
    }

    suspend fun saveLayout(profileName: String, config: DashboardLayoutConfig) = withContext(Dispatchers.IO) {
        val file = getFileForProfile(profileName)
        file.writeText(json.encodeToString(config))
    }

    suspend fun loadLayout(profileName: String): DashboardLayoutConfig = withContext(Dispatchers.IO) {
        val file = getFileForProfile(profileName)
        if (file.exists()) {
            try {
                return@withContext json.decodeFromString<DashboardLayoutConfig>(file.readText())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // Fallback to default layouts
        getDefaultLayout(profileName)
    }

    fun getDefaultLayout(profileName: String): DashboardLayoutConfig {
        return when (profileName.lowercase().replace(" ", "_")) {
            "driver_coach" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("field_viewer", "field_viewer", 0, 0, 5, 7),
                    WidgetConfig("joystick_visualizer", "joystick_visualizer", 0, 7, 3, 5),
                    WidgetConfig("system_health", "system_health", 3, 7, 2, 5),
                    WidgetConfig("telemetry_chart", "telemetry_chart", 5, 0, 5, 8),
                    WidgetConfig("alerts", "alerts", 5, 8, 5, 4)
                )
            )
            "programmer" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("telemetry_chart", "telemetry_chart", 0, 0, 6, 8),
                    WidgetConfig("console_viewer_0", "console_viewer", 0, 8, 6, 4),
                    WidgetConfig("system_health", "system_health", 6, 0, 3, 4),
                    WidgetConfig("profiling_diagnostics", "profiling_diagnostics", 6, 4, 3, 4),
                    WidgetConfig("ekf_telemetry", "ekf_telemetry", 6, 8, 3, 4)
                )
            )
            "pit_crew" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("runs_index", "runs_index", 0, 0, 3, 8),
                    WidgetConfig("system_health", "system_health", 0, 8, 3, 4),
                    WidgetConfig("motor_health", "motor_health", 3, 0, 4, 4),
                    WidgetConfig("vision_quality", "vision_quality", 3, 4, 4, 4),
                    WidgetConfig("alerts", "alerts", 3, 8, 4, 4),
                    WidgetConfig("advanced_analytics", "advanced_analytics", 7, 0, 4, 6),
                    WidgetConfig("ai_coach", "ai_coach", 7, 6, 4, 6)
                )
            )
            "replay", "match_review" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("runs_index", "runs_index", 0, 0, 3, 12),
                    WidgetConfig("telemetry_chart", "telemetry_chart", 3, 0, 5, 8),
                    WidgetConfig("field_viewer", "field_viewer", 3, 8, 5, 4),
                    WidgetConfig("advanced_analytics", "advanced_analytics", 8, 0, 5, 6),
                    WidgetConfig("alerts", "alerts", 8, 6, 5, 3),
                    WidgetConfig("system_health", "system_health", 8, 9, 5, 3)
                )
            )
            "pit_diagnostics" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("system_health", "system_health", 0, 0, 3, 6),
                    WidgetConfig("alerts", "alerts", 0, 6, 3, 6),
                    WidgetConfig("motor_health", "motor_health", 3, 0, 4, 4),
                    WidgetConfig("battery_health", "battery_health", 3, 4, 4, 4),
                    WidgetConfig("vision_quality", "vision_quality", 3, 8, 4, 4),
                    WidgetConfig("ai_coach", "ai_coach", 7, 0, 5, 6),
                    WidgetConfig("advanced_analytics", "advanced_analytics", 7, 6, 5, 6)
                )
            )
            "driver_practice" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("field_viewer", "field_viewer", 0, 0, 6, 8),
                    WidgetConfig("joystick_visualizer", "joystick_visualizer", 0, 8, 3, 4),
                    WidgetConfig("system_health", "system_health", 3, 8, 3, 4),
                    WidgetConfig("telemetry_chart", "telemetry_chart", 6, 0, 4, 8),
                    WidgetConfig("alerts", "alerts", 6, 8, 4, 4)
                )
            )
            else -> DashboardLayoutConfig( // Default standard layout
                listOf(
                    WidgetConfig("runs_index", "runs_index", 0, 0, 3, 7),
                    WidgetConfig("system_health", "system_health", 0, 7, 3, 5),
                    WidgetConfig("field_viewer", "field_viewer", 3, 0, 5, 7),
                    WidgetConfig("telemetry_chart", "telemetry_chart", 3, 7, 5, 5),
                    WidgetConfig("advanced_analytics", "advanced_analytics", 8, 0, 5, 6),
                    WidgetConfig("alerts", "alerts", 8, 6, 5, 3),
                    WidgetConfig("joystick_visualizer", "joystick_visualizer", 8, 9, 5, 3)
                )
            )
        }
    }

    fun getSavedLayouts(): List<String> {
        val dir = File(baseDir)
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        return files.map { file ->
            file.nameWithoutExtension.split("_").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    fun getAvailableLayouts(): List<String> {
        val defaults = listOf("Standard", "Driver Coach", "Programmer", "Pit Crew", "Match Review", "Pit Diagnostics", "Driver Practice", "Replay")
        val saved = getSavedLayouts()
        return (defaults + saved).distinct()
    }

    suspend fun deleteLayout(profileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = getFileForProfile(profileName)
        if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
