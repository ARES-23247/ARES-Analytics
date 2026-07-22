package com.ares.analytics.service

import com.ares.analytics.shared.ControllerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class KeybindingParserService {
    suspend fun parseBindings(projectPath: String): List<ControllerBinding> = withContext(Dispatchers.IO) {
        val root = File(projectPath)
        val bindings = mutableListOf<ControllerBinding>()
        if (!root.exists()) return@withContext emptyList()
        val ktFiles = root.walkTopDown()
            .filter { it.extension == "kt" || it.extension == "java" }
            .filter { !it.absolutePath.contains("build") }
            .toList()
        val dslRegex = Regex("""\b([a-zA-Z0-9_]+)\.([a-zA-Z_]+)\.(onPress|onRelease|whilePressed|label)\(\s*"([^"]+)"\s*\)""")
        
        for (file in ktFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                val match = dslRegex.find(line)
                if (match != null) {
                    val varName = match.groupValues[1]
                    val button = match.groupValues[2]
                    val eventType = match.groupValues[3] // onPress etc
                    val description = match.groupValues[4]
                    val gamepadId = if (varName.contains("operator") || varName.contains("2")) "gamepad2" else "gamepad1"
                    val actionLabel = "[$eventType] $description"
                    
                    bindings.add(ControllerBinding(gamepadId, button, actionLabel, file.name, index + 1))
                }
            }
        }
        
        bindings
    }
}
