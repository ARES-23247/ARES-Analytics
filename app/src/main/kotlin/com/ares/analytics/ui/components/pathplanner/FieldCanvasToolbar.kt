package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.League
import com.ares.analytics.shared.Obstacle
import com.ares.analytics.shared.PathPoint
import com.ares.analytics.ui.theme.*

@Composable
fun FieldCanvasToolbar(
    showPathControls: Boolean,
    showObstacleControls: Boolean,
    league: League,
    editorMode: EditorMode,
    onEditorModeChanged: (EditorMode) -> Unit,
    activeGamePieceType: String,
    onActiveGamePieceTypeChanged: (String) -> Unit,
    currentPolygonPoints: List<PathPoint>,
    onCurrentPolygonPointsCleared: () -> Unit,
    activeObstacles: List<Obstacle>,
    updateObstacles: (List<Obstacle>) -> Unit,
    zoomScale: Float,
    onZoomScaleChanged: (Float) -> Unit,
    onResetZoomPan: () -> Unit,
    showHeatmap: Boolean,
    onShowHeatmapChanged: (Boolean) -> Unit,
    viewRotation: Float,
    onViewRotationChanged: (Float) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = AresSurfaceElevated
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showPathControls || showObstacleControls) {
                // Select Mode
                IconButton(
                    onClick = { onEditorModeChanged(EditorMode.SELECT) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (editorMode == EditorMode.SELECT) AresCyan else AresTextTertiary)
                ) {
                    Icon(Icons.Default.PanTool, contentDescription = "Select / Drag")
                }
            }

            // Add Waypoint Mode
            if (showPathControls) {
                IconButton(
                    onClick = { onEditorModeChanged(EditorMode.ADD_WAYPOINT) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (editorMode == EditorMode.ADD_WAYPOINT) AresCyan else AresTextTertiary)
                ) {
                    Icon(Icons.Default.AddLocation, contentDescription = "Add Waypoint")
                }
            }

            // Draw Polygon Obstacle
            if (showObstacleControls) {
                IconButton(
                    onClick = { onEditorModeChanged(EditorMode.DRAW_POLYGON) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (editorMode == EditorMode.DRAW_POLYGON) AresCyan else AresTextTertiary)
                ) {
                    Icon(Icons.Default.Polyline, contentDescription = "Draw Free Form Obstacle")
                }
            }
                
            if (showObstacleControls && editorMode == EditorMode.DRAW_POLYGON && currentPolygonPoints.isNotEmpty()) {
                TextButton(
                    onClick = {
                        if (currentPolygonPoints.size >= 3) {
                            val newPoly = Obstacle.Polygon(
                                id = "poly_${System.currentTimeMillis()}",
                                name = "Polygon Obstacle ${activeObstacles.size + 1}",
                                vertices = currentPolygonPoints.toList()
                            )
                            updateObstacles(activeObstacles + newPoly)
                        }
                        onCurrentPolygonPointsCleared()
                    }
                ) {
                    Text("Finish Poly", color = AresCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Draw Circle Obstacle
            if (showObstacleControls) {
                IconButton(
                    onClick = { onEditorModeChanged(EditorMode.DRAW_CIRCLE) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (editorMode == EditorMode.DRAW_CIRCLE) AresCyan else AresTextTertiary)
                ) {
                    Icon(Icons.Default.TripOrigin, contentDescription = "Draw Circle Obstacle")
                }
            }

            // Draw Rectangle Obstacle
            if (showObstacleControls) {
                IconButton(
                    onClick = { onEditorModeChanged(EditorMode.DRAW_RECTANGLE) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (editorMode == EditorMode.DRAW_RECTANGLE) AresCyan else AresTextTertiary)
                ) {
                    Icon(Icons.Default.CropSquare, contentDescription = "Draw Rect Obstacle")
                }
            }

            // Place Game Piece Mode
            if (showObstacleControls) {
                IconButton(
                    onClick = { onEditorModeChanged(EditorMode.PLACE_GAME_PIECE) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (editorMode == EditorMode.PLACE_GAME_PIECE) AresCyan else AresTextTertiary)
                ) {
                    Icon(Icons.Default.Extension, contentDescription = "Place Game Piece")
                }
            }

            if (showObstacleControls && editorMode == EditorMode.PLACE_GAME_PIECE) {
                Box(modifier = Modifier.height(24.dp).width(1.dp).background(AresBorder))
                if (league == League.FTC) {
                    listOf("Sample (Yellow)", "Sample (Red)", "Sample (Blue)", "Specimen", "Decode (Ball)").forEach { type ->
                        val isSel = activeGamePieceType == type
                        val color = when (type) {
                            "Sample (Yellow)" -> Color.Yellow
                            "Sample (Red)" -> AresRed
                            "Sample (Blue)" -> AresCyan
                            "Decode (Ball)" -> Color.White
                            else -> Color.Magenta
                        }
                        TextButton(
                            onClick = { onActiveGamePieceTypeChanged(type) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = type.substringAfter("(").substringBefore(")").take(3),
                                color = if (isSel) color else AresTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                } else {
                    listOf("Note", "High Note").forEach { type ->
                        val isSel = activeGamePieceType == type
                        TextButton(
                            onClick = { onActiveGamePieceTypeChanged(type) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = type,
                                color = if (isSel) AresAmber else AresTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Place AprilTag Mode
            if (showObstacleControls && league == League.FTC) {
                IconButton(
                    onClick = { onEditorModeChanged(EditorMode.PLACE_APRILTAG) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (editorMode == EditorMode.PLACE_APRILTAG) AresCyan else AresTextTertiary)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Place AprilTag")
                }
            }

            // Eraser Mode
            if (showPathControls || showObstacleControls) {
                IconButton(
                    onClick = { onEditorModeChanged(EditorMode.ERASER) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = if (editorMode == EditorMode.ERASER) AresCyan else AresTextTertiary)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Eraser")
                }
            }

            if (showPathControls || showObstacleControls) {
                Box(modifier = Modifier.height(24.dp).width(1.dp).background(AresBorder))
            }

            // Zoom controls
            IconButton(onClick = { onZoomScaleChanged((zoomScale + 0.1f).coerceAtMost(3f)) }) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = AresTextSecondary)
            }
            IconButton(onClick = { onZoomScaleChanged((zoomScale - 0.1f).coerceAtLeast(0.5f)) }) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = AresTextSecondary)
            }
            IconButton(onClick = { onResetZoomPan() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom", tint = AresTextSecondary)
            }
            IconButton(
                onClick = { onShowHeatmapChanged(!showHeatmap) },
                colors = IconButtonDefaults.iconButtonColors(contentColor = if (showHeatmap) AresCyan else AresTextTertiary)
            ) {
                Icon(Icons.Default.Whatshot, contentDescription = "Toggle Heatmap")
            }
            IconButton(
                onClick = { 
                    onViewRotationChanged((viewRotation - 90f + 360f) % 360f) 
                    
                }
            ) {
                Icon(Icons.Default.RotateRight, contentDescription = "Rotate View", tint = AresTextSecondary)
            }
        }
    }
}
