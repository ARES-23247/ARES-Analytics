package com.ares.analytics.ui.components.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.DeployExecutionPhase
import com.ares.analytics.service.DeployExecutionState
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary

/**
 * 1-Click Wireless ADB and OTA Robot Deployment Dialog.
 *
 * Provides real-time visual progress through the connection, compilation, and APK flashing stages
 * without requiring the student to open a command line or Android Studio.
 */
@Composable
fun OneClickDeployDialog(
    state: DeployExecutionState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (state.phase != DeployExecutionPhase.CONNECTING &&
                state.phase != DeployExecutionPhase.BUILDING &&
                state.phase != DeployExecutionPhase.INSTALLING
            ) {
                onDismiss()
            }
        },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (state.phase) {
                        DeployExecutionPhase.CONNECTING -> Icons.Default.Wifi
                        DeployExecutionPhase.BUILDING, DeployExecutionPhase.INSTALLING -> Icons.Default.CloudUpload
                        DeployExecutionPhase.SUCCEEDED -> Icons.Default.CheckCircle
                        DeployExecutionPhase.FAILED, DeployExecutionPhase.CANCELED -> Icons.Default.Error
                        DeployExecutionPhase.IDLE -> Icons.Default.HourglassTop
                    },
                    contentDescription = null,
                    tint = when (state.phase) {
                        DeployExecutionPhase.SUCCEEDED -> AresGreen
                        DeployExecutionPhase.FAILED -> AresError
                        else -> AresCyan
                    },
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = when (state.phase) {
                        DeployExecutionPhase.CONNECTING -> "Connecting to Robot..."
                        DeployExecutionPhase.BUILDING -> "Compiling Robot Binary..."
                        DeployExecutionPhase.INSTALLING -> "Flashing Code Over Wi-Fi..."
                        DeployExecutionPhase.SUCCEEDED -> "Deployment Complete!"
                        DeployExecutionPhase.FAILED -> "Deployment Failed"
                        DeployExecutionPhase.CANCELED -> "Deployment Canceled"
                        DeployExecutionPhase.IDLE -> "Deploy to Robot"
                    },
                    color = AresTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.message,
                    color = AresTextSecondary,
                    fontSize = 13.sp,
                )

                if (state.phase == DeployExecutionPhase.CONNECTING ||
                    state.phase == DeployExecutionPhase.BUILDING ||
                    state.phase == DeployExecutionPhase.INSTALLING
                ) {
                    LinearProgressIndicator(
                        progress = { state.progressPercent },
                        modifier = Modifier.fillMaxWidth(),
                        color = AresCyan,
                        trackColor = AresBackground,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AresBackground,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DeployStepItem(
                            stepName = "1. Connect over Wi-Fi (ADB 5555 / SSH)",
                            isDone = state.phase.ordinal > DeployExecutionPhase.CONNECTING.ordinal,
                            isRunning = state.phase == DeployExecutionPhase.CONNECTING,
                        )
                        DeployStepItem(
                            stepName = "2. Compile robot binary (Gradle wrapper)",
                            isDone = state.phase.ordinal > DeployExecutionPhase.BUILDING.ordinal,
                            isRunning = state.phase == DeployExecutionPhase.BUILDING,
                        )
                        DeployStepItem(
                            stepName = "3. Install & launch APK / binary on robot",
                            isDone = state.phase == DeployExecutionPhase.SUCCEEDED,
                            isRunning = state.phase == DeployExecutionPhase.INSTALLING,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.phase == DeployExecutionPhase.SUCCEEDED ||
                state.phase == DeployExecutionPhase.FAILED ||
                state.phase == DeployExecutionPhase.CANCELED
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                ) {
                    Text("Close")
                }
            } else {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel Deploy")
                }
            }
        },
        containerColor = AresSurface,
    )
}

@Composable
private fun DeployStepItem(stepName: String, isDone: Boolean, isRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stepName,
            color = when {
                isDone -> AresGreen
                isRunning -> AresCyan
                else -> AresTextSecondary
            },
            fontSize = 12.sp,
            fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
        )
        if (isDone) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AresGreen, modifier = Modifier.size(16.dp))
        } else if (isRunning) {
            CircularProgressIndicator(color = AresCyan, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
    }
}
