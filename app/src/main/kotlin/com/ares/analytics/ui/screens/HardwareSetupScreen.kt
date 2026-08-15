package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
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
import com.ares.analytics.service.hardware.HardwareInventoryItem
import com.ares.analytics.service.hardware.HardwareInventoryOwner
import com.ares.analytics.service.hardware.HardwareIssueSeverity
import com.ares.analytics.service.hardware.HardwareReviewStatus
import com.ares.analytics.service.hardware.HardwareSetupSnapshot
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

/** Descriptor-backed review of every physical address before a project can become deployable. */
@Composable
fun HardwareSetupScreen(
    viewModel: HardwareSetupViewModel,
    onOpenDrivebase: () -> Unit,
    onOpenSubsystems: () -> Unit,
    onBackToStudio: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HardwareSetupHeader(state, viewModel::refresh, onBackToStudio)
        }
        state.error?.let { error -> item { MessageCard("Hardware Setup error", error, HardwareIssueSeverity.ERROR) } }
        if (state.loading) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = AresCyan)
                    Spacer(Modifier.width(10.dp))
                    Text("Reading canonical hardware descriptors…", color = AresTextSecondary)
                }
            }
        }
        state.snapshot?.let { snapshot ->
            item { ReviewStatusCard(snapshot) }
            snapshot.issues.forEach { issue ->
                item { MessageCard(if (issue.itemUid == null) "Project check" else "Device check", issue.message, issue.severity) }
            }
            item {
                SourceActions(onOpenDrivebase, onOpenSubsystems)
            }
            HardwareInventoryOwner.entries.forEach { owner ->
                val owned = snapshot.items.filter { it.owner == owner }
                if (owned.isNotEmpty()) {
                    item {
                        Text(
                            if (owner == HardwareInventoryOwner.DRIVEBASE) "Drivebase hardware" else "Subsystem hardware",
                            color = AresTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(owned, key = HardwareInventoryItem::uid) { item -> HardwareItemCard(item) }
                }
            }
            item {
                ReviewChecklist(state, viewModel)
            }
        }
    }
}

@Composable
private fun HardwareSetupHeader(
    state: HardwareSetupState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Robot Studio")
                }
                Text("Hardware Setup", color = AresTextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRefresh, enabled = !state.loading && !state.saving) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Refresh")
                }
            }
            Text(
                "Compare canonical drivetrain and subsystem addresses with the actual robot. This screen reads those documents; edit them in their owning builders.",
                color = AresTextSecondary,
                lineHeight = 20.sp,
            )
            Surface(color = AresAmber.copy(alpha = 0.10f), border = BorderStroke(1.dp, AresAmber), shape = RoundedCornerShape(8.dp)) {
                Text(
                    "Recording a review is not a hardware test. It proves only that a named person compared wiring, addresses, directions, safe outputs, and limits with the current descriptor hashes.",
                    color = AresTextPrimary,
                    modifier = Modifier.fillMaxWidth().padding(11.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun ReviewStatusCard(snapshot: HardwareSetupSnapshot) {
    val (label, explanation, tint) = when (snapshot.reviewStatus) {
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
            "Inspect every required device and complete the checklist below.",
            AresAmber,
        )
    }
    Surface(color = tint.copy(alpha = 0.09f), border = BorderStroke(1.dp, tint), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (snapshot.reviewStatus == HardwareReviewStatus.CURRENT) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = tint,
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = AresTextPrimary, fontWeight = FontWeight.Bold)
                Text(explanation, color = AresTextSecondary, fontSize = 12.sp)
                Text("Inventory hash ${snapshot.inventoryHash.take(16)}…", color = AresTextTertiary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SourceActions(onOpenDrivebase: () -> Unit, onOpenSubsystems: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Change a name, address, direction, or safety setting", color = AresTextPrimary, fontWeight = FontWeight.Bold)
            Text("Use the owning builder so there is still one canonical source of truth.", color = AresTextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenDrivebase) { Text("Open Drivebase Builder") }
                OutlinedButton(onClick = onOpenSubsystems) { Text("Open Subsystem Builder") }
            }
        }
    }
}

@Composable
private fun HardwareItemCard(item: HardwareInventoryItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.displayName, color = AresTextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (item.required) "REQUIRED" else "OPTIONAL", color = if (item.required) AresAmber else AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text("${item.ownerDisplayName} · ${item.role}", color = AresTextSecondary, fontSize = 12.sp)
            Text(item.addressDescription, color = AresTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text(
                if (item.inverted) "Direction: reversed at the hardware boundary" else "Direction: normal at the hardware boundary",
                color = AresTextSecondary,
                fontSize = 11.sp,
            )
            Text(item.sourcePath, color = AresTextTertiary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ReviewChecklist(state: HardwareSetupState, viewModel: HardwareSetupViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Record a physical mapping review", color = AresTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Complete this beside the disabled robot with power removed where your team procedure requires it.", color = AresTextSecondary)
            HorizontalDivider(color = AresBorder)
            ReviewCheck("Every listed device exists on this robot and the wiring diagram matches.", state.wiringMatched, viewModel::setWiringMatched)
            ReviewCheck("Hardware-map names, CAN IDs/buses, and channels match the controller configuration.", state.addressesChecked, viewModel::setAddressesChecked)
            ReviewCheck("Motor/servo directions and follower relationships were checked mechanically.", state.directionsChecked, viewModel::setDirectionsChecked)
            ReviewCheck("Every actuator has a safe neutral and disabled/stop behavior was reviewed.", state.neutralOutputsChecked, viewModel::setNeutralOutputsChecked)
            ReviewCheck("Current, soft, motion, homing, and feedback limits were reviewed where applicable.", state.limitsChecked, viewModel::setLimitsChecked)
            OutlinedTextField(
                value = state.reviewerName,
                onValueChange = viewModel::setReviewerName,
                label = { Text("Reviewed by") },
                supportingText = { Text("Student or mentor name; this does not create a cloud role or claim a hardware test.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::saveReview,
                enabled = state.canSaveReview,
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = AresOnAccent)
                    Spacer(Modifier.width(7.dp))
                }
                Text(if (state.saving) "Recording review…" else "Record reviewed mapping", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReviewCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = AresTextPrimary, modifier = Modifier.padding(top = 11.dp), lineHeight = 19.sp)
    }
}

@Composable
private fun MessageCard(title: String, message: String, severity: HardwareIssueSeverity) {
    val tint = when (severity) {
        HardwareIssueSeverity.INFO -> AresCyan
        HardwareIssueSeverity.WARNING -> AresAmber
        HardwareIssueSeverity.ERROR -> AresRed
    }
    Surface(color = tint.copy(alpha = 0.08f), border = BorderStroke(1.dp, tint), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(if (severity == HardwareIssueSeverity.ERROR) Icons.Default.Error else Icons.Default.Warning, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(message, color = AresTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}
