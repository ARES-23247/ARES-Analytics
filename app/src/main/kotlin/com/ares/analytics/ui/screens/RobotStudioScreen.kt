package com.ares.analytics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.GamepadState
import com.ares.analytics.shared.WorkspaceConfig
import com.ares.analytics.ui.components.controls.ControlsEditorPanel
import com.ares.analytics.ui.components.robotstudio.RobotContextInspector
import com.ares.analytics.ui.components.robotstudio.RobotHierarchyTree
import com.ares.analytics.ui.components.robotstudio.RobotStudioSelection
import com.ares.analytics.ui.components.robotstudio.SubsystemTreeItem
import com.ares.analytics.ui.theme.AresAmber
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.viewmodel.SubsystemGeneratorViewModel
import com.ares.analytics.viewmodel.controls.ControlsEditorState
import com.ares.analytics.viewmodel.controls.ControlsEditorViewModel
import com.ares.analytics.viewmodel.drivebase.DrivebaseBuilderViewModel
import com.ares.analytics.viewmodel.hardware.HardwareSetupViewModel
import com.ares.analytics.viewmodel.project.ProjectIdentityViewModel
import com.ares.analytics.viewmodel.robotstudio.RobotStudioAction
import com.ares.analytics.viewmodel.robotstudio.RobotStudioStageId
import com.ares.analytics.viewmodel.robotstudio.RobotStudioStageStatus
import com.ares.analytics.viewmodel.robotstudio.RobotStudioViewModel
import com.ares.analytics.viewmodel.superstructure.SuperstructureStudioViewModel

/**
 * Unified 3-Pane Robot Studio Workspace:
 * Left Pane: Robot Hierarchy Tree (Identity, Drivetrain, Subsystems, Superstructure, Controls, Port Map)
 * Center Pane: Interactive Visual Canvas (2D Kinematics, Stateflow Node Graph, Posture Matrix, Gamepad Canvas)
 * Right Pane: Context-sensitive live property inspector and validation checks.
 */
@Composable
fun RobotStudioScreen(
    viewModel: RobotStudioViewModel,
    drivebaseViewModel: DrivebaseBuilderViewModel,
    subsystemViewModel: SubsystemGeneratorViewModel,
    superstructureViewModel: SuperstructureStudioViewModel,
    pathPlannerViewModel: com.ares.analytics.viewmodel.PathPlannerViewModel,
    controlsViewModel: ControlsEditorViewModel,
    controlsState: ControlsEditorState,
    gamepad1State: GamepadState,
    gamepad2State: GamepadState,
    hardwareSetupViewModel: HardwareSetupViewModel,
    projectIdentityViewModel: ProjectIdentityViewModel,
    config: WorkspaceConfig,
    initialSelection: RobotStudioSelection = RobotStudioSelection.Identity,
    onAction: (RobotStudioAction) -> Unit,
    onOpenAcademy: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val subsystemState by subsystemViewModel.state.collectAsState()

    var selection by remember { mutableStateOf(initialSelection) }
    var isLeftTreeCollapsed by remember { mutableStateOf(false) }
    var isRightInspectorCollapsed by remember { mutableStateOf(false) }

    // Derive list of subsystem items for tree
    val subsystemTreeItems = remember(subsystemState.documents, subsystemState.draft, subsystemState.dirty) {
        val all = subsystemState.documents.map { sub ->
            SubsystemTreeItem(
                documentId = sub.documentId,
                displayName = sub.displayName,
                isDraft = sub.documentId == subsystemState.draft?.document?.documentId && subsystemState.dirty,
                hasIssues = false,
            )
        }
        if (all.isEmpty() && subsystemState.draft != null) {
            listOf(
                SubsystemTreeItem(
                    documentId = subsystemState.draft!!.document.documentId,
                    displayName = subsystemState.draft!!.document.displayName,
                    isDraft = subsystemState.dirty,
                    hasIssues = false,
                )
            )
        } else all
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(AresBackground),
    ) {
        // Left Pane: Robot Hierarchy Tree
        RobotHierarchyTree(
            state = state,
            subsystems = subsystemTreeItems,
            selected = selection,
            onSelect = { newSel ->
                selection = newSel
                if (newSel is RobotStudioSelection.Subsystem && newSel.documentId.isNotBlank()) {
                    subsystemViewModel.selectDocument(newSel.documentId)
                }
            },
            onAddSubsystem = {
                selection = RobotStudioSelection.Subsystem("")
                subsystemViewModel.setTemplatePickerVisible(true)
            },
            onGenerateAndBuild = {
                subsystemViewModel.generate()
                onAction(RobotStudioAction.RUN_BUILD)
            },
            isCollapsed = isLeftTreeCollapsed,
            onToggleCollapse = { isLeftTreeCollapsed = !isLeftTreeCollapsed },
        )

        // Center Pane: Active Visual Workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            when (val sel = selection) {
                is RobotStudioSelection.Identity -> {
                    ProjectIdentityScreen(
                        viewModel = projectIdentityViewModel,
                        config = config,
                        onBackToStudio = null,
                    )
                }
                is RobotStudioSelection.Drivetrain -> {
                    DrivebaseBuilderScreen(
                        viewModel = drivebaseViewModel,
                        onContinueToSubsystems = {
                            selection = RobotStudioSelection.Subsystem(
                                subsystemTreeItems.firstOrNull()?.documentId ?: ""
                            )
                        },
                        onBackToStudio = null,
                    )
                }
                is RobotStudioSelection.Subsystem -> {
                    SubsystemGeneratorScreen(
                        viewModel = subsystemViewModel,
                        onContinueToPortMap = { selection = RobotStudioSelection.PortMap },
                        onBackToDrivetrain = { selection = RobotStudioSelection.Drivetrain },
                    )
                }
                is RobotStudioSelection.Superstructure -> {
                    SuperstructureStudioScreen(
                        viewModel = superstructureViewModel,
                    )
                }
                is RobotStudioSelection.Autonomous -> {
                    PathPlannerScreen(
                        viewModel = pathPlannerViewModel,
                        league = config.league,
                        projectPath = config.projectPath,
                        robotDimensions = com.ares.analytics.viewmodel.pathing.RobotDimensions(
                            lengthMeters = config.robotLengthMeters
                                ?: com.ares.analytics.viewmodel.pathing.RobotDimensions
                                    .defaultFor(config.league).lengthMeters,
                            widthMeters = config.robotWidthMeters
                                ?: com.ares.analytics.viewmodel.pathing.RobotDimensions
                                    .defaultFor(config.league).widthMeters
                        ),
                    )
                }
                is RobotStudioSelection.Controls -> {
                    ControlsEditorPanel(
                        state = controlsState,
                        viewModel = controlsViewModel,
                        gamepad1State = gamepad1State,
                        gamepad2State = gamepad2State,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is RobotStudioSelection.PortMap -> {
                    HardwareSetupScreen(
                        viewModel = hardwareSetupViewModel,
                        onOpenDrivebase = { selection = RobotStudioSelection.Drivetrain },
                        onOpenSubsystems = {
                            selection = RobotStudioSelection.Subsystem(
                                subsystemTreeItems.firstOrNull()?.documentId ?: ""
                            )
                        },
                        onBackToStudio = null,
                    )
                }
            }
        }

        // Right Pane: Context Inspector
        RobotContextInspector(
            selection = selection,
            state = state,
            isCollapsed = isRightInspectorCollapsed,
            onToggleCollapse = { isRightInspectorCollapsed = !isRightInspectorCollapsed },
        )
    }
}
