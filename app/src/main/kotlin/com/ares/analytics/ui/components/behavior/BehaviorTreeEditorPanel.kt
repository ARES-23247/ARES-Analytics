package com.ares.analytics.ui.components.behavior

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGold
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.areslib.behavior.BehaviorNodeDocument
import com.areslib.behavior.BehaviorNodeKind
import com.areslib.behavior.BehaviorTreeDocument
import com.areslib.subsystem.InterlockComparison

/**
 * Visual Behavior Tree & Dynamic Decision Graph Editor.
 *
 * Allows students to construct hierarchical conditional state machines and dynamic branch logic
 * (e.g. sensor-based branching, fallback retries, lane selections) without hand-authoring code.
 */
@Composable
fun BehaviorTreeEditorPanel(
    tree: BehaviorTreeDocument,
    onTreeChanged: (BehaviorTreeDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = BorderStroke(1.dp, AresBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.AccountTree, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                    Column {
                        Text(
                            text = "Behavior Tree: ${tree.displayName}",
                            color = AresTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Visual dynamic branching and sensory decision-making graph.",
                            color = AresTextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            BehaviorNodeCard(
                node = tree.rootNode,
                depth = 0,
                onNodeChanged = { updatedRoot ->
                    onTreeChanged(tree.copy(rootNode = updatedRoot))
                },
                onDelete = { /* Root node cannot be deleted */ },
                isRoot = true,
            )
        }
    }
}

@Composable
private fun BehaviorNodeCard(
    node: BehaviorNodeDocument,
    depth: Int,
    onNodeChanged: (BehaviorNodeDocument) -> Unit,
    onDelete: () -> Unit,
    isRoot: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AresBackground,
        border = BorderStroke(1.dp, if (isRoot) AresCyan.copy(alpha = 0.6f) else AresBorder),
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 12).dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (node.kind) {
                            BehaviorNodeKind.SELECTOR -> AresGold.copy(alpha = 0.2f)
                            BehaviorNodeKind.SEQUENCE -> AresCyan.copy(alpha = 0.2f)
                            BehaviorNodeKind.CONDITION -> AresGreen.copy(alpha = 0.2f)
                            BehaviorNodeKind.ACTION -> AresCyan.copy(alpha = 0.2f)
                            else -> AresSurface
                        },
                    ) {
                        Text(
                            text = node.kind.name,
                            color = when (node.kind) {
                                BehaviorNodeKind.SELECTOR -> AresGold
                                BehaviorNodeKind.SEQUENCE -> AresCyan
                                BehaviorNodeKind.CONDITION -> AresGreen
                                else -> AresTextPrimary
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    Text(
                        text = node.title.ifBlank { node.nodeId },
                        color = AresTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (!isRoot) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AresError, modifier = Modifier.size(15.dp))
                    }
                }
            }

            when (node.kind) {
                BehaviorNodeKind.CONDITION -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = node.targetField.orEmpty(),
                            onValueChange = { onNodeChanged(node.copy(targetField = it)) },
                            label = { Text("Sensory State Field (e.g. intake.color)") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = node.expectedStringValue ?: node.expectedDoubleValue?.toString() ?: "",
                            onValueChange = {
                                val num = it.toDoubleOrNull()
                                if (num != null) {
                                    onNodeChanged(node.copy(expectedDoubleValue = num, expectedStringValue = null))
                                } else {
                                    onNodeChanged(node.copy(expectedStringValue = it, expectedDoubleValue = null))
                                }
                            },
                            label = { Text("Expected Value (e.g. YELLOW)") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                BehaviorNodeKind.ACTION -> {
                    OutlinedTextField(
                        value = node.actionKey.orEmpty(),
                        onValueChange = { onNodeChanged(node.copy(actionKey = it)) },
                        label = { Text("Subsystem Action Key (e.g. elevator.high_basket)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BehaviorNodeKind.SEQUENCE, BehaviorNodeKind.SELECTOR, BehaviorNodeKind.PARALLEL -> {
                    if (node.children.isEmpty()) {
                        Text("No child branches. Add a Condition or Action child below.", color = AresTextSecondary, fontSize = 11.sp)
                    } else {
                        node.children.forEachIndexed { index, child ->
                            BehaviorNodeCard(
                                node = child,
                                depth = 1,
                                onNodeChanged = { updatedChild ->
                                    onNodeChanged(node.copy(children = node.children.mapIndexed { i, c -> if (i == index) updatedChild else c }))
                                },
                                onDelete = {
                                    onNodeChanged(node.copy(children = node.children.filterIndexed { i, _ -> i != index }))
                                },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val newCondition = BehaviorNodeDocument(
                                    nodeId = "cond_${System.currentTimeMillis() % 10000}",
                                    kind = BehaviorNodeKind.CONDITION,
                                    title = "Check Condition",
                                    targetField = "intake.color",
                                    expectedStringValue = "YELLOW",
                                )
                                onNodeChanged(node.copy(children = node.children + newCondition))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("+ Condition", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val newAction = BehaviorNodeDocument(
                                    nodeId = "act_${System.currentTimeMillis() % 10000}",
                                    kind = BehaviorNodeKind.ACTION,
                                    title = "Execute Action",
                                    actionKey = "intake.grab",
                                )
                                onNodeChanged(node.copy(children = node.children + newAction))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("+ Action", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val newSequence = BehaviorNodeDocument(
                                    nodeId = "seq_${System.currentTimeMillis() % 10000}",
                                    kind = BehaviorNodeKind.SEQUENCE,
                                    title = "Sub-Sequence",
                                )
                                onNodeChanged(node.copy(children = node.children + newSequence))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("+ Sequence", fontSize = 11.sp)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
