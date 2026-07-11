package com.ares.analytics.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
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
        HorizontalDivider(color = AresBorder)

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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.constants) { const ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AresSurfaceElevated, RoundedCornerShape(8.dp))
                            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(
                                text = const.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AresTextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = const.filePath.split(java.io.File.separator).last(),
                                fontSize = 10.sp,
                                color = AresTextSecondary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var textValue by remember(const.value) { mutableStateOf(const.value.toString()) }
                            BasicTextField(
                                value = textValue,
                                onValueChange = { textValue = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AresTextPrimary),
                                cursorBrush = SolidColor(AresCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .background(AresSurface, RoundedCornerShape(6.dp))
                                    .border(1.dp, AresBorder, RoundedCornerShape(6.dp)),
                                decorationBox = { innerTextField ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            innerTextField()
                                        }
                                    }
                                }
                            )
                            Button(
                                onClick = {
                                    val newVal = textValue.toDoubleOrNull()
                                    if (newVal != null) {
                                        viewModel.onIntent(TuningIntent.SaveConstant(const, newVal))
                                    }
                                },
                                modifier = Modifier.height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AresCyan),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp)
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
