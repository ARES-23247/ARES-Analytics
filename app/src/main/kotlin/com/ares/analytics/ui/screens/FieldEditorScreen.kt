package com.ares.analytics.ui.screens

import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.*
import com.ares.analytics.ui.components.pathplanner.FieldCanvas
import com.ares.analytics.ui.screens.fieldeditor.AprilTagRow
import com.ares.analytics.ui.screens.fieldeditor.GamePieceRow
import com.ares.analytics.ui.screens.fieldeditor.ObstacleRow
import com.ares.analytics.ui.screens.fieldeditor.FieldWaypointRow
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.FieldEditorIntent
import com.ares.analytics.viewmodel.FieldEditorViewModel

@Composable
fun FieldEditorScreen(
    viewModel: FieldEditorViewModel,
    league: League,
    projectPath: String? = null
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(projectPath) {
        viewModel.onIntent(FieldEditorIntent.LoadConfig(projectPath, league))
    }

    var showCropBoundaries by remember { mutableStateOf(false) }
    var obstaclesCollapsed by remember { mutableStateOf(false) }
    var gamePiecesCollapsed by remember { mutableStateOf(false) }
    var aprilTagsCollapsed by remember { mutableStateOf(false) }
    var waypointsCollapsed by remember { mutableStateOf(false) }

    val fieldWidthM = if (state.fieldImageConfig.widthMeters > 0.0) state.fieldImageConfig.widthMeters else (if (league == League.FTC) 3.65 else 16.5)
    val fieldHeightM = if (state.fieldImageConfig.heightMeters > 0.0) state.fieldImageConfig.heightMeters else (if (league == League.FTC) 3.65 else 8.2)

    fun mirrorObstacleX(obs: Obstacle, fieldWidth: Double, league: League): Obstacle {
        return when (obs) {
            is Obstacle.Circle -> {
                val newX = if (league == League.FTC) -obs.centerX else fieldWidth - obs.centerX
                obs.copy(centerX = newX)
            }
            is Obstacle.Rectangle -> {
                val newX = if (league == League.FTC) -obs.centerX else fieldWidth - obs.centerX
                obs.copy(centerX = newX, rotation = -obs.rotation)
            }
            is Obstacle.Polygon -> {
                obs.copy(vertices = obs.vertices.map { v ->
                    val newX = if (league == League.FTC) -v.x else fieldWidth - v.x
                    PathPoint(newX, v.y)
                })
            }
        }
    }

    fun mirrorObstacleY(obs: Obstacle, fieldHeight: Double, league: League): Obstacle {
        return when (obs) {
            is Obstacle.Circle -> {
                val newY = if (league == League.FTC) -obs.centerY else fieldHeight - obs.centerY
                obs.copy(centerY = newY)
            }
            is Obstacle.Rectangle -> {
                val newY = if (league == League.FTC) -obs.centerY else fieldHeight - obs.centerY
                obs.copy(centerY = newY, rotation = -obs.rotation)
            }
            is Obstacle.Polygon -> {
                obs.copy(vertices = obs.vertices.map { v ->
                    val newY = if (league == League.FTC) -v.y else fieldHeight - v.y
                    PathPoint(v.x, newY)
                })
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left side controls
        Surface(
            modifier = Modifier.width(320.dp).fillMaxHeight().border(1.dp, AresBorder, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = AresSurface
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Field Background Editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                HorizontalDivider(color = AresBorder)

                Button(
                    onClick = {
                        if (!projectPath.isNullOrEmpty()) {
                            SwingUtilities.invokeLater {
                                val chooser = JFileChooser().apply {
                                    dialogTitle = "Select Field Image (PNG/JPG)"
                                    fileFilter = FileNameExtensionFilter("Images", "png", "jpg", "jpeg")
                                }
                                val result = chooser.showOpenDialog(null)
                                if (result == JFileChooser.APPROVE_OPTION) {
                                    val selectedFile = chooser.selectedFile
                                    if (selectedFile != null && selectedFile.exists()) {
                                        viewModel.onIntent(FieldEditorIntent.ImportFieldImage(selectedFile, projectPath, league))
                                    }
                                }
                            }
                        } else {
                            viewModel.onIntent(FieldEditorIntent.UpdateFieldImageConfig(state.fieldImageConfig, projectPath, league))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = AresBackground)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload Field Image", color = AresBackground, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = AresBorder)

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showCropBoundaries = !showCropBoundaries }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Image Adjustments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                    Icon(
                        imageVector = if (showCropBoundaries) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = "Expand/Collapse",
                        tint = AresTextSecondary
                    )
                }

                AnimatedVisibility(visible = showCropBoundaries) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Orientation", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(0.0, 90.0, 180.0, 270.0).forEach { angle ->
                                    val isSelected = state.fieldImageConfig.rotationDegrees == angle
                                    Button(
                                        onClick = {
                                            viewModel.onIntent(FieldEditorIntent.UpdateFieldImageConfig(state.fieldImageConfig.copy(rotationDegrees = angle), projectPath, league))
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) AresCyan else AresSurfaceElevated,
                                            contentColor = if (isSelected) AresBackground else AresTextPrimary
                                        ),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 2.dp)
                                    ) {
                                        Text("${angle.toInt()}°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Crop Boundaries", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                            Text("Left Crop: ${String.format("%.2f", state.fieldImageConfig.cropLeft)}", fontSize = 11.sp, color = AresTextSecondary)
                            Slider(
                                value = state.fieldImageConfig.cropLeft.toFloat(),
                                onValueChange = {
                                    viewModel.onIntent(FieldEditorIntent.UpdateFieldImageConfig(state.fieldImageConfig.copy(cropLeft = it.toDouble().coerceIn(0.0, state.fieldImageConfig.cropRight)), projectPath, league))
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan)
                            )

                            Text("Right Crop: ${String.format("%.2f", state.fieldImageConfig.cropRight)}", fontSize = 11.sp, color = AresTextSecondary)
                            Slider(
                                value = state.fieldImageConfig.cropRight.toFloat(),
                                onValueChange = {
                                    viewModel.onIntent(FieldEditorIntent.UpdateFieldImageConfig(state.fieldImageConfig.copy(cropRight = it.toDouble().coerceIn(state.fieldImageConfig.cropLeft, 1.0)), projectPath, league))
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan)
                            )

                            Text("Top Crop: ${String.format("%.2f", state.fieldImageConfig.cropTop)}", fontSize = 11.sp, color = AresTextSecondary)
                            Slider(
                                value = state.fieldImageConfig.cropTop.toFloat(),
                                onValueChange = {
                                    viewModel.onIntent(FieldEditorIntent.UpdateFieldImageConfig(state.fieldImageConfig.copy(cropTop = it.toDouble().coerceIn(0.0, state.fieldImageConfig.cropBottom)), projectPath, league))
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan)
                            )

                            Text("Bottom Crop: ${String.format("%.2f", state.fieldImageConfig.cropBottom)}", fontSize = 11.sp, color = AresTextSecondary)
                            Slider(
                                value = state.fieldImageConfig.cropBottom.toFloat(),
                                onValueChange = {
                                    viewModel.onIntent(FieldEditorIntent.UpdateFieldImageConfig(state.fieldImageConfig.copy(cropBottom = it.toDouble().coerceIn(state.fieldImageConfig.cropTop, 1.0)), projectPath, league))
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = AresCyan, activeTrackColor = AresCyan)
                            )
                        }
                    }
                }

                if (league == League.FTC) {
                    HorizontalDivider(color = AresBorder)
                    
                    Text("FTC Coordinate System", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FTCCoordinateSystem.entries.forEach { coord ->
                            val isSelected = state.fieldImageConfig.ftcCoordinateSystem == coord
                            Button(
                                onClick = {
                                    viewModel.onIntent(FieldEditorIntent.UpdateFieldImageConfig(state.fieldImageConfig.copy(ftcCoordinateSystem = coord), projectPath, league))
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) AresCyan else AresSurfaceElevated,
                                    contentColor = if (isSelected) AresBackground else AresTextPrimary
                                )
                                ,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = when (coord) {
                                        FTCCoordinateSystem.DIAMOND -> "Diamond"
                                        FTCCoordinateSystem.SQUARE -> "Square"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = AresBorder)

                Button(
                    onClick = {
                        viewModel.onIntent(FieldEditorIntent.UpdateFieldImageConfig(state.fieldImageConfig, projectPath, league))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Configuration", color = AresBackground, fontWeight = FontWeight.Bold)
                }

                if (state.saveStatus.isNotEmpty()) {
                    Text(
                        text = state.saveStatus,
                        color = if (state.saveStatus.contains("failed") || state.saveStatus.contains("error")) AresError else AresGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LaunchedEffect(state.saveStatus) {
                        kotlinx.coroutines.delay(3000)
                        viewModel.onIntent(FieldEditorIntent.ClearSaveStatus)
                    }
                }

                // Drawn Obstacles Section
                if (state.obstacles.isNotEmpty()) {
                    HorizontalDivider(color = AresBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state.selectedElement == null) { obstaclesCollapsed = !obstaclesCollapsed }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.selectedElement == null) {
                                Icon(
                                    imageVector = if (obstaclesCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = AresTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (state.selectedElement != null) "Selected Item Properties" else "Drawn Obstacles (${state.obstacles.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AresTextPrimary
                            )
                        }
                        if (state.selectedElement != null) {
                            TextButton(
                                onClick = { viewModel.onIntent(FieldEditorIntent.SelectElement(null)) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Clear Selection", fontSize = 10.sp, color = AresCyan)
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        val copies = state.obstacles.map { obs ->
                                            val mirrored = mirrorObstacleX(obs, fieldWidthM, league)
                                            when (mirrored) {
                                                is Obstacle.Circle -> mirrored.copy(id = "circle_${System.currentTimeMillis()}_${obs.id.hashCode()}", name = "${obs.name} Mirrored X")
                                                is Obstacle.Rectangle -> mirrored.copy(id = "rect_${System.currentTimeMillis()}_${obs.id.hashCode()}", name = "${obs.name} Mirrored X")
                                                is Obstacle.Polygon -> mirrored.copy(id = "poly_${System.currentTimeMillis()}_${obs.id.hashCode()}", name = "${obs.name} Mirrored X")
                                            }
                                        }
                                        viewModel.onIntent(FieldEditorIntent.SetObstacles(state.obstacles + copies))
                                        viewModel.onIntent(FieldEditorIntent.SaveObstacles(projectPath, league))
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(12.dp), tint = AresCyan)
                                    Spacer(Modifier.width(2.dp))
                                    Text("Copy X", fontSize = 10.sp, color = AresCyan)
                                }
                                TextButton(
                                    onClick = {
                                        val copies = state.obstacles.map { obs ->
                                            val mirrored = mirrorObstacleY(obs, fieldHeightM, league)
                                            when (mirrored) {
                                                is Obstacle.Circle -> mirrored.copy(id = "circle_${System.currentTimeMillis()}_${obs.id.hashCode()}", name = "${obs.name} Mirrored Y")
                                                is Obstacle.Rectangle -> mirrored.copy(id = "rect_${System.currentTimeMillis()}_${obs.id.hashCode()}", name = "${obs.name} Mirrored Y")
                                                is Obstacle.Polygon -> mirrored.copy(id = "poly_${System.currentTimeMillis()}_${obs.id.hashCode()}", name = "${obs.name} Mirrored Y")
                                            }
                                        }
                                        viewModel.onIntent(FieldEditorIntent.SetObstacles(state.obstacles + copies))
                                        viewModel.onIntent(FieldEditorIntent.SaveObstacles(projectPath, league))
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(12.dp), tint = AresCyan)
                                    Spacer(Modifier.width(2.dp))
                                    Text("Copy Y", fontSize = 10.sp, color = AresCyan)
                                }
                            }
                        }
                    }
                    
                    if (!obstaclesCollapsed || state.selectedElement != null) {
                        state.obstacles.forEachIndexed { index, obs ->
                            if (state.selectedElement == null || state.selectedElement == obs.id) {
                                key(obs.id) {
                                    ObstacleRow(
                                        index = index,
                                        obs = obs,
                                        fieldWidthM = fieldWidthM,
                                        fieldHeightM = fieldHeightM,
                                        league = league,
                                        onUpdate = { i, updated ->
                                            viewModel.onIntent(FieldEditorIntent.UpdateObstacle(i, updated))
                                            viewModel.onIntent(FieldEditorIntent.SaveObstacles(projectPath, league))
                                        },
                                        onDelete = { i ->
                                            viewModel.onIntent(FieldEditorIntent.DeleteObstacle(i))
                                            viewModel.onIntent(FieldEditorIntent.SaveObstacles(projectPath, league))
                                        },
                                        onAdd = { copy ->
                                            viewModel.onIntent(FieldEditorIntent.AddObstacle(copy))
                                            viewModel.onIntent(FieldEditorIntent.SaveObstacles(projectPath, league))
                                        },
                                        onMirrorX = ::mirrorObstacleX,
                                        onMirrorY = ::mirrorObstacleY
                                    )
                                }
                            }
                        }
                    }
                }

                // Placed Game Pieces Section
                if (state.gamePieces.isNotEmpty()) {
                    HorizontalDivider(color = AresBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { gamePiecesCollapsed = !gamePiecesCollapsed }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (gamePiecesCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = AresTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Placed Game Pieces (${state.gamePieces.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AresTextPrimary
                        )
                    }
                    if (!gamePiecesCollapsed) {
                        state.gamePieces.forEachIndexed { index, gp ->
                            key(gp.id) {
                                GamePieceRow(
                                    index = index,
                                    gp = gp,
                                    league = league,
                                    onUpdate = { i, updated ->
                                        viewModel.onIntent(FieldEditorIntent.UpdateGamePiece(i, updated))
                                        viewModel.onIntent(FieldEditorIntent.SaveGamePieces(projectPath, league))
                                    },
                                    onDelete = { i ->
                                        viewModel.onIntent(FieldEditorIntent.DeleteGamePiece(i))
                                        viewModel.onIntent(FieldEditorIntent.SaveGamePieces(projectPath, league))
                                    }
                                )
                            }
                        }
                    }
                }

                // AprilTags Section
                HorizontalDivider(color = AresBorder)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { aprilTagsCollapsed = !aprilTagsCollapsed }
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (aprilTagsCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = AresTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AprilTags (${state.aprilTags.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AresTextPrimary
                        )
                    }

                    if (!projectPath.isNullOrEmpty()) {
                        Button(
                            onClick = {
                                SwingUtilities.invokeLater {
                                    val chooser = JFileChooser().apply {
                                        dialogTitle = "Select Limelight .fmap File"
                                        fileFilter = FileNameExtensionFilter("AprilTag Map (.fmap)", "fmap")
                                    }
                                    val result = chooser.showOpenDialog(null)
                                    if (result == JFileChooser.APPROVE_OPTION) {
                                        val selectedFile = chooser.selectedFile
                                        val fmapContent = selectedFile.readText()
                                        viewModel.onIntent(FieldEditorIntent.ImportFmap(fmapContent, projectPath, league))
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AresSurfaceElevated)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = AresTextPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Import .fmap", fontSize = 11.sp, color = AresTextPrimary)
                        }
                    }
                }
                if (!aprilTagsCollapsed && state.aprilTags.isNotEmpty()) {
                    state.aprilTags.forEachIndexed { index, at ->
                        key(at.id) {
                            AprilTagRow(
                                index = index,
                                at = at,
                                onUpdate = { i, updated ->
                                    viewModel.onIntent(FieldEditorIntent.UpdateAprilTag(i, updated))
                                    viewModel.onIntent(FieldEditorIntent.SaveAprilTags(projectPath, league))
                                },
                                onDelete = { i ->
                                    viewModel.onIntent(FieldEditorIntent.DeleteAprilTag(i))
                                    viewModel.onIntent(FieldEditorIntent.SaveAprilTags(projectPath, league))
                                }
                            )
                        }
                    }
                }

                // Placed Waypoints Section
                if (state.fieldWaypoints.isNotEmpty()) {
                    HorizontalDivider(color = AresBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { waypointsCollapsed = !waypointsCollapsed }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (waypointsCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = AresTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Placed Waypoints (${state.fieldWaypoints.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AresTextPrimary
                        )
                    }
                    if (!waypointsCollapsed) {
                        state.fieldWaypoints.forEachIndexed { index, wp ->
                            key(wp.id) {
                                FieldWaypointRow(
                                    index = index,
                                    wp = wp,
                                    onUpdate = { i, updated ->
                                        viewModel.onIntent(FieldEditorIntent.UpdateFieldWaypoint(i, updated))
                                        viewModel.onIntent(FieldEditorIntent.SaveFieldWaypoints(projectPath, league))
                                    },
                                    onDelete = { i ->
                                        viewModel.onIntent(FieldEditorIntent.DeleteFieldWaypoint(i))
                                        viewModel.onIntent(FieldEditorIntent.SaveFieldWaypoints(projectPath, league))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right side canvas
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, AresBorder, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))
        ) {
            FieldCanvas(
                league = league,
                waypoints = emptyList(),
                actualPath = emptyList(),
                onWaypointsChanged = {},
                projectPath = projectPath,
                showPathControls = false,
                showObstacleControls = true,
                fieldImage = state.fieldImage,
                fieldImageConfig = state.fieldImageConfig,
                obstacles = state.obstacles,
                onObstaclesChanged = {
                    viewModel.onIntent(FieldEditorIntent.SetObstacles(it))
                },
                gamePieces = state.gamePieces,
                onGamePiecesChanged = {
                    viewModel.onIntent(FieldEditorIntent.SetGamePieces(it))
                },
                aprilTags = state.aprilTags,
                onAprilTagsChanged = {
                    viewModel.onIntent(FieldEditorIntent.SetAprilTags(it))
                },
                fieldWaypoints = state.fieldWaypoints,
                onFieldWaypointsChanged = {
                    viewModel.onIntent(FieldEditorIntent.SetFieldWaypoints(it))
                    viewModel.onIntent(FieldEditorIntent.SaveFieldWaypoints(projectPath, league))
                }
            )
        }
    }
}
