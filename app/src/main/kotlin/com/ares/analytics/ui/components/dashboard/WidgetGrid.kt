package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.DashboardLayoutConfig
import com.ares.analytics.service.WidgetConfig
import com.ares.analytics.ui.theme.*
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun WidgetGrid(
    widgets: List<WidgetConfig>,
    onLayoutChanged: (List<WidgetConfig>) -> Unit,
    onAddWidget: (String) -> Unit,
    onRemoveWidget: (String) -> Unit,
    widgetBuilders: Map<String, @Composable (WidgetConfig, Modifier) -> Unit>,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val colWidth = 120.dp
    val rowHeight = 80.dp
    val spacing = 16.dp
    val maxRow = widgets.maxOfOrNull { it.row + it.rowSpan } ?: 0
    val gridHeight = rowHeight * maxRow + spacing * (maxRow - 1).coerceAtLeast(0) + 120.dp
    val maxCol = widgets.maxOfOrNull { it.col + it.colSpan } ?: 12
    val gridWidth = colWidth * maxCol + spacing * (maxCol - 1).coerceAtLeast(0) + 64.dp
    val currentWidgets by rememberUpdatedState(widgets)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .width(gridWidth)
                    .height(gridHeight)
                    .padding(bottom = 80.dp)
            ) {
            widgets.forEach { widget ->
                key(widget.id) {
                    var offsetX by remember { mutableStateOf(0f) }
                    var offsetY by remember { mutableStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }
                var resizeWidthOffset by remember { mutableStateOf(0f) }
                var resizeHeightOffset by remember { mutableStateOf(0f) }
                var isResizing by remember { mutableStateOf(false) }
                val builder = widgetBuilders[widget.type]
                if (builder != null) {
                    val w = colWidth * widget.colSpan + spacing * (widget.colSpan - 1) + (resizeWidthOffset / density).dp
                    val h = rowHeight * widget.rowSpan + spacing * (widget.rowSpan - 1) + (resizeHeightOffset / density).dp
                    val x = colWidth * widget.col + spacing * widget.col
                    val y = rowHeight * widget.row + spacing * widget.row

                    Box(
                        modifier = Modifier
                            .absoluteOffset(
                                x = x + (offsetX / density).dp,
                                y = y + (offsetY / density).dp
                            )
                            .size(width = w, height = h)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AresSurface)
                            .border(
                                width = if (isDragging || isResizing) 2.dp else 1.dp,
                                color = if (isDragging || isResizing) AresCyan else AresBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        // Drag Handle Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .then(
                                    if (widget.isLocked) Modifier
                                    else Modifier.pointerInput(widget) {
                                        detectDragGestures(
                                            onDragStart = { isDragging = true },
                                            onDragEnd = {
                                                isDragging = false
                                                val deltaCol = ((offsetX / density) / 120f).roundToInt()
                                                val deltaRow = ((offsetY / density) / 80f).roundToInt()
                                                offsetX = 0f
                                                offsetY = 0f
                                                if (deltaCol != 0 || deltaRow != 0) {
                                                    val newCol = (widget.col + deltaCol).coerceIn(0, 18 - widget.colSpan)
                                                    val newRow = (widget.row + deltaRow).coerceIn(0, 30)
                                                    val updated = currentWidgets.map {
                                                        if (it.id == widget.id) it.copy(col = newCol, row = newRow) else it
                                                    }
                                                    onLayoutChanged(resolveOverlaps(updated, widget.id))
                                                }
                                            },
                                            onDragCancel = {
                                                isDragging = false
                                                offsetX = 0f
                                                offsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                offsetX += dragAmount.x
                                                offsetY += dragAmount.y
                                            }
                                        )
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DragIndicator, contentDescription = "Drag", tint = AresTextTertiary)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { 
                                            val updated = widgets.map {
                                                if (it.id == widget.id) it.copy(isLocked = !it.isLocked) else it
                                            }
                                            onLayoutChanged(updated)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            if (widget.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, 
                                            contentDescription = "Toggle Lock", 
                                            tint = if (widget.isLocked) AresCyan else AresTextTertiary, 
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onRemoveWidget(widget.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = AresError, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Widget Content (leaving space for drag header)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 36.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
                        ) {
                            builder(widget, Modifier.fillMaxSize())
                        }

                        // Resize Handle (bottom right)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .then(
                                    if (widget.isLocked) Modifier
                                    else Modifier.pointerInput(widget) {
                                        detectDragGestures(
                                            onDragStart = { isResizing = true },
                                            onDragEnd = {
                                                isResizing = false
                                                val deltaColSpan = ((resizeWidthOffset / density) / 120f).roundToInt()
                                                val deltaRowSpan = ((resizeHeightOffset / density) / 80f).roundToInt()
                                                resizeWidthOffset = 0f
                                                resizeHeightOffset = 0f
                                                if (deltaColSpan != 0 || deltaRowSpan != 0) {
                                                    val newColSpan = (widget.colSpan + deltaColSpan).coerceIn(1, 18 - widget.col)
                                                    val newRowSpan = (widget.rowSpan + deltaRowSpan).coerceIn(1, 12)
                                                    val updated = currentWidgets.map {
                                                        if (it.id == widget.id) it.copy(colSpan = newColSpan, rowSpan = newRowSpan) else it
                                                    }
                                                    onLayoutChanged(resolveOverlaps(updated, widget.id))
                                                }
                                            },
                                            onDragCancel = {
                                                isResizing = false
                                                resizeWidthOffset = 0f
                                                resizeHeightOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                resizeWidthOffset += dragAmount.x
                                                resizeHeightOffset += dragAmount.y
                                            }
                                        )
                                    }
                                )
                                .padding(4.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Icon(
                                Icons.Default.AspectRatio,
                                contentDescription = "Resize",
                                tint = if (isResizing) AresCyan else AresTextTertiary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                }
            }
        }
    }

        // Floating Action Buttons for layout picker & reset
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = { onAddWidget("") },
                containerColor = AresCyan,
                contentColor = AresBackground,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Widget")
            }
        }
    }
}

