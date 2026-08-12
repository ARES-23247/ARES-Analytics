package com.ares.analytics.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresCyanGlow
import com.ares.analytics.ui.theme.AresRed
import com.ares.analytics.ui.theme.AresRedDark
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.viewmodel.OnboardingStep

@Composable
fun WelcomeStep(currentStep: OnboardingStep) {
    val heading = when (currentStep) {
        OnboardingStep.PROJECT -> "Choose your robot project"
        OnboardingStep.ROBOT -> "Check the robot details"
        OnboardingStep.OPTIONAL -> "Optional connections"
        OnboardingStep.REVIEW -> "Ready to finish"
    }
    val guidance = when (currentStep) {
        OnboardingStep.PROJECT -> "Pick the folder you use to build your robot. ARES will detect FTC or FRC and fill in anything it recognizes."
        OnboardingStep.ROBOT -> "Confirm the team, season, and robot. Detected values are already filled in and can be changed."
        OnboardingStep.OPTIONAL -> "Cloud sync and custom connection settings can be added now or later. The dashboard works fully offline."
        OnboardingStep.REVIEW -> "Review the workspace and make sure JDK 17 is ready. Nothing is uploaded unless you choose cloud sync."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(AresRed, AresRedDark))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PrecisionManufacturing, contentDescription = null, tint = AresTextPrimary)
            }
            Column {
                Text(
                    text = "ARES setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AresTextPrimary,
                )
                Text("Step ${currentStep.number} of ${OnboardingStep.entries.size}", color = AresTextSecondary, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnboardingStep.entries.forEach { step ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (step.ordinal <= currentStep.ordinal) AresCyan else AresSurfaceElevated),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(heading, color = AresTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(guidance, style = MaterialTheme.typography.bodyMedium, color = AresTextSecondary, lineHeight = 20.sp)
        }
    }
}
