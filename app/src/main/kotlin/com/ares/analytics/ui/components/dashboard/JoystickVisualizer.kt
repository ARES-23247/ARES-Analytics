package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.ReplayFrame
import com.ares.analytics.service.Nt4ClientService
import com.ares.analytics.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun JoystickVisualizer(
    currentFrame: ReplayFrame?,
    nt4ClientService: Nt4ClientService? = null,
    services: com.ares.analytics.di.ServiceRegistry? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // Active visualizer states (what is shown on the UI)
    var lx by remember { mutableStateOf(0.0) }
    var ly by remember { mutableStateOf(0.0) }
    var rx by remember { mutableStateOf(0.0) }
    var ry by remember { mutableStateOf(0.0) }
    var lt by remember { mutableStateOf(0.0) }
    var rt by remember { mutableStateOf(0.0) }
    var btnA by remember { mutableStateOf(false) }
    var btnB by remember { mutableStateOf(false) }
    var btnX by remember { mutableStateOf(false) }
    var btnY by remember { mutableStateOf(false) }
    var dpadUp by remember { mutableStateOf(false) }
    var dpadDown by remember { mutableStateOf(false) }
    var dpadLeft by remember { mutableStateOf(false) }
    var dpadRight by remember { mutableStateOf(false) }

    // Keyboard drive state (gets from global services or dummy local state if null)
    val keyboardState = services?.keyboardDriveState ?: remember { com.ares.analytics.di.KeyboardDriveState() }
    val keyboardControlEnabled = keyboardState.enabled

    // 1. Receive data from telemetry logs or NetworkTables if Keyboard Drive is disabled
    if (currentFrame != null) {
        lx = currentFrame.values["Gamepad1/LeftStick_X"] ?: 0.0
        ly = currentFrame.values["Gamepad1/LeftStick_Y"] ?: 0.0
        rx = currentFrame.values["Gamepad1/RightStick_X"] ?: 0.0
        ry = currentFrame.values["Gamepad1/RightStick_Y"] ?: 0.0
        lt = currentFrame.values["Gamepad1/LeftTrigger"] ?: 0.0
        rt = currentFrame.values["Gamepad1/RightTrigger"] ?: 0.0
        btnA = (currentFrame.values["Gamepad1/A"] ?: 0.0) > 0.5
        btnB = (currentFrame.values["Gamepad1/B"] ?: 0.0) > 0.5
        btnX = (currentFrame.values["Gamepad1/X"] ?: 0.0) > 0.5
        btnY = (currentFrame.values["Gamepad1/Y"] ?: 0.0) > 0.5
        dpadUp = (currentFrame.values["Gamepad1/DpadUp"] ?: 0.0) > 0.5
        dpadDown = (currentFrame.values["Gamepad1/DpadDown"] ?: 0.0) > 0.5
        dpadLeft = (currentFrame.values["Gamepad1/DpadLeft"] ?: 0.0) > 0.5
        dpadRight = (currentFrame.values["Gamepad1/DpadRight"] ?: 0.0) > 0.5
    } else if (nt4ClientService != null && !keyboardControlEnabled) {
        LaunchedEffect(Unit) {
            scope.launch {
                nt4ClientService.telemetryFlow.collect { frame ->
                    val key = frame.key
                    val value = frame.value
                    when (key) {
                        "Gamepad1/LeftStick_X" -> lx = value
                        "Gamepad1/LeftStick_Y" -> ly = value
                        "Gamepad1/RightStick_X" -> rx = value
                        "Gamepad1/RightStick_Y" -> ry = value
                        "Gamepad1/LeftTrigger" -> lt = value
                        "Gamepad1/RightTrigger" -> rt = value
                        "Gamepad1/A" -> btnA = value > 0.5
                        "Gamepad1/B" -> btnB = value > 0.5
                        "Gamepad1/X" -> btnX = value > 0.5
                        "Gamepad1/Y" -> btnY = value > 0.5
                        "Gamepad1/DpadUp" -> dpadUp = value > 0.5
                        "Gamepad1/DpadDown" -> dpadDown = value > 0.5
                        "Gamepad1/DpadLeft" -> dpadLeft = value > 0.5
                        "Gamepad1/DpadRight" -> dpadRight = value > 0.5
                    }
                }
            }
        }
    }

    // 2. Headless Keyboard controller publishing loop (50Hz / 20ms)
    LaunchedEffect(keyboardControlEnabled) {
        if (keyboardControlEnabled && nt4ClientService != null) {
            var heartbeat = 0L
            while (true) {
                val activeVx = if (keyboardState.isWPressed) 4.0 else if (keyboardState.isSPressed) -4.0 else 0.0
                val activeVy = if (keyboardState.isAPressed) 4.0 else if (keyboardState.isDPressed) -4.0 else 0.0
                val activeOmega = if (keyboardState.isQPressed) 4.0 else if (keyboardState.isEPressed) -4.0 else 0.0

                nt4ClientService.publishInputDouble(1001, activeVx)
                nt4ClientService.publishInputDouble(1002, activeVy)
                nt4ClientService.publishInputDouble(1003, activeOmega)
                nt4ClientService.publishInputBoolean(1004, keyboardState.isIntaking)
                nt4ClientService.publishInputBoolean(1005, keyboardState.isFlywheelOn)
                nt4ClientService.publishInputBoolean(1006, keyboardState.isTransferring)
                nt4ClientService.publishInputBoolean(1007, keyboardState.isTeleopMode)
                nt4ClientService.publishInputBoolean(1008, keyboardState.isFieldCentric)
                nt4ClientService.publishInputBoolean(1009, keyboardState.isRedAlliance)
                nt4ClientService.publishInputLong(1010, heartbeat++)

                // Reflect visual changes locally
                lx = if (keyboardState.isAPressed) -1.0 else if (keyboardState.isDPressed) 1.0 else 0.0
                ly = if (keyboardState.isWPressed) -1.0 else if (keyboardState.isSPressed) 1.0 else 0.0
                rx = if (keyboardState.isQPressed) -1.0 else if (keyboardState.isEPressed) 1.0 else 0.0
                lt = if (keyboardState.isIntaking) 1.0 else 0.0
                rt = if (keyboardState.isTransferring) 1.0 else 0.0
                btnA = keyboardState.isTeleopMode
                btnB = keyboardState.isFieldCentric
                btnX = keyboardState.isRedAlliance
                btnY = keyboardState.isFlywheelOn

                kotlinx.coroutines.delay(20)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AresSurface)
            .border(
                width = if (keyboardControlEnabled) 2.dp else 1.dp,
                color = if (keyboardControlEnabled) AresGreen else AresBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Joystick/Gamepad Input Monitor",
                color = AresTextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            if (nt4ClientService != null) {
                Button(
                    onClick = { keyboardState.enabled = !keyboardState.enabled },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (keyboardControlEnabled) AresGreen else AresCyan
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (keyboardControlEnabled) "⌨️ Keyboard Active" else "🔌 Enable Keyboard Drive",
                        color = AresBackground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (keyboardControlEnabled) {
            Text(
                "WASD = Drive | QE = Steer | Shift = Intake | F = Flywheel | Enter = Shoot | Space = Mode | C = FieldCentric",
                color = AresGreen,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AresBackground, RoundedCornerShape(8.dp))
                .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // 1. Draw Controller Main Body Outline
                val bodyW = 340f
                val bodyH = 180f
                drawRoundRect(
                    color = AresSurfaceElevated,
                    topLeft = Offset(cx - bodyW / 2f, cy - bodyH / 2f),
                    size = Size(bodyW, bodyH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f, 40f)
                )
                drawRoundRect(
                    color = if (keyboardControlEnabled) AresGreen else AresBorder,
                    topLeft = Offset(cx - bodyW / 2f, cy - bodyH / 2f),
                    size = Size(bodyW, bodyH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f, 40f),
                    style = Stroke(width = 2.5f)
                )

                // 2. Draw Analog Sticks (Left Stick at x-85, y+15; Right Stick at x+45, y+15)
                val stickRadius = 32f
                val leftStickCenter = Offset(cx - 85f, cy + 15f)
                val rightStickCenter = Offset(cx + 45f, cy + 15f)

                // Left Stick Base
                drawCircle(color = AresSurface, radius = stickRadius, center = leftStickCenter)
                drawCircle(color = AresBorder, radius = stickRadius, center = leftStickCenter, style = Stroke(width = 1.5f))

                // Left Stick Deflection Node (WASD keys map here)
                val lNodeX = leftStickCenter.x + (lx * stickRadius * 0.7).toFloat()
                val lNodeY = leftStickCenter.y + (ly * stickRadius * 0.7).toFloat()
                drawCircle(color = if (keyboardControlEnabled) AresGreen else AresCyan, radius = 12f, center = Offset(lNodeX, lNodeY))

                // Right Stick Base
                drawCircle(color = AresSurface, radius = stickRadius, center = rightStickCenter)
                drawCircle(color = AresBorder, radius = stickRadius, center = rightStickCenter, style = Stroke(width = 1.5f))

                // Right Stick Deflection Node (QE keys map here)
                val rNodeX = rightStickCenter.x + (rx * stickRadius * 0.7).toFloat()
                val rNodeY = rightStickCenter.y + (ry * stickRadius * 0.7).toFloat()
                drawCircle(color = if (keyboardControlEnabled) AresGreen else AresCyan, radius = 12f, center = Offset(rNodeX, rNodeY))

                // 3. Draw D-pad (Left side: center x-135, y-25)
                val dpadCenter = Offset(cx - 135f, cy - 25f)
                val dpadSize = 16f

                // Draw D-pad background cross
                drawRect(color = AresSurface, topLeft = Offset(dpadCenter.x - dpadSize * 2.5f, dpadCenter.y - dpadSize / 2f), size = Size(dpadSize * 5f, dpadSize))
                drawRect(color = AresSurface, topLeft = Offset(dpadCenter.x - dpadSize / 2f, dpadCenter.y - dpadSize * 2.5f), size = Size(dpadSize, dpadSize * 5f))

                // Draw directions with highlight colors
                drawRect(color = if (dpadLeft) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x - dpadSize * 2.5f, dpadCenter.y - dpadSize / 2f), size = Size(dpadSize, dpadSize))
                drawRect(color = if (dpadRight) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x + dpadSize * 1.5f, dpadCenter.y - dpadSize / 2f), size = Size(dpadSize, dpadSize))
                drawRect(color = if (dpadUp) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x - dpadSize / 2f, dpadCenter.y - dpadSize * 2.5f), size = Size(dpadSize, dpadSize))
                drawRect(color = if (dpadDown) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x - dpadSize / 2f, dpadCenter.y + dpadSize * 1.5f), size = Size(dpadSize, dpadSize))

                // 4. Draw Buttons A/B/X/Y (Right side: center x+105, y-25)
                val buttonsCenter = Offset(cx + 105f, cy - 25f)
                val btnRadius = 12f
                val btnOffset = 24f

                // Y Button (Flywheel state indicator)
                drawCircle(color = if (btnY) AresGold else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y - btnOffset))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y - btnOffset), style = Stroke(width = 1.5f))

                // A Button (Drive mode indicator - Teleop vs Auto)
                drawCircle(color = if (btnA) AresGold else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y + btnOffset))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y + btnOffset), style = Stroke(width = 1.5f))

                // X Button (Alliance state indicator - Red vs Blue)
                drawCircle(color = if (btnX) AresRed else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x - btnOffset, buttonsCenter.y))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x - btnOffset, buttonsCenter.y), style = Stroke(width = 1.5f))

                // B Button (Field centric state indicator)
                drawCircle(color = if (btnB) AresCyan else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x + btnOffset, buttonsCenter.y))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x + btnOffset, buttonsCenter.y), style = Stroke(width = 1.5f))

                // 5. Draw Triggers and Bumpers (Top: left x-120, right x+60)
                val triggerW = 60f
                val triggerH = 14f

                // Left Trigger Bar (Intake state indicator)
                drawRect(color = AresSurface, topLeft = Offset(cx - 120f, cy - bodyH / 2f - 20f), size = Size(triggerW, triggerH))
                drawRect(color = if (keyboardControlEnabled) AresGreen else AresCyan, topLeft = Offset(cx - 120f, cy - bodyH / 2f - 20f), size = Size(triggerW * lt.toFloat(), triggerH))
                drawRect(color = AresBorder, topLeft = Offset(cx - 120f, cy - bodyH / 2f - 20f), size = Size(triggerW, triggerH), style = Stroke(width = 1.5f))

                // Right Trigger Bar (Transfer/Shoot indicator)
                drawRect(color = AresSurface, topLeft = Offset(cx + 60f, cy - bodyH / 2f - 20f), size = Size(triggerW, triggerH))
                drawRect(color = if (keyboardControlEnabled) AresGreen else AresCyan, topLeft = Offset(cx + 60f, cy - bodyH / 2f - 20f), size = Size(triggerW * rt.toFloat(), triggerH))
                drawRect(color = AresBorder, topLeft = Offset(cx + 60f, cy - bodyH / 2f - 20f), size = Size(triggerW, triggerH), style = Stroke(width = 1.5f))
            }
        }

        // Details Panel
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "L Stick: (${"%.2f".format(lx)}, ${"%.2f".format(ly)})", color = AresTextSecondary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(text = "R Stick: (${"%.2f".format(rx)}, ${"%.2f".format(ry)})", color = AresTextSecondary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(text = "L Trigger: ${"%.2f".format(lt)} | R Trigger: ${"%.2f".format(rt)}", color = AresTextSecondary, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}
