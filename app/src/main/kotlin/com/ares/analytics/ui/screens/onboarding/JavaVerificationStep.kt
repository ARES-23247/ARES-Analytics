package com.ares.analytics.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.ui.theme.*

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun JavaVerificationStep(
    isValid: Boolean?,
    isVerifying: Boolean,
    message: String,
    onVerifyClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
            .background(AresSurfaceElevated)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            val icon = when (isValid) {
                true -> Icons.Default.CheckCircle
                false -> Icons.Default.Error
                null -> Icons.Default.HourglassEmpty
            }
            val tint = when (isValid) {
                true -> AresGreen
                false -> AresError
                null -> AresTextTertiary
            }
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Column {
                Text(
                    "JAVA_HOME Verification",
                    style = MaterialTheme.typography.labelMedium,
                    color = AresTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isVerifying) "Verifying JVM toolchain..." else message.take(50) + "...",
                    style = MaterialTheme.typography.labelSmall,
                    color = AresTextSecondary
                )
            }
        }

        IconButton(onClick = onVerifyClick) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry Verification", tint = AresCyan)
        }
    }
}
