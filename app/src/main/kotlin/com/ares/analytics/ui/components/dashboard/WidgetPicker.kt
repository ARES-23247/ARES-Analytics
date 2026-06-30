package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*

data class AvailableWidget(
    val type: String,
    val displayName: String,
    val description: String,
    val icon: ImageVector
)

val availableWidgetsList = listOf(
    AvailableWidget("runs_index", "Recorded Sessions", "List of practice runs, match logs, and annotation tagging.", Icons.Default.History),
    AvailableWidget("alerts", "Live Alerts", "Real-time warning notifications for battery, motor, and sensors.", Icons.Default.Warning),
    AvailableWidget("telemetry_chart", "Live Telemetry Chart", "Searchable scrolling multi-channel line chart.", Icons.Default.ShowChart),
    AvailableWidget("motor_health", "Motor Health", "Motor current draw gauges and stall warnings.", Icons.Default.ElectricBolt),
    AvailableWidget("vision_quality", "Vision & EKF Quality", "AprilTag verification rates and pose estimator health.", Icons.Default.Camera),
    AvailableWidget("ai_coach", "AI Forensics Coach", "Vertex AI automated pit-diagnostics coach recommendations.", Icons.Default.Psychology),
    AvailableWidget("match_schedule", "Match Schedule", "TBA / TOA match schedule calendar & sync integrations.", Icons.Default.EventNote),
    AvailableWidget("console_viewer", "Robot Console Viewer", "Live print logs and monospaced console history with regex search.", Icons.Default.Terminal),
    AvailableWidget("swerve_animator", "Swerve Visualizer", "Real-time vector graphics representing target vs actual swerve wheel states.", Icons.Default.DirectionsCar),
    AvailableWidget("joystick_visualizer", "Gamepad Monitor", "Real-time controller sticks, triggers, and button deflections.", Icons.Default.Gamepad),
    AvailableWidget("mechanism_visualizer", "Linkage Animator", "Real-time 2D rendering of arm angles and slide height extensions.", Icons.Default.Build),
    AvailableWidget("mecanum_visualizer", "Mecanum Force Visualizer", "Real-time wheel spin velocities and traction force vectors.", Icons.Default.Settings),
    AvailableWidget("field_viewer", "Field 2D Viewer", "Real-time 2D visualization of the robot's pose on the game field.", Icons.Default.Map),
    AvailableWidget("trends_card", "Battery Trends", "Multi-session battery voltage degradation trend lines and linear regression.", Icons.Default.TrendingDown),
    AvailableWidget("battery_health", "Battery Diagnostics", "Real-time battery voltage monitoring and brownout warnings.", Icons.Default.BatteryChargingFull),
    AvailableWidget("statistics_panel", "Signal Statistics", "Descriptive statistics, error forensics, and distribution histograms.", Icons.Default.Analytics)
)

@Composable
fun WidgetPicker(
    onDismiss: () -> Unit,
    onSelectWidget: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Widget to Dashboard",
                color = AresTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Select a telemetry or planning card to add to your custom layout grid:",
                    color = AresTextSecondary,
                    fontSize = 12.sp
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(availableWidgetsList) { widget ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AresSurfaceElevated)
                                .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectWidget(widget.type)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(widget.icon, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                                Text(
                                    widget.displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AresTextPrimary
                                )
                            }
                            Text(
                                widget.description,
                                fontSize = 10.sp,
                                color = AresTextTertiary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AresTextSecondary)
            }
        },
        containerColor = AresSurface,
        shape = RoundedCornerShape(12.dp)
    )
}
