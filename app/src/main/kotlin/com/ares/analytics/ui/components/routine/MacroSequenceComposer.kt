package com.ares.analytics.ui.components.routine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

data class MacroStepModel(
    val stepId: String,
    val actionKey: String,
    val description: String,
    val waitCondition: String = "",
    val timeoutSeconds: Double = 2.0,
)

/**
 * Visual Compound Action & Macro Sequencer.
 *
 * Allows students to compose complex teleop macros (e.g., Score High Basket, Intake and Index)
 * across multiple subsystems into a deterministic state-machine sequence without writing code.
 */
@Composable
fun MacroSequenceComposer(
    macroName: String,
    steps: List<MacroStepModel>,
    onStepsChanged: (List<MacroStepModel>) -> Unit,
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
                Column {
                    Text(
                        text = "Macro Sequencer: $macroName",
                        color = AresTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Chain multiple mechanism actions into one automated driver button macro.",
                        color = AresTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            if (steps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AresBackground,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "No sequence steps yet. Click '+ Add Sequence Step' to build your multi-mechanism workflow.",
                        color = AresTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            steps.forEachIndexed { index, step ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AresBackground,
                        border = BorderStroke(1.dp, AresBorder),
                        modifier = Modifier.fillMaxWidth(),
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
                                        color = AresCyan.copy(alpha = 0.2f),
                                    ) {
                                        Text(
                                            text = "Step ${index + 1}",
                                            color = AresCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text(
                                        text = step.actionKey.ifBlank { "Unassigned action" },
                                        color = AresTextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        onStepsChanged(steps.filterIndexed { i, _ -> i != index })
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete step",
                                        tint = AresError,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = step.actionKey,
                                onValueChange = { newKey ->
                                    onStepsChanged(steps.mapIndexed { i, s -> if (i == index) s.copy(actionKey = newKey) else s })
                                },
                                label = { Text("Action Key (e.g. elevator.high, intake.reverse)") },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = step.waitCondition,
                                    onValueChange = { newCond ->
                                        onStepsChanged(steps.mapIndexed { i, s -> if (i == index) s.copy(waitCondition = newCond) else s })
                                    },
                                    label = { Text("Completion Condition (e.g. at_target, sensor_active)") },
                                    modifier = Modifier.weight(1f),
                                )

                                OutlinedTextField(
                                    value = step.timeoutSeconds.toString(),
                                    onValueChange = { newTimeout ->
                                        val parsed = newTimeout.toDoubleOrNull() ?: step.timeoutSeconds
                                        onStepsChanged(steps.mapIndexed { i, s -> if (i == index) s.copy(timeoutSeconds = parsed) else s })
                                    },
                                    label = { Text("Timeout (s)") },
                                    modifier = Modifier.weight(0.4f),
                                )
                            }
                        }
                    }

                    if (index < steps.size - 1) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = "Next step",
                            tint = AresCyan,
                            modifier = Modifier.padding(vertical = 4.dp).size(18.dp),
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val newStep = MacroStepModel(
                        stepId = "step_${steps.size + 1}",
                        actionKey = "",
                        description = "Next sequential mechanism action",
                        waitCondition = "at_target",
                        timeoutSeconds = 2.0,
                    )
                    onStepsChanged(steps + newStep)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("+ Add Sequence Step")
            }
        }
    }
}
