package com.ares.analytics.ui.components.hardware

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ares.analytics.service.hardware.HardwareInventoryItem
import com.ares.analytics.service.hardware.HardwareInventoryOwner
import com.ares.analytics.service.hardware.HardwareIssueSeverity
import com.ares.analytics.service.hardware.HardwareReviewStatus
import com.ares.analytics.ui.theme.AresAmber
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresRed
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary
import com.ares.analytics.viewmodel.hardware.HardwareSetupState
import com.ares.analytics.viewmodel.hardware.HardwareSetupViewModel

/**
 * Modal dialog for reviewing physical hardware port and CAN address allocations without leaving Robot Studio.
 */
@Composable
fun HardwareSetupModal(
    isOpen: Boolean,
    viewModel: HardwareSetupViewModel,
    onDismiss: () -> Unit,
    onOpenDrivebase: () -> Unit,
    onOpenSubsystems: () -> Unit,
) {
    if (!isOpen) return

    val state by viewModel.state.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 880.dp)
                .heightIn(max = 780.dp)
                .fillMaxWidth(0.94f)
                .padding(16.dp),
            shape = RoundedCornerShape(14.dp),
            color = AresSurface,
            border = BorderStroke(1.dp, AresBorder),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AresCyan.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = AresCyan, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("Robot Hardware & Port Setup", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Physical address, CAN ID, direction, and port review", color = AresTextSecondary, fontSize = 12.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = viewModel::refresh, enabled = !state.loading && !state.saving) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AresTextSecondary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = AresTextSecondary)
                        }
                    }
                }

                HorizontalDivider(color = AresBorder)

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AresCyan, strokeWidth = 2.dp)
                            Text("Reading canonical hardware descriptors…", color = AresTextSecondary)
                        }
                    } else {
                        state.error?.let { err ->
                            Surface(
                                color = AresRed.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, AresRed),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(err, color = AresTextPrimary, modifier = Modifier.fillMaxWidth().padding(10.dp), fontSize = 12.sp)
                            }
                        }

                        state.snapshot?.let { snapshot ->
                            // Status Banner
                            val (statusLabel, statusDesc, statusColor) = when (snapshot.reviewStatus) {
                                HardwareReviewStatus.CURRENT -> Triple(
                                    "Current reviewed mapping",
                                    "${snapshot.reviewedBy.orEmpty()} reviewed this exact ${snapshot.items.size}-device inventory.",
                                    AresGreen,
                                )
                                HardwareReviewStatus.STALE -> Triple(
                                    "Review is stale",
                                    "A drivetrain or subsystem descriptor changed after the last review. Compare the updated mapping again.",
                                    AresAmber,
                                )
                                HardwareReviewStatus.INVALID -> Triple(
                                    "Review record is invalid",
                                    "Repair the reported issue, then record a new review.",
                                    AresRed,
                                )
                                HardwareReviewStatus.NOT_REVIEWED -> Triple(
                                    "Not physically reviewed",
                                    "Inspect every required device and complete the checklist below before physical testing.",
                                    AresAmber,
                                )
                            }
                            Surface(
                                color = statusColor.copy(alpha = 0.09f),
                                border = BorderStroke(1.dp, statusColor),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (snapshot.reviewStatus == HardwareReviewStatus.CURRENT) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(statusLabel, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(statusDesc, color = AresTextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }

                            // Issues
                            snapshot.issues.forEach { issue ->
                                Surface(
                                    color = (if (issue.severity == HardwareIssueSeverity.ERROR) AresRed else AresAmber).copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, if (issue.severity == HardwareIssueSeverity.ERROR) AresRed else AresAmber),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = if (issue.severity == HardwareIssueSeverity.ERROR) AresRed else AresAmber, modifier = Modifier.size(16.dp))
                                        Text(issue.message, color = AresTextPrimary, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Device Inventory
                            HardwareInventoryOwner.entries.forEach { owner ->
                                val owned = snapshot.items.filter { it.owner == owner }
                                if (owned.isNotEmpty()) {
                                    Text(
                                        if (owner == HardwareInventoryOwner.DRIVEBASE) "🏎️ Drivetrain Devices" else "⚙️ Mechanism Devices",
                                        color = AresTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                    owned.forEach { item ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                                            border = BorderStroke(1.dp, AresBorder),
                                            shape = RoundedCornerShape(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(item.displayName, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text("· ${item.role}", color = AresTextSecondary, fontSize = 11.sp)
                                                    }
                                                    Text(item.addressDescription, color = AresCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                                }
                                                Surface(
                                                    color = AresSurface,
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(1.dp, AresBorder),
                                                ) {
                                                    Text(
                                                        if (item.inverted) "REVERSED" else "NORMAL",
                                                        color = if (item.inverted) AresAmber else AresTextTertiary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Sign-off section
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
                                border = BorderStroke(1.dp, AresBorder),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Record a physical mapping review", color = AresTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Complete this beside the disabled robot with power removed where required.", color = AresTextSecondary, fontSize = 11.sp)
                                    HorizontalDivider(color = AresBorder)

                                    ReviewModalCheck("Every listed device exists on this robot and wiring matches.", state.wiringMatched, viewModel::setWiringMatched)
                                    ReviewModalCheck("Hardware-map names, CAN IDs/buses, and channels match controller config.", state.addressesChecked, viewModel::setAddressesChecked)
                                    ReviewModalCheck("Motor/servo directions and follower relationships checked mechanically.", state.directionsChecked, viewModel::setDirectionsChecked)
                                    ReviewModalCheck("Every actuator has safe neutral / stop behavior reviewed.", state.neutralOutputsChecked, viewModel::setNeutralOutputsChecked)
                                    ReviewModalCheck("Current, soft, motion, homing, and feedback limits reviewed.", state.limitsChecked, viewModel::setLimitsChecked)

                                    OutlinedTextField(
                                        value = state.reviewerName,
                                        onValueChange = viewModel::setReviewerName,
                                        label = { Text("Reviewed by (Student or mentor name)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Button(
                                        onClick = viewModel::saveReview,
                                        enabled = state.canSaveReview && !state.saving,
                                        colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (state.saving) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AresOnAccent)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(if (state.saving) "Recording review…" else "Record reviewed mapping", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer
                HorizontalDivider(color = AresBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            onDismiss()
                            onOpenDrivebase()
                        }) {
                            Text("Open Drivebase Builder")
                        }
                        OutlinedButton(onClick = {
                            onDismiss()
                            onOpenSubsystems()
                        }) {
                            Text("Open Subsystem Builder")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewModalCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = AresTextPrimary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}
