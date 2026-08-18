package com.ares.analytics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.GamepadState
import com.ares.analytics.ui.components.controls.ControllerCanvas
import com.ares.analytics.ui.components.core.AresInspectorDrawer
import com.ares.analytics.ui.components.core.AresSpecRow
import com.ares.analytics.ui.components.core.AresSpecSection
import com.ares.analytics.ui.components.core.AresSpecSummaryModal
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresTheme
import com.areslib.controls.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class BuilderVisualScreenshotTest {

    private val outputDir = File("C:/Users/david/.gemini/antigravity/brain/4081e3eb-e3a3-4e9c-9ea3-a9bbd492a7b1")

    @Test
    fun renderControllerCanvasWithActionPills() {
        val scene = ImageComposeScene(1000, 700)
        val profile = ControllerProfileDocument(
            documentId = "standard_gamepad",
            displayName = "Logitech F310 / Xbox Standard",
            controls = listOf(
                ControllerControlDocument(
                    controlId = "btn_a",
                    displayName = "A Button",
                    surface = ControllerSurfaceDocument.FRONT,
                    type = ControllerControlTypeDocument.BUTTON,
                    anchor = ControllerAnchorDocument(0.78, 0.65),
                ),
                ControllerControlDocument(
                    controlId = "btn_b",
                    displayName = "B Button",
                    surface = ControllerSurfaceDocument.FRONT,
                    type = ControllerControlTypeDocument.BUTTON,
                    anchor = ControllerAnchorDocument(0.85, 0.55),
                ),
                ControllerControlDocument(
                    controlId = "btn_x",
                    displayName = "X Button",
                    surface = ControllerSurfaceDocument.FRONT,
                    type = ControllerControlTypeDocument.BUTTON,
                    anchor = ControllerAnchorDocument(0.71, 0.55),
                ),
                ControllerControlDocument(
                    controlId = "btn_y",
                    displayName = "Y Button",
                    surface = ControllerSurfaceDocument.FRONT,
                    type = ControllerControlTypeDocument.BUTTON,
                    anchor = ControllerAnchorDocument(0.78, 0.45),
                ),
                ControllerControlDocument(
                    controlId = "bumper_r",
                    displayName = "Right Bumper",
                    surface = ControllerSurfaceDocument.FRONT,
                    type = ControllerControlTypeDocument.BUTTON,
                    anchor = ControllerAnchorDocument(0.75, 0.22),
                ),
            ),
        )

        val boundLabels = mapOf(
            "btn_a" to listOf("Intake Forward"),
            "btn_b" to listOf("Eject Gamepiece"),
            "bumper_r" to listOf("High Basket Score", "Auto Target Align"),
        )

        scene.setContent {
            AresTheme {
                Box(Modifier.fillMaxSize().background(AresBackground).padding(20.dp)) {
                    ControllerCanvas(
                        profile = profile,
                        surface = ControllerSurfaceDocument.FRONT,
                        selectedControlId = "bumper_r",
                        chordControlIds = emptySet(),
                        boundControlIds = setOf("btn_a", "btn_b", "bumper_r"),
                        targetPlatform = ControllerInputPlatform.FTC,
                        liveState = GamepadState(),
                        onControlSelected = {},
                        boundActionLabels = boundLabels,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
        if (data != null) {
            val file = File(outputDir, "controller_canvas_badges.png")
            file.writeBytes(data.bytes)
            println("Saved screenshot to: ${file.absolutePath}")
        }
    }

    @Test
    fun renderInspectorDrawer() {
        val scene = ImageComposeScene(1100, 750)
        scene.setContent {
            AresTheme {
                Box(Modifier.fillMaxSize().background(AresBackground).padding(20.dp)) {
                    Text("Main Stage Background Area (Drivetrain / Subsystem / Controls Canvas)", color = AresBackground)
                    AresInspectorDrawer(
                        isOpen = true,
                        title = "Flywheel Motor (Left)",
                        categoryBadge = "ACTUATOR",
                        stableId = "left_flywheel",
                        onDismiss = {},
                        onDone = {},
                        onDelete = {},
                        width = 460.dp,
                    ) {
                        Text("Motor Velocity Control & Current Limits Inspector Body Content", color = AresBackground)
                    }
                }
            }
        }

        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
        if (data != null) {
            val file = File(outputDir, "inspector_drawer_preview.png")
            file.writeBytes(data.bytes)
            println("Saved screenshot to: ${file.absolutePath}")
        }
    }

    @Test
    fun renderSpecSummaryModal() {
        val scene = ImageComposeScene(1200, 800)
        val sections = listOf(
            AresSpecSection(
                title = "Hardware Map",
                rows = listOf(
                    AresSpecRow(
                        id = "fl_motor",
                        primaryLabel = "Front Left Motor",
                        secondaryLabel = "fl · Port 0",
                        badge = "MOTOR",
                        columns = listOf(
                            "Hardware Name" to "fl",
                            "Role" to "Front Left Drive",
                            "Direction" to "Normal",
                            "Current Limit" to "30A",
                        ),
                    ),
                    AresSpecRow(
                        id = "fr_motor",
                        primaryLabel = "Front Right Motor",
                        secondaryLabel = "fr · Port 1",
                        badge = "MOTOR",
                        columns = listOf(
                            "Hardware Name" to "fr",
                            "Role" to "Front Right Drive",
                            "Direction" to "Inverted",
                            "Current Limit" to "30A",
                        ),
                    ),
                    AresSpecRow(
                        id = "rl_motor",
                        primaryLabel = "Rear Left Motor",
                        secondaryLabel = "rl · Port 2",
                        badge = "MOTOR",
                        columns = listOf(
                            "Hardware Name" to "rl",
                            "Role" to "Rear Left Drive",
                            "Direction" to "Normal",
                            "Current Limit" to "30A",
                        ),
                    ),
                    AresSpecRow(
                        id = "rr_motor",
                        primaryLabel = "Rear Right Motor",
                        secondaryLabel = "rr · Port 3",
                        badge = "MOTOR",
                        columns = listOf(
                            "Hardware Name" to "rr",
                            "Role" to "Rear Right Drive",
                            "Direction" to "Inverted",
                            "Current Limit" to "30A",
                        ),
                    ),
                ),
            ),
            AresSpecSection(
                title = "Stateflow Fields",
                rows = listOf(
                    AresSpecRow(
                        id = "target_rpm",
                        primaryLabel = "Target Velocity",
                        secondaryLabel = "targetRpm · DOUBLE (RPM)",
                        badge = "TARGET",
                        columns = listOf(
                            "Type" to "DOUBLE",
                            "Unit" to "RPM",
                            "Default" to "0.0",
                            "Range" to "[0.0 .. 6000.0]",
                        ),
                    ),
                    AresSpecRow(
                        id = "measured_rpm",
                        primaryLabel = "Measured Velocity",
                        secondaryLabel = "measuredRpm · DOUBLE (RPM)",
                        badge = "ESTIMATE",
                        columns = listOf(
                            "Type" to "DOUBLE",
                            "Unit" to "RPM",
                            "Sensor Source" to "fl.encoderVelocity",
                        ),
                    ),
                ),
            ),
            AresSpecSection(
                title = "Control Laws",
                rows = listOf(
                    AresSpecRow(
                        id = "velocity_pid",
                        primaryLabel = "Flywheel Velocity PIDF",
                        secondaryLabel = "left_flywheel ← target targetRpm",
                        badge = "VELOCITY_PID",
                        columns = listOf(
                            "PID Gains" to "kP=0.0012, kI=0.0000, kD=0.0001",
                            "Feedforward" to "kS=0.05, kV=0.0018, kA=0.0002",
                            "Output Limits" to "[-1.0 .. 1.0]",
                            "Tolerance" to "50 RPM",
                        ),
                    ),
                ),
            ),
        )

        scene.setContent {
            AresTheme {
                Box(Modifier.fillMaxSize().background(AresBackground)) {
                    AresSpecSummaryModal(
                        isOpen = true,
                        title = "Mecanum Drivetrain Specification",
                        subtitle = "Autonomous & TeleOp Kinematics · .ares/drivetrains/mecanum.aresdrive",
                        sections = sections,
                        onDismiss = {},
                        rawMarkdownGenerator = { "# Drivetrain Spec\n- 4 Motors\n- Pinpoint Odometry" },
                    )
                }
            }
        }

        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
        if (data != null) {
            val file = File(outputDir, "spec_summary_modal_preview.png")
            file.writeBytes(data.bytes)
            println("Saved screenshot to: ${file.absolutePath}")
        }
    }

    @Test
    fun renderAiAssistantDrawer() {
        val scene = ImageComposeScene(1200, 800)
        scene.setContent {
            AresTheme {
                Box(Modifier.fillMaxSize().background(AresBackground).padding(20.dp)) {
                    Text("Drivebase Builder Workspace (Geometry & Odometry Stage)", color = AresBackground)
                    AresInspectorDrawer(
                        isOpen = true,
                        title = "AI Drivebase Assistant",
                        categoryBadge = "GEMINI",
                        icon = androidx.compose.material.icons.Icons.Default.AutoAwesome,
                        onDismiss = {},
                        onDone = {},
                        doneButtonText = "Close",
                        width = 520.dp,
                    ) {
                        androidx.compose.foundation.layout.Column(
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                        ) {
                            androidx.compose.material3.Surface(
                                color = com.ares.analytics.ui.theme.AresSurface,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, com.ares.analytics.ui.theme.AresBorder),
                            ) {
                                androidx.compose.foundation.layout.Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "Describe your robot's requirements in plain language.",
                                        color = com.ares.analytics.ui.theme.AresTextPrimary,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        "Gemini will generate a structured proposal matching your league rules (FTC). It suggests reviewed form edits only; it cannot save or edit Kotlin/Java source directly.",
                                        color = com.ares.analytics.ui.theme.AresTextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                    )
                                }
                            }

                            androidx.compose.material3.OutlinedTextField(
                                value = "4-motor Mecanum drive with GoBilda 19.2:1 motors, 435 RPM, 96mm wheels, and Pinpoint odometry computer at (0.05, -0.02) m",
                                onValueChange = {},
                                label = { Text("What should this drivebase do?") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 4,
                            )

                            androidx.compose.material3.Button(
                                onClick = {},
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = com.ares.analytics.ui.theme.AresCyan,
                                    contentColor = com.ares.analytics.ui.theme.AresOnAccent
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                                Text("Ask Gemini for a form proposal")
                            }

                            androidx.compose.material3.Surface(
                                color = AresBackground.copy(alpha = 0.5f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    "Privacy: Only your prompt and current drivebase configuration are sent. Your source files, telemetry logs, and credentials are never transmitted.",
                                    color = com.ares.analytics.ui.theme.AresTextTertiary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        val image = scene.render()
        val data = image.encodeToData(EncodedImageFormat.PNG)
        if (data != null) {
            val file = File(outputDir, "ai_assistant_drawer_preview.png")
            file.writeBytes(data.bytes)
            println("Saved screenshot to: ${file.absolutePath}")
        }
    }
}
