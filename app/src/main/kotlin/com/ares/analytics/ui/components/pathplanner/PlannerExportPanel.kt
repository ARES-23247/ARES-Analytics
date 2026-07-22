package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.League
import com.ares.analytics.shared.PathPoint
import com.ares.analytics.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun PlannerExportPanel(
    showPathControls: Boolean,
    projectPath: String?,
    league: League,
    waypoints: List<Waypoint>,
    modifier: Modifier = Modifier
) {
    if (showPathControls && !projectPath.isNullOrEmpty()) {
        var pathName by remember { mutableStateOf("autonomous_route") }
        var saveStatus by remember { mutableStateOf("") }
        
        Surface(
            modifier = modifier
                .padding(16.dp)
                .width(260.dp)
                .border(1.dp, AresBorder, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = AresSurfaceElevated.copy(alpha = 0.9f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Export Autonomous Path", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                
                OutlinedTextField(
                    value = pathName,
                    onValueChange = { pathName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AresCyan, unfocusedBorderColor = AresBorder)
                )

                Button(
                    onClick = {
                        try {
                            val json = Json { prettyPrint = true }
                            val pathData = waypoints.map { PathPoint(it.x, it.y) }
                            val serialized = json.encodeToString(pathData)
                            val relativeDir = if (league == League.FTC) "src/main/assets/paths" else "src/main/deploy/paths"
                            val targetDir = File(projectPath, relativeDir)
                            targetDir.mkdirs()
                            val targetFile = File(targetDir, "$pathName.json")
                            targetFile.writeText(serialized)
                            saveStatus = "Path exported to ${targetFile.name}!"
                        } catch (e: Exception) {
                            saveStatus = "Export failed: ${e.message}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Path JSON", color = AresBackground, fontWeight = FontWeight.Bold)
                }

                if (saveStatus.isNotEmpty()) {
                    Text(saveStatus, color = if (saveStatus.contains("failed")) AresError else AresGreen, fontSize = 10.sp)
                }
            }
        }
    }
}
