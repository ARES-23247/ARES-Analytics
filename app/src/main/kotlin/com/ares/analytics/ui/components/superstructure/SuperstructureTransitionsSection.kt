package com.ares.analytics.ui.components.superstructure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areslib.superstructure.*
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.superstructure.*
import kotlin.math.*

@Composable
fun SuperstructureTransitionsSection(
    state: SuperstructureStudioState,
    draft: SuperstructureDocument,
    viewModel: SuperstructureStudioViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Visual Transition Graph Canvas
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AresSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("VISUAL TRANSITION STATE GRAPH", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Drag nodes to arrange. Click a node to inspect its setpoints.", color = AresTextTertiary, fontSize = 10.sp)
                    }

                    var addTransitionOpen by remember { mutableStateOf(false) }
                    var srcState by remember { mutableStateOf(draft.initialStateId) }
                    var tgtState by remember { mutableStateOf(draft.states.firstOrNull { it.stateId != draft.initialStateId }?.stateId ?: draft.initialStateId) }
                    var actionKey by remember { mutableStateOf(state.parameterlessActions.firstOrNull()?.key.orEmpty()) }

                    if (!addTransitionOpen) {
                        Button(
                            onClick = { addTransitionOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Transition", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            StudioDropdown(
                                label = "From: $srcState",
                                options = draft.states.map { it.stateId to it.displayName },
                                onSelect = { srcState = it },
                                modifier = Modifier.width(130.dp),
                            )
                            StudioDropdown(
                                label = "To: $tgtState",
                                options = draft.states.map { it.stateId to it.displayName },
                                onSelect = { tgtState = it },
                                modifier = Modifier.width(130.dp),
                            )
                            Button(
                                onClick = {
                                    viewModel.addActionTransition(srcState, tgtState, actionKey.ifBlank { "action_${srcState}_$tgtState" })
                                    addTransitionOpen = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AresGreen, contentColor = AresOnAccent),
                                modifier = Modifier.height(36.dp),
                            ) {
                                Text("Add Route", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = { addTransitionOpen = false }, modifier = Modifier.height(36.dp)) {
                                Text("Cancel", fontSize = 11.sp)
                            }
                        }
                    }
                }

                StateflowGraphCanvas(
                    state = state,
                    draft = draft,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                )
            }
        }

        // Collision Guards & Interlocks and Lookup Tables (Side-by-Side 2 columns)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Left (50%): Interlock Guards
            Surface(
                modifier = Modifier.weight(1f),
                color = AresSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AresBorder),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = AresGold, modifier = Modifier.size(14.dp))
                            Text("COLLISION INTERLOCKS (${draft.interlocks.size})", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        if (state.targetFields.size >= 2) {
                            OutlinedButton(
                                onClick = {
                                    val src = state.sourceFields.firstOrNull()
                                    val tgt = state.targetFields.firstOrNull()
                                    if (src != null && tgt != null) {
                                        viewModel.addInterlock(src, tgt)
                                    }
                                },
                                modifier = Modifier.height(26.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(10.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Add Guard", fontSize = 10.sp)
                            }
                        }
                    }
                    if (draft.interlocks.isEmpty()) {
                        Text("No collision interlocks configured. Mechanisms move freely without positional guards.", color = AresTextTertiary, fontSize = 10.sp)
                    } else {
                        draft.interlocks.forEach { guard ->
                            Surface(
                                color = AresSurfaceElevated,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, AresBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(guard.description.ifBlank { guard.ruleId }, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("rule: ${guard.ruleId}", color = AresGold, fontSize = 9.sp)
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeInterlock(guard.ruleId) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = AresError, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right (50%): Lookup Tables (LUTs)
            Surface(
                modifier = Modifier.weight(1f),
                color = AresSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AresBorder),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TableChart, null, tint = AresCyan, modifier = Modifier.size(14.dp))
                            Text("LOOKUP TABLES / CURVES (${draft.luts.size})", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = viewModel::addLut,
                            modifier = Modifier.height(26.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Add LUT", fontSize = 10.sp)
                        }
                    }
                    if (draft.luts.isEmpty()) {
                        Text("No lookup tables configured. Use when output speeds depend on distance (e.g. shooter RPM).", color = AresTextTertiary, fontSize = 10.sp)
                    } else {
                        draft.luts.forEach { lut ->
                            Surface(
                                color = AresSurfaceElevated,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, AresBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(lut.displayName.ifBlank { lut.lutId }, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("${lut.controlPoints.size} sample points · ${lut.interpolation.name}", color = AresCyan, fontSize = 9.sp)
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeLut(lut.lutId) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, null, tint = AresError, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StateflowGraphCanvas(
    state: SuperstructureStudioState,
    draft: SuperstructureDocument,
    viewModel: SuperstructureStudioViewModel,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val nodeWidth = 180f
    val nodeHeight = 70f

    val nodePositions = remember(draft.states, draft.nodeLayouts) {
        val map = mutableMapOf<String, Offset>()
        val cols = 3
        draft.states.forEachIndexed { index, statePreset ->
            val layout = draft.nodeLayouts[statePreset.stateId]
            if (layout != null) {
                map[statePreset.stateId] = Offset(layout.x.toFloat(), layout.y.toFloat())
            } else {
                val col = index % cols
                val row = index / cols
                map[statePreset.stateId] = Offset(30f + col * 220f, 25f + row * 110f)
            }
        }
        map
    }

    var draggedNodeId by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        border = BorderStroke(1.dp, AresBorder),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(draft.states, draft.nodeLayouts) {
                        detectTapGestures { tapOffset ->
                            val clicked = nodePositions.entries.firstOrNull { (_, pos) ->
                                tapOffset.x >= pos.x && tapOffset.x <= pos.x + nodeWidth &&
                                tapOffset.y >= pos.y && tapOffset.y <= pos.y + nodeHeight
                            }
                            if (clicked != null) {
                                viewModel.selectState(clicked.key)
                            }
                        }
                    }
                    .pointerInput(draft.states, draft.nodeLayouts) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val hit = nodePositions.entries.firstOrNull { (_, pos) ->
                                    startOffset.x >= pos.x && startOffset.x <= pos.x + nodeWidth &&
                                    startOffset.y >= pos.y && startOffset.y <= pos.y + nodeHeight
                                }
                                if (hit != null) {
                                    draggedNodeId = hit.key
                                    viewModel.selectState(hit.key)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedNodeId?.let { id ->
                                    val currentPos = nodePositions[id] ?: Offset.Zero
                                    val newPos = currentPos + dragAmount
                                    val clampedX = max(10f, min(1400f, newPos.x))
                                    val clampedY = max(10f, min(900f, newPos.y))
                                    viewModel.moveStateNode(id, clampedX.toDouble(), clampedY.toDouble())
                                }
                            },
                            onDragEnd = { draggedNodeId = null },
                            onDragCancel = { draggedNodeId = null },
                        )
                    }
            ) {
                clipRect {
                    // Dot Grid
                    val dotSpacing = 24f
                    val dotColor = AresBorder.copy(alpha = 0.35f)
                    var x = 0f
                    while (x < size.width) {
                        var y = 0f
                        while (y < size.height) {
                            drawCircle(dotColor, radius = 1f, center = Offset(x, y))
                            y += dotSpacing
                        }
                        x += dotSpacing
                    }

                    // Directed Bezier Curves
                    draft.transitions.forEach { edge ->
                        val srcPos = nodePositions[edge.sourceStateId] ?: return@forEach
                        val tgtPos = nodePositions[edge.targetStateId] ?: return@forEach

                        val srcCenter = Offset(srcPos.x + nodeWidth / 2f, srcPos.y + nodeHeight / 2f)
                        val tgtCenter = Offset(tgtPos.x + nodeWidth / 2f, tgtPos.y + nodeHeight / 2f)

                        val dx = tgtCenter.x - srcCenter.x
                        val dy = tgtCenter.y - srcCenter.y
                        val dist = hypot(dx, dy)
                        if (dist < 1f) return@forEach

                        val nx = dx / dist
                        val ny = dy / dist

                        val startPt = Offset(srcCenter.x + nx * (nodeWidth / 2.2f), srcCenter.y + ny * (nodeHeight / 2.2f))
                        val endPt = Offset(tgtCenter.x - nx * (nodeWidth / 2.2f), tgtCenter.y - ny * (nodeHeight / 2.2f))

                        val perpX = -ny * 25f
                        val perpY = nx * 25f

                        val ctrl1 = Offset(startPt.x + dx * 0.35f + perpX, startPt.y + dy * 0.35f + perpY)
                        val ctrl2 = Offset(startPt.x + dx * 0.65f + perpX, startPt.y + dy * 0.65f + perpY)

                        val curveColor = when (edge.triggerKind) {
                            TransitionTriggerKind.ACTION_REQUEST -> AresCyan
                            TransitionTriggerKind.SENSOR_CONDITION_AUTO -> AresGreen
                            TransitionTriggerKind.TIME_ELAPSED -> AresGold
                        }

                        val path = Path().apply {
                            moveTo(startPt.x, startPt.y)
                            cubicTo(ctrl1.x, ctrl1.y, ctrl2.x, ctrl2.y, endPt.x, endPt.y)
                        }
                        drawPath(path, color = curveColor.copy(alpha = 0.85f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))

                        // Arrowhead
                        val arrowAngle = atan2(endPt.y - ctrl2.y, endPt.x - ctrl2.x)
                        val arrowSize = 9f
                        val p1 = endPt
                        val p2 = Offset(endPt.x - arrowSize * cos(arrowAngle - 0.45f), endPt.y - arrowSize * sin(arrowAngle - 0.45f))
                        val p3 = Offset(endPt.x - arrowSize * cos(arrowAngle + 0.45f), endPt.y - arrowSize * sin(arrowAngle + 0.45f))

                        val arrowPath = Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                            lineTo(p3.x, p3.y)
                            close()
                        }
                        drawPath(arrowPath, color = curveColor, style = Fill)
                    }

                    // Nodes
                    draft.states.forEach { statePreset ->
                        val pos = nodePositions[statePreset.stateId] ?: Offset.Zero
                        val isSelected = statePreset.stateId == state.selectedStateId
                        val isInitial = statePreset.stateId == draft.initialStateId
                        val isFault = statePreset.stateId == draft.faultStateId

                        val borderColor = when {
                            isSelected -> AresCyan
                            isInitial -> AresGreen
                            isFault -> AresError
                            else -> AresBorder
                        }

                        drawRoundRect(
                            color = AresSurface,
                            topLeft = pos,
                            size = Size(nodeWidth, nodeHeight),
                            cornerRadius = CornerRadius(8f, 8f),
                        )
                        drawRoundRect(
                            color = borderColor,
                            topLeft = pos,
                            size = Size(nodeWidth, nodeHeight),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(if (isSelected) 2.5f else 1.2f),
                        )

                        // Node Title
                        drawText(
                            textMeasurer,
                            statePreset.displayName.ifBlank { statePreset.stateId },
                            topLeft = Offset(pos.x + 10f, pos.y + 8f),
                            style = TextStyle(color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        )
                        // Target Count
                        drawText(
                            textMeasurer,
                            "${statePreset.subsystemTargets.size} targets",
                            topLeft = Offset(pos.x + 10f, pos.y + 32f),
                            style = TextStyle(color = AresTextSecondary, fontSize = 9.sp),
                        )
                    }
                }
            }
        }
    }
}
