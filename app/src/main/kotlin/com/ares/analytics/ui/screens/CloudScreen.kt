package com.ares.analytics.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.SessionSummary
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.CloudIntent
import com.ares.analytics.viewmodel.CloudViewModel
import com.ares.analytics.viewmodel.RobotLogFileInfo

@Composable
fun CloudScreen(
    viewModel: CloudViewModel,
    teamId: String,
    seasonId: String
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header / Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AresSurface)
                .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Cloud Data Management", color = AresTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                if (state.isAuthenticated) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AresGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Authenticated with Firebase", color = AresTextSecondary, fontSize = 12.sp)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AresAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Not Authenticated - Syncing may fail", color = AresTextSecondary, fontSize = 12.sp)
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onIntent(CloudIntent.RefreshRobotLogs) },
                    colors = ButtonDefaults.buttonColors(containerColor = AresSurfaceElevated)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AresCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Robot", color = AresCyan)
                }
                
                Button(
                    onClick = { viewModel.onIntent(CloudIntent.PerformDeltaSync(teamId, seasonId)) },
                    enabled = !state.isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, disabledContainerColor = AresSurfaceElevated)
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AresCyan, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = AresBackground)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Cloud", color = if (state.isSyncing) AresTextTertiary else AresBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Error message if any
        if (state.errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AresRedDark)
                    .border(1.dp, AresRed, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(state.errorMessage!!, color = AresTextPrimary, fontSize = 14.sp)
                TextButton(onClick = { viewModel.onIntent(CloudIntent.ClearError) }) {
                    Text("Dismiss", color = AresTextPrimary)
                }
            }
        }

        // Dual Lists
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane: Robot Logs
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Text("Robot Logs (Local)", color = AresTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AresSurface)
                        .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (state.isFetchingRobotLogs && state.robotRuns.isEmpty()) {
                        CircularProgressIndicator(color = AresCyan, modifier = Modifier.align(Alignment.Center))
                    } else if (state.robotRuns.isEmpty()) {
                        Text("No logs found on connected robot.", color = AresTextSecondary, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.robotRuns, key = { it.runId }) { run ->
                                RobotRunRow(
                                    run = run,
                                    isUploading = state.isUploadingRobotLog == run.runId,
                                    onUpload = { viewModel.onIntent(CloudIntent.UploadRobotRun(run.runId, teamId, seasonId, "robot-1")) },
                                    onDelete = { viewModel.onIntent(CloudIntent.DeleteRobotRun(run.runId)) }
                                )
                            }
                        }
                    }
                }
            }
            
            // Right Pane: Cloud Logs
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Text("Cloud Logs (Remote)", color = AresTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AresSurface)
                        .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (state.cloudLogs.isEmpty()) {
                        Text("No cloud sessions found.", color = AresTextSecondary, modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.cloudLogs, key = { it.sessionId }) { summary ->
                                CloudLogRow(
                                    summary = summary,
                                    isDeleting = state.isDeletingCloudLog == summary.sessionId,
                                    onDelete = { viewModel.onIntent(CloudIntent.DeleteCloudLog(summary.sessionId, summary.teamId)) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Console Output
        if (state.uploadLogs.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(AresBackground, RoundedCornerShape(8.dp))
                    .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text("Upload Console", color = AresCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                LaunchedEffect(state.uploadLogs.size) {
                    if (state.uploadLogs.isNotEmpty()) {
                        lazyListState.animateScrollToItem(state.uploadLogs.size - 1)
                    }
                }
                SelectionContainer {
                    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                        items(state.uploadLogs) { log ->
                            Text(log, color = AresTextSecondary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RobotRunRow(
    run: com.ares.analytics.viewmodel.RobotRun,
    isUploading: Boolean,
    onUpload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AresBackground, RoundedCornerShape(6.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Run: ${run.runId}", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            val statusText = if (run.isActive) " | ACTIVE RECORDING..." else ""
            Text(
                "Files: ${run.files.size} | Size: ${run.totalSizeBytes / 1024} KB | ${run.lastModifiedFmt}$statusText",
                color = if (run.isActive) AresCyan else AresTextSecondary,
                fontSize = 12.sp
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onDelete, enabled = !isUploading && !run.isActive) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AresRed)
            }
            Button(
                onClick = onUpload,
                enabled = !isUploading && !run.isActive,
                colors = ButtonDefaults.buttonColors(containerColor = AresSurfaceElevated)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AresCyan, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = AresCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload", color = AresCyan)
                }
            }
        }
    }
}

@Composable
fun CloudLogRow(
    summary: SessionSummary,
    isDeleting: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AresBackground, RoundedCornerShape(6.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            val formatter = java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
            val runName = formatter.format(java.util.Date(summary.createdAt))
            Text("Run: $runName (Cloud: ${summary.sessionId.takeLast(8)})", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            
            val sizeStr = if (summary.fileSizeBytes > 0) " | Size: ${summary.fileSizeBytes / 1024} KB" else ""
            val dateStr = java.text.SimpleDateFormat("MMM dd, HH:mm").format(java.util.Date(summary.createdAt))
            
            Text(
                "Match: ${summary.matchNumber ?: "None"}$sizeStr | $dateStr",
                color = AresTextSecondary,
                fontSize = 12.sp
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onDelete,
                enabled = !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AresRed, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Delete, contentDescription = "Delete from cloud", tint = AresRed)
                }
            }
        }
    }
}