/**
 * Ensures that no widgets in the layout grid overlap, pushing colliding items down.
 * The active widget has placement priority.
 */
private fun resolveOverlaps(widgets: List<WidgetConfig>, activeWidgetId: String): List<WidgetConfig> {
    val active = widgets.find { it.id == activeWidgetId } ?: return widgets
    val others = widgets.filter { it.id != activeWidgetId }
    val resolved = mutableListOf<WidgetConfig>()

    // 1. Locked widgets are completely stationary, they get placed first
    val lockedOthers = others.filter { it.isLocked }
    resolved.addAll(lockedOthers)

    // Helper to check overlap
    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun hasOverlap(w: WidgetConfig): Boolean {
        return resolved.any { placed ->
            val overlapX = w.col < placed.col + placed.colSpan && w.col + w.colSpan > placed.col
            val overlapY = w.row < placed.row + placed.rowSpan && w.row + w.rowSpan > placed.row
            overlapX && overlapY
        }
    }

    // 2. Add the active widget, resolving its position around locked widgets
    var currentActive = active
    while (hasOverlap(currentActive)) {
        currentActive = currentActive.copy(row = currentActive.row + 1)
    }
    resolved.add(currentActive)

    // 3. Add unlocked others, resolving around locked AND active widgets
    val unlockedOthers = others.filter { !it.isLocked }.sortedWith(
        compareBy<WidgetConfig> { it.row }.thenBy { it.col }
    )
    for (widget in unlockedOthers) {
        var currentWidget = widget
        while (hasOverlap(currentWidget)) {
            currentWidget = currentWidget.copy(row = currentWidget.row + 1)
        }
        resolved.add(currentWidget)
    }

    return resolved
}
