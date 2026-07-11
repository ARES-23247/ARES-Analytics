package com.ares.analytics.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.TuningIntent
import com.ares.analytics.viewmodel.TuningViewModel

@Composable
fun TuningScreen(
    viewModel: TuningViewModel,
    projectPath: String
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(projectPath) {
        viewModel.onIntent(TuningIntent.LoadConstants(projectPath))
    }

    Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(AresSurface).border(1.dp, AresBorder, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Constants Tuning Board", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
        Divider(color = AresBorder)

        if (state.saveStatus.isNotEmpty()) {
            Text(state.saveStatus, color = AresGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            LaunchedEffect(state.saveStatus) {
                kotlinx.coroutines.delay(3000)
                viewModel.onIntent(TuningIntent.ClearSaveStatus)
            }
        }
        val error = state.errorMessage
        if (error != null) {
            Text(error, color = AresError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        if (state.constants.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No tunable constants found in project workspace. (Scans TunerConstants.kt/Constants.kt/RobotConfig.java)", color = AresTextTertiary, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.constants) { idx, const ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(AresSurfaceElevated).border(1.dp, AresBorder, RoundedCornerShape(8.dp)).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(const.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                            Text(const.filePath.split(java.io.File.separator).last(), fontSize = 11.sp, color = AresTextSecondary)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            var textValue by remember(const.value) { mutableStateOf(const.value.toString()) }
                            OutlinedTextField(
                                value = textValue,
                                onValueChange = { textValue = it },
                                modifier = Modifier.width(100.dp),
                                textStyle = androidx.compose.ui.text.font.FontFamily.Monospace.let { MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary) },
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    val newVal = textValue.toDoubleOrNull()
                                    if (newVal != null) {
                                        viewModel.onIntent(TuningIntent.SaveConstant(const, newVal))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Save", color = AresBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
