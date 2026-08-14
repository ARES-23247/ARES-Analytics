package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.DriverCoachingReport
import com.ares.analytics.service.DriverReviewConfidence
import com.ares.analytics.ui.theme.*

@Composable
fun DriverCoachingCard(
    report: DriverCoachingReport,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automated Driver Coaching & Efficiency Forensics", color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    Text("Telemetry analysis measuring synchronized chassis motion, heading snaps, and input smoothness.", color = AresTextSecondary, fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when (report.confidence) {
                                DriverReviewConfidence.STRONG -> AresGreen.copy(alpha = 0.2f)
                                DriverReviewConfidence.LIMITED -> AresAmber.copy(alpha = 0.2f)
                                DriverReviewConfidence.INSUFFICIENT -> AresTextTertiary.copy(alpha = 0.2f)
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Confidence: ${report.confidence.name}",
                        fontSize = 10.sp,
                        color = when (report.confidence) {
                            DriverReviewConfidence.STRONG -> AresGreen
                            DriverReviewConfidence.LIMITED -> AresAmber
                            DriverReviewConfidence.INSUFFICIENT -> AresTextTertiary
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Stat Metrics Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Synchronized Samples", fontSize = 9.sp, color = AresTextTertiary)
                    Text(
                        "${report.synchronizedSampleCount}",
                        fontSize = 14.sp,
                        color = AresCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Spin + Translate", fontSize = 9.sp, color = AresTextTertiary)
                    Text(
                        "${"%.1f".format(report.simultaneousTranslationRotationFraction * 100)}%",
                        fontSize = 14.sp,
                        color = if (report.simultaneousTranslationRotationFraction <= 0.20) AresGreen else AresAmber,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Reversals / Min", fontSize = 9.sp, color = AresTextTertiary)
                    Text(
                        "${"%.0f".format(report.directionReversalRatePerMinute)}",
                        fontSize = 14.sp,
                        color = if (report.directionReversalRatePerMinute <= 35.0) AresGreen else AresGold,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Duration", fontSize = 9.sp, color = AresTextTertiary)
                    Text(
                        "${"%.1f".format(report.durationSeconds)}s",
                        fontSize = 14.sp,
                        color = AresTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Observations & Practice Ideas
            if (report.observations.isNotEmpty()) {
                Text("Coach Observations & Practice Ideas:", color = AresTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.observations.forEach { obs ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(AresSurface)
                                .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AresGold, modifier = Modifier.size(15.dp))
                                Text(obs.title, color = AresTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(obs.evidence, color = AresTextSecondary, fontSize = 11.sp)
                            Text("💡 ${obs.practiceIdea}", color = AresCyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
