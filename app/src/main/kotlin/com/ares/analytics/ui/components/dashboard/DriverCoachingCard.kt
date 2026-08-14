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
                    Text("Telemetry analysis measuring driver wheel scrub, input smoothness, and cycle times.", color = AresTextSecondary, fontSize = 12.sp)
                }
            }

            // Stat Metrics Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Efficiency Score", fontSize = 9.sp, color = AresTextTertiary)
                    Text(
                        "${"%.0f".format(report.energyEfficiencyScore)}%",
                        fontSize = 14.sp,
                        color = if (report.energyEfficiencyScore >= 80.0) AresGreen else AresAmber,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Wheel Scrub", fontSize = 9.sp, color = AresTextTertiary)
                    Text(
                        "${"%.1f".format(report.scrubRatio * 100)}%",
                        fontSize = 14.sp,
                        color = if (report.scrubRatio <= 0.15) AresGreen else AresError,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Reversals / Min", fontSize = 9.sp, color = AresTextTertiary)
                    Text(
                        "${"%.0f".format(report.reversalRatePerMinute)}",
                        fontSize = 14.sp,
                        color = if (report.reversalRatePerMinute <= 35.0) AresGreen else AresGold,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Avg Cycle Time", fontSize = 9.sp, color = AresTextTertiary)
                    Text(
                        "${"%.1f".format(report.averageCycleTimeSeconds)}s",
                        fontSize = 14.sp,
                        color = AresCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Coaching Recommendations
            Text("Automated Coach Feedback:", color = AresTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.coachingRecommendations.forEach { rec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(AresSurface)
                            .border(1.dp, AresBorder, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AresGold, modifier = Modifier.size(16.dp))
                        Text(rec, color = AresTextPrimary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
    }
}
