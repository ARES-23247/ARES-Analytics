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
                    WidgetConfig("mecanum_visualizer", "mecanum_visualizer", 0, 0, 6, 6),
                    WidgetConfig("joystick_visualizer", "joystick_visualizer", 0, 6, 6, 3),
                    WidgetConfig("telemetry_chart", "telemetry_chart", 6, 0, 6, 6),
                    WidgetConfig("alerts", "alerts", 6, 6, 3, 3),
                    WidgetConfig("ai_coach", "ai_coach", 9, 6, 3, 3)
                )
            )
            "programmer" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("telemetry_chart", "telemetry_chart", 0, 0, 6, 9),
                    WidgetConfig("motor_health", "motor_health", 6, 0, 3, 3),
                    WidgetConfig("vision_quality", "vision_quality", 6, 3, 3, 3),
                    WidgetConfig("alerts", "alerts", 6, 6, 3, 3),
                    WidgetConfig("console_viewer_0", "console_viewer", 9, 0, 3, 9)
                )
            )
            "pit_crew" -> DashboardLayoutConfig(
                listOf(
                    WidgetConfig("runs_index", "runs_index", 0, 0, 6, 3),
                    WidgetConfig("alerts", "alerts", 0, 3, 3, 6),
                    WidgetConfig("ai_coach", "ai_coach", 3, 3, 3, 6),
                    WidgetConfig("motor_health", "motor_health", 6, 0, 3, 5),
                    WidgetConfig("vision_quality", "vision_quality", 6, 5, 3, 4)
                )
            )
            else -> DashboardLayoutConfig( // Default standard layout
                listOf(
                    WidgetConfig("runs_index", "runs_index", 0, 0, 6, 3),
                    WidgetConfig("mecanum_visualizer", "mecanum_visualizer", 0, 3, 6, 3),
                    WidgetConfig("joystick_visualizer", "joystick_visualizer", 0, 6, 6, 3),
                    WidgetConfig("telemetry_chart", "telemetry_chart", 6, 0, 3, 6),
                    WidgetConfig("alerts", "alerts", 6, 6, 3, 3)
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
