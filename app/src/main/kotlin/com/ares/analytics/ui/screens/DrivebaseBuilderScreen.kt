package com.ares.analytics.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.drivebase.*
import com.ares.analytics.shared.League
import com.ares.analytics.ui.components.core.AresInspectorDrawer
import com.ares.analytics.ui.components.core.AresSpecRow
import com.ares.analytics.ui.components.core.AresSpecSection
import com.ares.analytics.ui.components.core.AresSpecSummaryModal
import com.ares.analytics.ui.theme.*
import com.ares.analytics.viewmodel.drivebase.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DrivebaseBuilderScreen(viewModel: DrivebaseBuilderViewModel, onBackToStudio: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var showSpecSummaryModal by remember { mutableStateOf(false) }
    var showAiAssistantDrawer by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Drivebase Builder", color = AresTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Describe how the robot moves, localizes, stops safely, and is calibrated.", color = AresTextSecondary, fontSize = 12.sp)
                    Text("${state.league.name} PROJECT · ${state.draft.kind.runtimeSupportLabel(state.league)}", color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Drafts never command hardware. Saving requires a content-hash-bound reviewed diff and creates a history backup.", color = AresGold, fontSize = 11.sp)
                    Text("PROJECT · ${state.projectPath}", color = AresTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("CANONICAL · .ares/drivetrains/${state.draft.documentId}.aresdrivetrain", color = AresTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("GENERATED · robot module build/generated/ares/drivebase (never hand-edit)", color = AresTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showAiAssistantDrawer = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AresCyan),
                        border = BorderStroke(1.dp, AresCyan.copy(alpha = 0.6f)),
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = AresCyan)
                        Spacer(Modifier.width(5.dp))
                        Text("AI Assistant")
                    }
                    OutlinedButton(onClick = { showSpecSummaryModal = true }) {
                        Icon(Icons.Default.TableChart, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Spec Summary")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(state.advanced, { viewModel.onIntent(DrivebaseBuilderIntent.SetAdvanced(it)) }, enabled = !state.loading)
                        Text(" Advanced", color = AresTextPrimary, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { viewModel.onIntent(DrivebaseBuilderIntent.Reload) }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, "Reload drivebase document", Modifier.size(16.dp)); Text(" Reload")
                    }
                    Button(onClick = { viewModel.onIntent(DrivebaseBuilderIntent.ReviewSave) }, enabled = !state.loading && state.dirty, colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent)) {
                        Text("Review changes")
                    }
                }
            }
            state.error?.let { StatusBanner(it, AresError) }
            if (state.status.isNotBlank()) StatusBanner(state.status, AresGreen)
            if (state.draft.kind.runtimeSupport(state.league) == DrivebaseRuntimeSupport.CODE_REQUIRED) {
                StatusBanner("CODE REQUIRED · You can inspect and learn from this architecture, but ARES cannot save it as a runnable no-code ${state.league.name} drivebase until a team-written adapter and lifecycle wiring exist.", AresGold)
            }
            if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text("Loading the project drivebase before editing…", color = AresTextSecondary) }
            } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StepRail(state.step, viewModel, Modifier.width(190.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state.step) {
                        DrivebaseBuilderStep.DRIVE_TYPE -> DriveTypeStep(state, viewModel)
                        DrivebaseBuilderStep.HARDWARE -> HardwareStep(state, viewModel)
                        DrivebaseBuilderStep.GEOMETRY -> GeometryStep(state, viewModel)
                        DrivebaseBuilderStep.LOCALIZATION -> LocalizationStep(state, viewModel)
                        DrivebaseBuilderStep.SAFETY -> SafetyStep(state, viewModel)
                        DrivebaseBuilderStep.LABS -> LabsStep(state, viewModel)
                        DrivebaseBuilderStep.REVIEW -> ReviewStep(state, viewModel, onBackToStudio)
                    }
                }
                IssueRail(state, Modifier.width(260.dp).fillMaxHeight())
            }
        }

        // Slide-out Hardware Device Inspector Drawer
        val selected = state.draft.hardware.firstOrNull { it.id == state.selectedHardwareId }
        if (selected != null) {
            AresInspectorDrawer(
                isOpen = true,
                title = selected.displayName,
                categoryBadge = selected.role.name,
                stableId = selected.id,
                icon = Icons.Default.Settings,
                onDismiss = { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(null)) },
                onDone = { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(null)) },
                onDelete = if (state.advanced || state.draft.kind in setOf(DrivebaseKind.DIFFERENTIAL, DrivebaseKind.CUSTOM)) {
                    { viewModel.onIntent(DrivebaseBuilderIntent.RemoveHardware(selected.id)) }
                } else null,
                deleteButtonText = "Delete Hardware",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HardwareEditor(
                        device = selected,
                        hardware = state.draft.hardware,
                        advanced = state.advanced || state.draft.kind in setOf(DrivebaseKind.DIFFERENTIAL, DrivebaseKind.CUSTOM),
                        onUpdate = { viewModel.onIntent(DrivebaseBuilderIntent.UpdateHardware(it)) },
                        onRemove = { viewModel.onIntent(DrivebaseBuilderIntent.RemoveHardware(selected.id)) },
                    )
                }
            }
        }

        // At-a-Glance Drivetrain Spec Summary Modal
        AresSpecSummaryModal(
            isOpen = showSpecSummaryModal,
            title = "${state.draft.displayName} Drivetrain Spec",
            subtitle = "${state.league.name} · .ares/drivetrains/${state.draft.documentId}.aresdrivetrain",
            sections = generateDrivebaseSpecSections(
                state = state,
                onSelectHardware = { id ->
                    showSpecSummaryModal = false
                    viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(id))
                },
            ),
            onDismiss = { showSpecSummaryModal = false },
            rawMarkdownGenerator = { generateDrivebaseMarkdown(state) },
        )

        // Slide-out AI Drivebase Assistant Drawer
        AresInspectorDrawer(
            isOpen = showAiAssistantDrawer,
            title = "AI Drivebase Assistant",
            categoryBadge = "GEMINI",
            icon = Icons.Default.AutoAwesome,
            onDismiss = { showAiAssistantDrawer = false },
            width = 520.dp,
            doneButtonText = "Close",
            onDone = { showAiAssistantDrawer = false },
        ) {
            DrivebaseAiAssistantContent(state, viewModel)
        }

        state.aiProposal?.let { review ->
            AlertDialog(
                onDismissRequest = viewModel::dismissAiProposal,
                title = { Text("Review Gemini's drivebase proposal") },
                text = {
                    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(review.proposal.summary, color = AresTextPrimary, fontWeight = FontWeight.Bold)
                        review.proposal.explanations.forEach { Text("• $it", color = AresTextSecondary, fontSize = 11.sp) }
                        HorizontalDivider(color = AresBorder)
                        review.changes.forEach { change ->
                            Text(change.path, color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("Before: ${change.before}", color = AresTextSecondary, fontSize = 10.sp)
                            Text("After: ${change.after}", color = AresTextPrimary, fontSize = 10.sp)
                        }
                        review.issues.forEach { issue ->
                            Text("${issue.path}: ${issue.message}", color = if (issue.severity == DrivebaseIssueSeverity.ERROR) AresError else AresGold, fontSize = 10.sp)
                        }
                    }
                },
                confirmButton = { Button(viewModel::applyAiProposal, enabled = review.canApply) { Text("Apply to form") } },
                dismissButton = { OutlinedButton(viewModel::dismissAiProposal) { Text("Keep current form") } },
            )
        }
        if (state.pendingDiscardAction != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(DrivebaseBuilderIntent.CancelDiscard) },
                title = { Text("Discard unsaved drivebase changes?") },
                text = { Text("This replaces the current draft. Saved project files are not changed until you review and confirm a save.") },
                confirmButton = { Button({ viewModel.onIntent(DrivebaseBuilderIntent.ConfirmDiscard) }) { Text("Discard draft") } },
                dismissButton = { OutlinedButton({ viewModel.onIntent(DrivebaseBuilderIntent.CancelDiscard) }) { Text("Keep editing") } },
            )
        }
    }
}

