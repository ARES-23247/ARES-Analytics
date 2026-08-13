package com.ares.analytics.ui.screens.onboarding

import com.ares.analytics.ui.theme.AresOnAccent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary

/** Final readiness check. ARES Analytics builds and simulator tools require exactly JDK 17. */
@Composable
fun JavaVerificationStep(
    isValid: Boolean?,
    isVerifying: Boolean,
    message: String,
    onVerifyClick: () -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isValid == false) AresError else AresBorder, RoundedCornerShape(8.dp))
            .background(AresSurfaceElevated)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("JDK 17", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            Text(
                if (isVerifying) "Checking the Java version..." else message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isValid == false) AresError else AresTextSecondary,
            )
        }
        Button(
            onClick = onVerifyClick,
            enabled = !isVerifying,
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
        ) {
            if (isVerifying) {
                CircularProgressIndicator(color = AresBackground, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AresBackground)
                Text("Check", color = AresBackground)
            }
        }
    }
}
