package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.AresAmber
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresRed
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary
import com.ares.analytics.viewmodel.robotstudio.RobotStudioAction
import com.ares.analytics.viewmodel.robotstudio.RobotStudioStage
import com.ares.analytics.viewmodel.robotstudio.RobotStudioStageStatus
import com.ares.analytics.viewmodel.robotstudio.RobotStudioViewModel

/** Guided, read-first route through the existing specialized robot authoring tools. */
@Composable
fun RobotStudioScreen(
    viewModel: RobotStudioViewModel,
    onAction: (RobotStudioAction) -> Unit,
    onOpenAcademy: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    BoxWithConstraints(Modifier.fillMaxSize().background(AresBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(if (maxWidth < 760.dp) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RobotStudioHeader(
                    projectName = state.projectName,
                    projectPath = state.projectPath,
                    ready = state.readyCount,
                    total = state.stages.size,
                    loading = state.loading,
                    onRefresh = viewModel::refresh,
                    onOpenAcademy = onOpenAcademy,
                )
            }
            state.error?.let { message ->
                item { StudioError(message, onRefresh = viewModel::refresh) }
            }
            if (state.loading && state.stages.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AresCyan)
                        Spacer(Modifier.width(10.dp))
                        Text("Inspecting canonical project documents…", color = AresTextSecondary)
                    }
                }
            }
            state.nextStage?.let { next ->
                item { NextStepCard(next, onAction) }
            }
            items(state.stages, key = { it.id }) { stage ->
                RobotStudioStageCard(stage, onAction)
            }
            item {
                Surface(
                    color = AresSurface,
                    border = BorderStroke(1.dp, AresBorder),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "Robot Studio reports document, simulator, and imported-run evidence only. A successful simulation or build is not physical robot validation; use your team’s supervised hardware checklist before enabling mechanisms.",
                        color = AresTextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RobotStudioHeader(
    projectName: String,
    projectPath: String,
    ready: Int,
    total: Int,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenAcademy: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PrecisionManufacturing, contentDescription = null, tint = AresCyan, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Robot Studio", color = AresTextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Build, verify, simulate, and understand one robot without losing the project-wide story.", color = AresTextSecondary)
                }
                OutlinedButton(onClick = onRefresh, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (loading) "Refreshing" else "Refresh")
                }
            }
            HorizontalDivider(color = AresBorder)
            Text(projectName.ifBlank { "Selected robot workspace" }, color = AresTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(projectPath.ifBlank { "No project folder selected" }, color = AresTextTertiary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            if (total > 0) {
                Text("$ready of $total stages currently ready or running", color = AresTextSecondary, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = { ready.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = AresGreen,
                    trackColor = AresSurfaceElevated,
                )
            }
            OutlinedButton(onClick = onOpenAcademy) { Text("Learn this workflow in Robot Academy") }
        }
    }
}

@Composable
private fun NextStepCard(stage: RobotStudioStage, onAction: (RobotStudioAction) -> Unit) {
    Surface(
        color = AresCyan.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Recommended next step", color = AresTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(stage.title, color = AresTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(stage.explanation, color = AresTextSecondary, lineHeight = 19.sp)
            }
            Button(
                onClick = { onAction(stage.action) },
                enabled = stage.status != RobotStudioStageStatus.BLOCKED,
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            ) {
                Text(stage.actionLabel, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun RobotStudioStageCard(stage: RobotStudioStage, onAction: (RobotStudioAction) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StageStatus(stage.status)
                Column(Modifier.weight(1f)) {
                    Text(stage.title, color = AresTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(stage.outcome, color = AresTextSecondary, lineHeight = 19.sp)
                }
                OutlinedButton(
                    onClick = { onAction(stage.action) },
                    enabled = stage.status != RobotStudioStageStatus.BLOCKED && stage.status != RobotStudioStageStatus.RUNNING,
                ) { Text(stage.actionLabel) }
            }
            Text(stage.explanation, color = AresTextSecondary, lineHeight = 19.sp)
            stage.issues.forEach { issue ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AresAmber, modifier = Modifier.size(17.dp))
                    Text(issue, color = AresTextPrimary, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Surface(color = AresSurfaceElevated, shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Stored in: ${stage.storage}", color = AresTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("Used by: ${stage.consumer}", color = AresTextTertiary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StageStatus(status: RobotStudioStageStatus) {
    val (icon, tint) = when (status) {
        RobotStudioStageStatus.READY -> Icons.Default.CheckCircle to AresGreen
        RobotStudioStageStatus.RUNNING -> Icons.Default.HourglassTop to AresCyan
        RobotStudioStageStatus.OPTIONAL -> Icons.Default.Info to AresTextSecondary
        RobotStudioStageStatus.NEEDS_ACTION -> Icons.Default.Warning to AresAmber
        RobotStudioStageStatus.BLOCKED -> Icons.Default.Block to AresTextSecondary
        RobotStudioStageStatus.INVALID -> Icons.Default.Error to AresRed
        RobotStudioStageStatus.CODE_REQUIRED -> Icons.Default.Code to AresAmber
    }
    Surface(
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(status.label, color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StudioError(message: String, onRefresh: () -> Unit) {
    Surface(
        color = AresRed.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, AresRed.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = AresRed)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Robot Studio could not inspect the project", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                Text("$message Check the selected repository and canonical files, then refresh.", color = AresTextSecondary)
            }
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
        }
    }
}
