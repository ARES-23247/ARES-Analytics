package com.ares.analytics.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class WidgetConfig(
    val id: String,
    val type: String, // "runs_index", "alerts", "telemetry_chart", "motor_health", "vision_quality", "ai_coach", "match_schedule", "console_viewer"
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val properties: Map<String, String> = emptyMap()
)

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
                    WidgetConfig("mecanum_visualizer", "mecanum_visualizer", 0, 0, 2, 2),
                    WidgetConfig("joystick_visualizer", "joystick_visualizer", 0, 2, 2, 1),
                    WidgetConfig("telemetry_chart", "telemetry_chart", 2, 0, 2, 2),
                    WidgetConfig("alerts", "alerts", 2, 2, 1, 1),
                    WidgetConfig("ai_coach", "ai_coach", 3, 2, 1, 1)
                )
            )
            "programmer" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("telemetry_chart", "telemetry_chart", 0, 0, 2, 3),
                    WidgetConfig("motor_health", "motor_health", 2, 0, 1, 1),
                    WidgetConfig("vision_quality", "vision_quality", 2, 1, 1, 1),
                    WidgetConfig("alerts", "alerts", 2, 2, 1, 1),
                    WidgetConfig("console_viewer_0", "console_viewer", 3, 0, 1, 3)
                )
            )
            "pit_crew" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("runs_index", "runs_index", 0, 0, 2, 1),
                    WidgetConfig("alerts", "alerts", 0, 1, 1, 2),
                    WidgetConfig("ai_coach", "ai_coach", 1, 1, 1, 2),
                    WidgetConfig("motor_health", "motor_health", 2, 0, 1, 15),
                    WidgetConfig("vision_quality", "vision_quality", 2, 1, 1, 15)
                )
            )
            else -> DashboardLayoutConfig( // Default standard layout
                listOf(
                    WidgetConfig("runs_index", "runs_index", 0, 0, 2, 1),
                    WidgetConfig("mecanum_visualizer", "mecanum_visualizer", 0, 1, 2, 1),
                    WidgetConfig("joystick_visualizer", "joystick_visualizer", 0, 2, 2, 1),
                    WidgetConfig("telemetry_chart", "telemetry_chart", 2, 0, 1, 2),
                    WidgetConfig("alerts", "alerts", 2, 2, 1, 1)
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
        val defaults = listOf("Standard", "Driver Coach", "Programmer", "Pit Crew", "Match Review", "Pit Diagnostics", "Driver Practice")
        val saved = getSavedLayouts()
        return (defaults + saved).distinct()
    }
}