@Composable
private fun DrivebaseAiAssistantContent(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    var request by remember(state.draft.documentId) { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = AresSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Describe your robot's requirements in plain language.",
                    color = AresTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    "Gemini will generate a structured proposal matching your league rules (${state.league.name}). It suggests reviewed form edits only; it cannot save or edit Kotlin/Java source directly.",
                    color = AresTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }

        OutlinedTextField(
            value = request,
            onValueChange = { request = it.take(4_000) },
            label = { Text("What should this drivebase do?") },
            placeholder = { Text("e.g. 4-motor Mecanum drive with GoBilda 19.2:1 motors and Pinpoint odometry computer...") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading && !state.aiProposalInProgress,
            minLines = 4,
        )

        Button(
            onClick = { viewModel.requestAiProposal(request) },
            enabled = request.isNotBlank() && !state.loading && !state.aiProposalInProgress,
            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (state.aiProposalInProgress) "Preparing proposal…" else "Ask Gemini for a form proposal")
        }

        state.aiProposalError?.let { Text(it, color = AresError, fontSize = 11.sp) }

        Surface(
            color = AresBackground.copy(alpha = 0.5f),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                "Privacy: Only your prompt and current drivebase configuration are sent. Your source files, telemetry logs, and credentials are never transmitted.",
                color = AresTextTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun StepRail(step: DrivebaseBuilderStep, viewModel: DrivebaseBuilderViewModel, modifier: Modifier) {
    Column(modifier.driveCard(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("BUILD STEPS", color = AresTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        DrivebaseBuilderStep.entries.forEachIndexed { index, candidate ->
            val label = when (candidate) {
                DrivebaseBuilderStep.DRIVE_TYPE -> "Choose drive type"
                DrivebaseBuilderStep.HARDWARE -> "Name hardware"
                DrivebaseBuilderStep.GEOMETRY -> "Measure geometry"
                DrivebaseBuilderStep.LOCALIZATION -> "Choose localization"
                DrivebaseBuilderStep.SAFETY -> "Review safety"
                DrivebaseBuilderStep.LABS -> "Try simulation labs"
                DrivebaseBuilderStep.REVIEW -> "Review & save"
            }
            Text(
                "${index + 1}. $label",
                color = if (step == candidate) AresOnAccent else AresTextPrimary,
                fontWeight = if (step == candidate) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
                    .background(if (step == candidate) AresCyan else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { viewModel.onIntent(DrivebaseBuilderIntent.SelectStep(candidate)) }
                    .padding(9.dp)
                    .semantics { contentDescription = "Step ${index + 1}: $label" }
            )
        }
    }
}

@Composable
private fun DriveTypeStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("1 · Choose how the robot moves", "You can change this later; ARES will rebuild the editable draft and show the resulting checks.")
    val cards = listOf(
        Triple(DrivebaseKind.FTC_MECANUM, "FTC mecanum", "Four angled rollers allow forward, sideways, and turning motion."),
        Triple(DrivebaseKind.FRC_CTRE_SWERVE, "FRC CTRE swerve", "Four independently steering modules; supports read-only TunerConstants import."),
        Triple(DrivebaseKind.DIFFERENTIAL, "Differential", "Left and right wheel groups drive like a tank; no sideways motion."),
        Triple(DrivebaseKind.CUSTOM, "Advanced/custom", "Start with a safe example motor and gyro, then add, remove, and classify team-maintained hardware explicitly.")
    ).filter { (kind, _, _) -> kind in drivebaseKindsForLeague(state.league) }
    cards.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (kind, title, explanation) ->
                Column(
                    Modifier.weight(1f).background(if (state.draft.kind == kind) AresCyan.copy(alpha = .14f) else AresSurfaceElevated, RoundedCornerShape(10.dp))
                        .border(2.dp, if (state.draft.kind == kind) AresCyan else AresBorder, RoundedCornerShape(10.dp))
                        .clickable { viewModel.onIntent(DrivebaseBuilderIntent.SelectKind(kind)) }.padding(14.dp)
                        .semantics { contentDescription = "$title. $explanation" },
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold)
                    Text(kind.runtimeSupportLabel(state.league), color = if (kind.runtimeSupport(state.league) == DrivebaseRuntimeSupport.NO_CODE_RUNNABLE) AresGreen else AresGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(explanation, color = AresTextSecondary, fontSize = 11.sp)
                    Text(if (state.draft.kind == kind) "SELECTED" else "Choose this drive", color = if (state.draft.kind == kind) AresCyan else AresTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (state.draft.kind == DrivebaseKind.FRC_CTRE_SWERVE) CtreImportCard(state, viewModel)
}

@Composable
private fun CtreImportCard(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    Column(Modifier.driveCard(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldHeading("Optional CTRE TunerConstants import", "ARES reads a snapshot of vendor-generated constants for review. It never edits, formats, or overwrites TunerConstants.java.")
        OutlinedTextField(state.importPath, { viewModel.onIntent(DrivebaseBuilderIntent.SetImportPath(it)) }, Modifier.fillMaxWidth(), label = { Text("TunerConstants.java path") }, singleLine = true)
        Button(onClick = { viewModel.onIntent(DrivebaseBuilderIntent.ImportCtre) }, enabled = state.importPath.isNotBlank()) { Text("Import read-only snapshot") }
        Text("Import support fails closed when typed units, module positions, CAN bus, IDs, or inversion cannot be recognized. Review every imported field.", color = AresGold, fontSize = 10.sp)
        state.importWarnings.forEach { Text("• $it", color = AresGold, fontSize = 10.sp) }
    }
}

@Composable
private fun HardwareStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("2 · Identify hardware", "Select any device on the top-down chassis to edit its properties in the slide-out inspector.")
    if (state.league == League.FTC && state.draft.kind == DrivebaseKind.FTC_MECANUM) {
        FtcHubNameGuide(state.draft)
    }
    ChassisDiagram(state, viewModel, Modifier.fillMaxWidth().height(380.dp))
    if (state.advanced || state.draft.kind in setOf(DrivebaseKind.DIFFERENTIAL, DrivebaseKind.CUSTOM)) {
        var addMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton({ addMenu = true }, Modifier.fillMaxWidth()) { Text("+ Add motor, sensor, or follower") }
            DropdownMenu(addMenu, { addMenu = false }) {
                DriveHardwareRole.entries.forEach { role ->
                    DropdownMenuItem({ Text(role.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) }, {
                        addMenu = false
                        viewModel.onIntent(DrivebaseBuilderIntent.AddHardware(role))
                    })
                }
            }
        }
    }
    Text("Direction is always labeled in text: NORMAL or INVERTED. Color is only supplemental.", color = AresTextSecondary, fontSize = 10.sp)
}

@Composable
private fun FtcHubNameGuide(draft: DrivebaseDocument) {
    val cornerHardware = draft.cornerDriveHardware()
    Column(Modifier.driveCard(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldHeading(
            "FTC Robot Controller names",
            "Use these exact names in Configure Robot on the Driver Station. ARES matches names exactly, including lowercase letters.",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cornerHardware.forEachIndexed { index, device ->
                val corner = listOf("Front left", "Front right", "Rear left", "Rear right")[index]
                Column(Modifier.weight(1f)) {
                    Text(corner, color = AresTextSecondary, fontSize = 9.sp)
                    Text(device?.hardwareName ?: "Not configured", color = if (device == null) AresGold else AresCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Recommended defaults: fl, fr, rl, rr. Rear motors use rl and rr—not bl and br.", color = AresTextPrimary, fontSize = 10.sp)
    }
}

@Composable
private fun ChassisDiagram(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel, modifier: Modifier) {
    Column(modifier.driveCard(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FieldHeading("Top-down chassis", "The arrow points toward the robot front. Selectable hardware is listed below for keyboard users.")
        val cornerHardware = state.draft.cornerDriveHardware()
        val cornerIds = cornerHardware.mapNotNull { it?.id }.toSet()
        val displayHardware = cornerHardware.filterNotNull() + state.draft.hardware.filterNot { it.id in cornerIds }
        Canvas(Modifier.fillMaxWidth().height(145.dp)) {
            val left = size.width * .2f; val right = size.width * .8f; val top = size.height * .17f; val bottom = size.height * .83f
            drawRoundRect(AresBorder, Offset(left, top), androidx.compose.ui.geometry.Size(right - left, bottom - top), style = Stroke(3f))
            drawLine(AresCyan, Offset(size.width / 2f, top + 10), Offset(size.width / 2f, top - 30), 5f)
            drawLine(AresCyan, Offset(size.width / 2f, top - 30), Offset(size.width / 2f - 12, top - 12), 5f)
            drawLine(AresCyan, Offset(size.width / 2f, top - 30), Offset(size.width / 2f + 12, top - 12), 5f)
            val positions = listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom))
            positions.forEachIndexed { index, position ->
                val device = cornerHardware[index]
                drawCircle(if (device?.id == state.selectedHardwareId) AresCyan else AresTextSecondary, 16f, position)
            }
        }
        Text("${state.draft.hardware.size} configured devices · scroll to inspect every motor and sensor", color = AresTextSecondary, fontSize = 9.sp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            displayHardware.forEach { device ->
                OutlinedButton(
                    onClick = { viewModel.onIntent(DrivebaseBuilderIntent.SelectHardware(device.id)) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Select ${device.displayName}, ${if (device.inverted) "inverted" else "normal direction"}" }
                ) {
                    Text("${device.displayName} · ${if (device.inverted) "INVERTED" else "NORMAL"}", Modifier.weight(1f), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HardwareEditor(
    device: DriveHardwareDeclaration,
    hardware: List<DriveHardwareDeclaration>,
    advanced: Boolean,
    onUpdate: (DriveHardwareDeclaration) -> Unit,
    onRemove: () -> Unit,
) {
    Text(device.displayName, color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Text(device.role.name.lowercase().replace('_', ' '), color = AresTextSecondary, fontSize = 10.sp)
    HelpedTextField("Display name", device.displayName, "A student-facing label. It does not change the stable device ID.") { onUpdate(device.copy(displayName = it)) }
    HelpedTextField("Hardware-map name", device.hardwareName, "The exact configured name used by FTC or a named FRC device.") { onUpdate(device.copy(hardwareName = it)) }
    if (advanced) {
        var roleMenu by remember(device.id) { mutableStateOf(false) }
        Box {
            OutlinedButton({ roleMenu = true }, Modifier.fillMaxWidth()) { Text("Role · ${device.role.name.lowercase().replace('_', ' ')}") }
            DropdownMenu(roleMenu, { roleMenu = false }) {
                DriveHardwareRole.entries.forEach { role -> DropdownMenuItem({ Text(role.name.lowercase().replace('_', ' ')) }, {
                    roleMenu = false
                    onUpdate(device.copy(role = role, leaderId = if (role in setOf(DriveHardwareRole.LEFT_FOLLOWER, DriveHardwareRole.RIGHT_FOLLOWER)) device.leaderId else null))
                }) }
            }
        }
    }
    if (device.role in setOf(DriveHardwareRole.LEFT_FOLLOWER, DriveHardwareRole.RIGHT_FOLLOWER)) {
        var leaderMenu by remember(device.id) { mutableStateOf(false) }
        val leaders = hardware.filter { it.id != device.id && it.role in setOf(DriveHardwareRole.LEFT_LEADER, DriveHardwareRole.RIGHT_LEADER, DriveHardwareRole.DRIVE_MOTOR) }
        Box {
            OutlinedButton({ leaderMenu = true }, Modifier.fillMaxWidth()) { Text("Leader · ${device.leaderId ?: "Choose a leader"}") }
            DropdownMenu(leaderMenu, { leaderMenu = false }) {
                leaders.forEach { leader -> DropdownMenuItem({ Text(leader.displayName) }, { leaderMenu = false; onUpdate(device.copy(leaderId = leader.id)) }) }
            }
        }
        Text("Follower inversion below is relative to the leader and remains independent from the leader's own mounting inversion.", color = AresTextSecondary, fontSize = 9.sp)
    }
    if (advanced || device.canId != null) {
        HelpedTextField("CAN ID", device.canId?.toString().orEmpty(), "The unique numeric CAN address. Valid ARES range: 0–62.") { onUpdate(device.copy(canId = it.toIntOrNull())) }
        HelpedTextField("CAN bus", device.canBus.orEmpty(), "The named CAN network, for example rio or CANivore name.") { onUpdate(device.copy(canBus = it.ifBlank { null })) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(device.inverted, { onUpdate(device.copy(inverted = it)) })
        Text(if (device.inverted) " INVERTED direction" else " NORMAL direction", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        HelpButton("Mounting inversion changes the sign at the hardware boundary. It is different from follower direction.")
    }
    if (advanced) OutlinedButton(onRemove, Modifier.fillMaxWidth()) { Text("Remove this hardware") }
}

@Composable
private fun GeometryStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("3 · Measure geometry", "Use meters internally. Wheelbase and track width are center-to-center distances; robot dimensions include bumpers/frame perimeter.")
    val geometry = state.draft.geometry
    val labResult = evaluateGeometryLab(
        geometry = geometry,
        linearCommand = 1.0,
        angularCommand = 0.0,
        configuredMaxLinearSpeedMps = state.draft.safety.maxLinearSpeedMetersPerSecond,
        useCornerModuleRadius = state.draft.kind == DrivebaseKind.FRC_CTRE_SWERVE
    )
    Column(Modifier.driveCard(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        GeometryField("Wheel radius", geometry.wheelRadiusMeters, "m", "Measure from the axle center to the floor under normal robot weight.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateGeometry(geometry.copy(wheelRadiusMeters = it))) }
        GeometryField("Track width", geometry.trackWidthMeters, "m", "Center-to-center distance between the left and right wheel contact lines.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateGeometry(geometry.copy(trackWidthMeters = it))) }
        GeometryField("Wheelbase", geometry.wheelBaseMeters, "m", "Center-to-center distance between the front and rear wheel/module contact lines.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateGeometry(geometry.copy(wheelBaseMeters = it))) }
        
        HorizontalDivider(color = AresBorder)
        FieldHeading("Configured Kinematic Limits", "Derived from this drivebase's reviewed safety envelope and measured geometry; no motor or gear ratio is guessed.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.weight(1f), color = AresSurface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, AresBorder)) {
                Column(Modifier.padding(10.dp)) {
                    Text("Linear Limit", color = AresTextSecondary, fontSize = 11.sp)
                    Text(labResult.maxLinearSpeedMps?.let { "%.2f m/s".format(it) } ?: "Not configured", color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("≈ ${"%.1f".format((labResult.maxLinearSpeedMps ?: 0.0) * 3.28084)} ft/s", color = AresTextTertiary, fontSize = 10.sp)
                }
            }
            Surface(Modifier.weight(1f), color = AresSurface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, AresBorder)) {
                Column(Modifier.padding(10.dp)) {
                    Text("Max Angular Rate", color = AresTextSecondary, fontSize = 11.sp)
                    Text("${"%.1f".format(labResult.maxAngularSpeedRadPerSec ?: 0.0)} rad/s", color = AresGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("≈ ${"%.0f".format((labResult.maxAngularSpeedRadPerSec ?: 0.0) * 180.0 / Math.PI)}°/s", color = AresTextTertiary, fontSize = 10.sp)
                }
            }
            Surface(Modifier.weight(1f), color = AresSurface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, AresBorder)) {
                Column(Modifier.padding(10.dp)) {
                    val ratio = if (geometry.trackWidthMeters > 0.01) geometry.wheelBaseMeters / geometry.trackWidthMeters else 1.0
                    Text("Aspect Ratio (L/W)", color = AresTextSecondary, fontSize = 11.sp)
                    Text("%.2f".format(ratio), color = if (ratio in 0.7..1.4) AresTextPrimary else AresGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (ratio in 0.7..1.4) "Balanced turning" else "High scrub risk", color = if (ratio in 0.7..1.4) AresGreen else AresGold, fontSize = 10.sp)
                }
            }
        }
        Text("Overall bumper dimensions are intentionally not collected by drivetrain schema v1; field-collision dimensions belong to the robot geometry contract.", color = AresTextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun LocalizationStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("4 · Choose localization", "Localization estimates where the robot is. Multiple sources can be fused, but each must use CCW-positive headings and valid freshness.")
    LocalizationKind.entries.forEach { kind ->
        val description = when (kind) {
            LocalizationKind.FTC_PINPOINT -> "goBILDA Pinpoint odometry computer; ARES normalizes heading at this boundary."
            LocalizationKind.WHEEL_ODOMETRY_GYRO -> "Wheel encoders plus a gyro; works for differential and custom drives."
            LocalizationKind.CTRE_POSE_ESTIMATOR -> "CTRE swerve module and Pigeon observations."
            LocalizationKind.VISION_FUSION -> "AprilTag/vision corrections fused only when valid and statistically plausible."
            LocalizationKind.CUSTOM -> "Team-maintained estimator with an explicit ARES adapter."
        }
        Row(Modifier.fillMaxWidth().driveCard(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(kind.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase), color = AresTextPrimary, fontWeight = FontWeight.Bold); Text(description, color = AresTextSecondary, fontSize = 10.sp) }
            Checkbox(kind in state.draft.localization, { viewModel.onIntent(DrivebaseBuilderIntent.SetLocalization(kind, it)) }, Modifier.semantics { contentDescription = "Use ${kind.name.lowercase().replace('_', ' ')} localization" })
        }
    }
}

@Composable
private fun SafetyStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("5 · Safety contract", "These requirements fail closed. A drivebase cannot be saved if safe neutral, configuration health, or explicit neutral recovery are disabled.")
    val safety = state.draft.safety
    Column(Modifier.driveCard(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SafetySwitch("Safe neutral required", safety.safeNeutralRequired, "Outputs become neutral at startup, disable, stop, fault, and close.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(safeNeutralRequired = it))) }
        SafetySwitch("Configuration health required", safety.configurationHealthRequired, "Nonzero motion is blocked until every required device reports healthy configuration.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(configurationHealthRequired = it))) }
        SafetySwitch("Explicit neutral recovery", safety.explicitNeutralRecoveryRequired, "After a fault, motion resumes only after a successful neutral write is confirmed.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(explicitNeutralRecoveryRequired = it))) }
        SafetySwitch("Current monitoring required", safety.currentMonitoringRequired, "Unknown current is invalid rather than zero; monitoring must report validity.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(currentMonitoringRequired = it))) }
        GeometryField("Feedback freshness timeout", safety.feedbackFreshnessTimeoutMs.toDouble(), "ms", "Feedback older than this is stale and blocks nonzero closed-loop output.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(feedbackFreshnessTimeoutMs = it.toInt()))) }
        GeometryField("Maximum linear speed", safety.maxLinearSpeedMetersPerSecond, "m/s", "Hard command envelope used by control, simulation, and verification.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(maxLinearSpeedMetersPerSecond = it))) }
        GeometryField("Maximum angular speed", safety.maxAngularSpeedRadiansPerSecond, "rad/s", "Positive rotation is counter-clockwise when viewed from above.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateSafety(safety.copy(maxAngularSpeedRadiansPerSecond = it))) }
    }
}

@Composable
private fun LabsStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel) {
    SectionHeading("6 · Direction & field-relative lab", "This interactive diagram performs local math only. It never publishes NT4, starts a simulator process, or commands robot hardware.")
    val lab = state.lab
    val result = evaluateDriveLab(state.draft.kind, lab, state.draft.geometry, state.draft.hardware)
    val geometryResult = evaluateGeometryLab(
        geometry = state.draft.geometry,
        linearCommand = lab.forward,
        angularCommand = lab.rotate,
        configuredMaxLinearSpeedMps = state.draft.safety.maxLinearSpeedMetersPerSecond,
        useCornerModuleRadius = state.draft.kind == DrivebaseKind.FRC_CTRE_SWERVE
    )
    val localizationResult = evaluateLocalizationFailure(lab.localizationScenario)
    Column(Modifier.driveCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SIMULATION ONLY · NO HARDWARE OUTPUT", color = AresGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        LabSlider("Forward command", lab.forward, "Positive means away from the driver / toward robot front.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateLab(lab.copy(forward = it))) }
        LabSlider("Strafe command", lab.strafe, "Positive means robot-left in the CCW-positive coordinate convention.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateLab(lab.copy(strafe = it))) }
        LabSlider("Turn command", lab.rotate, "Positive means counter-clockwise when viewed from above.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateLab(lab.copy(rotate = it))) }
        LabSlider("Robot heading", lab.headingDegrees / 180.0, "Heading is shown in degrees here; runtime math uses radians.") { viewModel.onIntent(DrivebaseBuilderIntent.UpdateLab(lab.copy(headingDegrees = it * 180.0))) }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(lab.fieldRelative, { viewModel.onIntent(DrivebaseBuilderIntent.UpdateLab(lab.copy(fieldRelative = it))) }); Text(" Field-relative input", color = AresTextPrimary); HelpButton("Field-relative means forward follows the field even when the robot turns. ARES rotates that command into the robot frame.") }
        Text("Robot-frame result: forward ${"%.2f".format(result.robotForward)}, strafe ${"%.2f".format(result.robotStrafe)}", color = AresTextPrimary, fontWeight = FontWeight.Bold)
        result.wheelOutputs.forEach { (wheel, output) ->
            val angle = result.moduleAnglesDegrees[wheel]?.let { " · module angle ${"%.1f".format(it)}°" }.orEmpty()
            Text("${wheel.replace(Regex("([a-z])([A-Z])"), "$1 $2")}: ${if (output >= 0) "FORWARD" else "REVERSE"} ${"%.2f".format(kotlin.math.abs(output))}$angle", color = AresTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Text(result.explanation, color = AresTextSecondary, fontSize = 10.sp)
        DirectionDiagram(lab.headingDegrees, result, Modifier.fillMaxWidth().height(220.dp))
        HorizontalDivider(color = AresBorder)
        FieldHeading("Geometry & turning-radius lab", "The center turning radius is linear command divided by angular command. Track width explains why outside wheels and modules travel farther.")
        Text(geometryResult.explanation, color = AresTextPrimary, fontSize = 10.sp)
        Text("Track-circle diameter: ${geometryResult.trackCircleDiameterMeters?.let { "%.2f m".format(it) } ?: "not applicable while driving straight"}", color = AresTextSecondary, fontSize = 10.sp)
        HorizontalDivider(color = AresBorder)
        FieldHeading("Localization failure lab", "Try each failure. Primary motion feedback and heading fail closed; optional vision may be rejected without disabling healthy odometry.")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LocalizationFailureScenario.entries.chunked(2).forEach { scenarios ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    scenarios.forEach { scenario ->
                        FilterChip(
                            selected = lab.localizationScenario == scenario,
                            onClick = { viewModel.onIntent(DrivebaseBuilderIntent.UpdateLab(lab.copy(localizationScenario = scenario))) },
                            label = { Text(scenario.name.lowercase().replace('_', ' '), fontSize = 9.sp) },
                            modifier = Modifier.semantics { contentDescription = "Simulate localization scenario ${scenario.name.lowercase().replace('_', ' ')}" }
                        )
                    }
                }
            }
        }
        Text("Closed-loop drive: ${if (localizationResult.canDriveClosedLoop) "AVAILABLE" else "BLOCKED"} · vision correction: ${if (localizationResult.usesVisionCorrection) "USED WHEN VALID" else "NOT USED"}", color = if (localizationResult.canDriveClosedLoop) AresGreen else AresError, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Text(localizationResult.message, color = AresTextPrimary, fontSize = 10.sp)
    }
}

@Composable
private fun DirectionDiagram(headingDegrees: Double, result: DriveLabResult, modifier: Modifier) {
    Canvas(modifier.background(AresBackground, RoundedCornerShape(8.dp)).border(1.dp, AresBorder, RoundedCornerShape(8.dp))) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(AresTextSecondary, 48f, center, style = Stroke(3f))
        val angle = headingDegrees * PI / 180.0
        val front = Offset(center.x + (cos(angle) * 70).toFloat(), center.y - (sin(angle) * 70).toFloat())
        drawLine(AresCyan, center, front, 6f)
        val command = Offset(center.x + (result.robotStrafe * 70).toFloat(), center.y - (result.robotForward * 70).toFloat())
        drawLine(AresGold, center, command, 5f)
    }
}

@Composable
private fun ReviewStep(state: DrivebaseBuilderState, viewModel: DrivebaseBuilderViewModel, onBackToStudio: () -> Unit) {
    SectionHeading("7 · Review & save", "Only the canonical .ares/drivetrains document changes. Generated plumbing/source generation is a later explicit project action.")
    val review = state.saveReview
    val noCodeRunnable = state.draft.kind.runtimeSupport(state.league) == DrivebaseRuntimeSupport.NO_CODE_RUNNABLE
    if (review == null) {
        if (state.dirty) {
            Text("Select Review changes to validate the draft and create a content-hash-bound structured diff.", color = AresTextSecondary)
            Button(onClick = { viewModel.onIntent(DrivebaseBuilderIntent.ReviewSave) }, enabled = noCodeRunnable) { Text(if (noCodeRunnable) "Create reviewed diff" else "Code required before save") }
        } else {
            StatusBanner("Saved · The canonical drivebase already matches this form.", AresGreen)
            Button(onClick = onBackToStudio, colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent)) {
                Text("Continue to Robot Studio")
            }
        }
        if (!noCodeRunnable) Text("This builder will not claim an architecture is runnable when the selected ${state.league.name} season shell has no matching adapter.", color = AresGold, fontSize = 10.sp)
    } else {
        Column(Modifier.driveCard(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("STRUCTURED DIFF · base ${review.baseContentHash?.take(12) ?: "new document"}", color = AresCyan, fontWeight = FontWeight.Bold)
            review.changes.forEach { change ->
                Column(Modifier.fillMaxWidth().background(AresBackground.copy(alpha = .55f), RoundedCornerShape(6.dp)).padding(8.dp)) {
                    Text(change.path, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Before: ${change.before}", color = AresTextSecondary, fontSize = 10.sp)
                    Text("After:  ${change.after}", color = AresTextPrimary, fontSize = 10.sp)
                }
            }
            Text("Confirmation ${review.confirmationToken}", color = AresTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Button(onClick = { viewModel.onIntent(DrivebaseBuilderIntent.ConfirmSave(review.confirmationToken)) }, colors = ButtonDefaults.buttonColors(containerColor = AresGreen, contentColor = AresOnAccent)) { Text("Confirm and save canonical drivebase") }
            Text("ARES creates a history backup first. This does not push a robot value or edit CTRE vendor source.", color = AresTextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun IssueRail(state: DrivebaseBuilderState, modifier: Modifier) {
    Column(modifier.driveCard().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("READINESS CHECKS", color = AresTextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        if (state.issues.isEmpty()) Text("Ready for review", color = AresGreen, fontWeight = FontWeight.Bold)
        state.issues.forEach { issue ->
            val color = when (issue.severity) { DrivebaseIssueSeverity.ERROR -> AresError; DrivebaseIssueSeverity.WARNING -> AresGold; DrivebaseIssueSeverity.INFO -> AresCyan }
            Column(Modifier.fillMaxWidth().background(color.copy(alpha = .08f), RoundedCornerShape(6.dp)).padding(8.dp)) {
                Text(issue.severity.name, color = color, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                Text(issue.message, color = AresTextPrimary, fontSize = 10.sp)
                Text(issue.path, color = AresTextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
        }
        HorizontalDivider(color = AresBorder)
        Text("RUNTIME FLOW", color = AresTextSecondary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Text("Input → Redux action/reducer → immutable state → drive controller → IO contract → FTC/FRC or simulated adapter", color = AresTextPrimary, fontSize = 10.sp)
    }
}

@Composable private fun SectionHeading(title: String, description: String) { Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(description, color = AresTextSecondary, fontSize = 11.sp) } }
@Composable private fun FieldHeading(title: String, help: String) { Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp); HelpButton(help) } }

@Composable
private fun HelpButton(help: String) {
    var show by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { show = true }, Modifier.size(28.dp).semantics { contentDescription = "Help: $help" }) { Icon(Icons.AutoMirrored.Filled.HelpOutline, "Show help", tint = AresTextSecondary, modifier = Modifier.size(15.dp)) }
        DropdownMenu(show, { show = false }) { Text(help, color = AresTextPrimary, fontSize = 11.sp, modifier = Modifier.widthIn(max = 320.dp).padding(12.dp)) }
    }
}

@Composable
private fun HelpedTextField(label: String, value: String, help: String, onValue: (String) -> Unit) {
    Column { FieldHeading(label, help); OutlinedTextField(value, onValue, Modifier.fillMaxWidth().semantics { contentDescription = "$label. $help" }, singleLine = true) }
}

@Composable
private fun GeometryField(label: String, value: Double, unit: String, help: String, onValue: (Double) -> Unit) {
    var raw by remember(label, value) { mutableStateOf(value.toString()) }
    Column { FieldHeading("$label ($unit)", help); OutlinedTextField(raw, { text -> raw = text; text.toDoubleOrNull()?.let(onValue) }, Modifier.fillMaxWidth().semantics { contentDescription = "$label in $unit. $help" }, singleLine = true) }
}

@Composable
private fun SafetySwitch(label: String, checked: Boolean, help: String, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text(label, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp); HelpButton(help) }; Switch(checked, onChecked, Modifier.semantics { contentDescription = "$label. $help" }) }
}

@Composable
private fun LabSlider(label: String, value: Double, help: String, onValue: (Double) -> Unit) {
    Column { Row(verticalAlignment = Alignment.CenterVertically) { Text("$label: ${"%.2f".format(value)}", color = AresTextPrimary, fontSize = 11.sp); HelpButton(help) }; Slider(value.toFloat(), { onValue(it.toDouble()) }, valueRange = -1f..1f, modifier = Modifier.semantics { contentDescription = "$label. $help" }) }
}

@Composable private fun StatusBanner(message: String, color: Color) { Text(message, color = color, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(color.copy(alpha = .08f), RoundedCornerShape(6.dp)).padding(8.dp)) }
private fun Modifier.driveCard() = background(AresSurfaceElevated, RoundedCornerShape(10.dp)).border(1.dp, AresBorder, RoundedCornerShape(10.dp)).padding(12.dp)

private fun generateDrivebaseSpecSections(
    state: DrivebaseBuilderState,
    onSelectHardware: (String) -> Unit,
): List<AresSpecSection> {
    val draft = state.draft
    return listOf(
        AresSpecSection(
            title = "Hardware Map & Actuators",
            rows = draft.hardware.map { dev ->
                AresSpecRow(
                    id = dev.id,
                    primaryLabel = dev.displayName,
                    secondaryLabel = "${dev.hardwareName} · ${if (dev.inverted) "INVERTED" else "NORMAL"}",
                    badge = dev.role.name,
                    columns = listOfNotNull(
                        "Role" to dev.role.name.lowercase().replace('_', ' '),
                        "Hardware Name" to dev.hardwareName,
                        dev.canId?.let { "CAN ID" to "$it (${dev.canBus ?: "default"})" },
                        dev.leaderId?.let { "Leader" to it },
                        "Direction" to if (dev.inverted) "Inverted" else "Normal",
                    ),
                    onEditClick = { onSelectHardware(dev.id) },
                )
            },
        ),
        AresSpecSection(
            title = "Chassis Geometry",
            rows = listOf(
                AresSpecRow(
                    id = "geom_trackwidth",
                    primaryLabel = "Trackwidth",
                    secondaryLabel = "${draft.geometry.trackWidthMeters} m",
                    badge = "Dimensions",
                    columns = listOf("Trackwidth" to "${draft.geometry.trackWidthMeters} m (${(draft.geometry.trackWidthMeters * 39.3701).toInt()} in)"),
                ),
                AresSpecRow(
                    id = "geom_wheelbase",
                    primaryLabel = "Wheelbase",
                    secondaryLabel = "${draft.geometry.wheelBaseMeters} m",
                    badge = "Dimensions",
                    columns = listOf("Wheelbase" to "${draft.geometry.wheelBaseMeters} m (${(draft.geometry.wheelBaseMeters * 39.3701).toInt()} in)"),
                ),
                AresSpecRow(
                    id = "geom_wheel_radius",
                    primaryLabel = "Wheel Radius",
                    secondaryLabel = "${draft.geometry.wheelRadiusMeters} m",
                    badge = "Kinematics",
                    columns = listOf(
                        "Wheel Radius" to "${draft.geometry.wheelRadiusMeters} m",
                    ),
                ),
            ),
        ),
        AresSpecSection(
            title = "Localization & Odometry",
            rows = draft.localization.map { kind ->
                AresSpecRow(
                    id = "loc_${kind.name}",
                    primaryLabel = kind.name.replace('_', ' '),
                    secondaryLabel = "Enabled",
                    badge = kind.name,
                    columns = listOf(
                        "Type" to kind.name,
                        "Status" to "Active in build",
                    ),
                )
            },
        ),
        AresSpecSection(
            title = "Kinematic & Safety Limits",
            rows = listOf(
                AresSpecRow(
                    id = "limits_speed",
                    primaryLabel = "Velocity Limits",
                    secondaryLabel = "${draft.safety.maxLinearSpeedMetersPerSecond} m/s",
                    badge = "Limits",
                    columns = listOf(
                        "Max Linear Speed" to "${draft.safety.maxLinearSpeedMetersPerSecond} m/s",
                        "Max Angular Speed" to "${draft.safety.maxAngularSpeedRadiansPerSecond} rad/s",
                    ),
                ),
                AresSpecRow(
                    id = "limits_safety",
                    primaryLabel = "Safety Policies",
                    secondaryLabel = "Freshness: ${draft.safety.feedbackFreshnessTimeoutMs}ms",
                    badge = "Safety",
                    columns = listOf(
                        "Safe Neutral" to if (draft.safety.safeNeutralRequired) "Required" else "Optional",
                        "Config Health" to if (draft.safety.configurationHealthRequired) "Required" else "Optional",
                        "Current Monitoring" to if (draft.safety.currentMonitoringRequired) "Required" else "Optional",
                    ),
                ),
            ),
        ),
    )
}

private fun generateDrivebaseMarkdown(state: DrivebaseBuilderState): String = buildString {
    val draft = state.draft
    appendLine("# Drivebase Specification: ${draft.displayName}")
    appendLine("League: ${state.league.name}")
    appendLine("Kind: ${draft.kind.name}")
    appendLine("Document ID: ${draft.documentId}")
    appendLine()
    appendLine("## Geometry & Kinematics")
    appendLine("- Trackwidth: ${draft.geometry.trackWidthMeters} m")
    appendLine("- Wheelbase: ${draft.geometry.wheelBaseMeters} m")
    appendLine("- Wheel Radius: ${draft.geometry.wheelRadiusMeters} m")
    appendLine("- Max Linear Speed: ${draft.safety.maxLinearSpeedMetersPerSecond} m/s")
    appendLine("- Max Angular Speed: ${draft.safety.maxAngularSpeedRadiansPerSecond} rad/s")
    appendLine()
    appendLine("## Hardware (${draft.hardware.size} devices)")
    appendLine("| Name | Hardware Map / CAN | Role | Inverted |")
    appendLine("|---|---|---|---|")
    draft.hardware.forEach { dev ->
        appendLine("| ${dev.displayName} | ${dev.hardwareName}${dev.canId?.let { " (CAN $it)" }.orEmpty()} | ${dev.role.name} | ${if (dev.inverted) "INVERTED" else "NORMAL"} |")
    }
    appendLine()
    appendLine("## Localization (${draft.localization.size} providers)")
    draft.localization.forEach { kind ->
        appendLine("- ${kind.name}")
    }
}

