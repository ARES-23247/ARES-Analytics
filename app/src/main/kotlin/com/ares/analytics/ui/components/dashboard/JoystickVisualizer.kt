package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
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
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Active visualizer states
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

    // Keyboard driving states
    var keyboardControlEnabled by remember { mutableStateOf(false) }
    var isWPressed by remember { mutableStateOf(false) }
    var isSPressed by remember { mutableStateOf(false) }
    var isAPressed by remember { mutableStateOf(false) }
    var isDPressed by remember { mutableStateOf(false) }
    var isQPressed by remember { mutableStateOf(false) }
    var isEPressed by remember { mutableStateOf(false) }
    var isTransferring by remember { mutableStateOf(false) }

    var isTeleopMode by remember { mutableStateOf(true) }
    var isFieldCentric by remember { mutableStateOf(false) }
    var isRedAlliance by remember { mutableStateOf(false) }
    var isIntaking by remember { mutableStateOf(false) }
    var isFlywheelOn by remember { mutableStateOf(false) }

    // Repeat keys guards
    var isSpacePressed by remember { mutableStateOf(false) }
    var isCPressed by remember { mutableStateOf(false) }
    var isRPressed by remember { mutableStateOf(false) }
    var isShiftPressed by remember { mutableStateOf(false) }
    var isFPressed by remember { mutableStateOf(false) }

    // 1. Receive data from telemetry logs or NetworkTables if Keyboard Drive is disabled
    if (currentFrame != null) {
        lx = currentFrame.values["Driver/LeftStickX"] ?: 0.0
        ly = currentFrame.values["Driver/LeftStickY"] ?: 0.0
        rx = currentFrame.values["Driver/RightStickX"] ?: 0.0
        ry = currentFrame.values["Driver/RightStickY"] ?: 0.0
        lt = currentFrame.values["Driver/LeftTrigger"] ?: 0.0
        rt = currentFrame.values["Driver/RightTrigger"] ?: 0.0
        btnA = (currentFrame.values["Driver/AButton"] ?: 0.0) > 0.5
        btnB = (currentFrame.values["Driver/BButton"] ?: 0.0) > 0.5
        btnX = (currentFrame.values["Driver/XButton"] ?: 0.0) > 0.5
        btnY = (currentFrame.values["Driver/YButton"] ?: 0.0) > 0.5
        dpadUp = (currentFrame.values["Driver/DpadUp"] ?: 0.0) > 0.5
        dpadDown = (currentFrame.values["Driver/DpadDown"] ?: 0.0) > 0.5
        dpadLeft = (currentFrame.values["Driver/DpadLeft"] ?: 0.0) > 0.5
        dpadRight = (currentFrame.values["Driver/DpadRight"] ?: 0.0) > 0.5
    } else if (nt4ClientService != null && !keyboardControlEnabled) {
        LaunchedEffect(Unit) {
            scope.launch {
                nt4ClientService.telemetryFlow.collect { frame ->
                    val key = frame.key
                    val value = frame.value
                    when (key) {
                        "Driver/LeftStickX" -> lx = value
                        "Driver/LeftStickY" -> ly = value
                        "Driver/RightStickX" -> rx = value
                        "Driver/RightStickY" -> ry = value
                        "Driver/LeftTrigger" -> lt = value
                        "Driver/RightTrigger" -> rt = value
                        "Driver/AButton" -> btnA = value > 0.5
                        "Driver/BButton" -> btnB = value > 0.5
                        "Driver/XButton" -> btnX = value > 0.5
                        "Driver/YButton" -> btnY = value > 0.5
                        "Driver/DpadUp" -> dpadUp = value > 0.5
                        "Driver/DpadDown" -> dpadDown = value > 0.5
                        "Driver/DpadLeft" -> dpadLeft = value > 0.5
                        "Driver/DpadRight" -> dpadRight = value > 0.5
                    }
                }
            }
        }
    }

    // Request focus when keyboard drive is activated
    LaunchedEffect(keyboardControlEnabled) {
        if (keyboardControlEnabled) {
            focusRequester.requestFocus()
        } else {
            // Reset keys when disabled
            isWPressed = false
            isSPressed = false
            isAPressed = false
            isDPressed = false
            isQPressed = false
            isEPressed = false
            isTransferring = false
        }
    }

    // 2. Headless Keyboard controller publishing loop (50Hz / 20ms)
    LaunchedEffect(keyboardControlEnabled) {
        if (keyboardControlEnabled && nt4ClientService != null) {
            var heartbeat = 0L
            while (true) {
                val activeVx = if (isWPressed) 4.0 else if (isSPressed) -4.0 else 0.0
                val activeVy = if (isAPressed) 4.0 else if (isDPressed) -4.0 else 0.0
                val activeOmega = if (isQPressed) 4.0 else if (isEPressed) -4.0 else 0.0

                nt4ClientService.publishInputDouble(1001, activeVx)
                nt4ClientService.publishInputDouble(1002, activeVy)
                nt4ClientService.publishInputDouble(1003, activeOmega)
                nt4ClientService.publishInputBoolean(1004, isIntaking)
                nt4ClientService.publishInputBoolean(1005, isFlywheelOn)
                nt4ClientService.publishInputBoolean(1006, isTransferring)
                nt4ClientService.publishInputBoolean(1007, isTeleopMode)
                nt4ClientService.publishInputBoolean(1008, isFieldCentric)
                nt4ClientService.publishInputBoolean(1009, isRedAlliance)
                nt4ClientService.publishInputLong(1010, heartbeat++)

                // Reflect visual changes locally
                lx = if (isAPressed) -1.0 else if (isDPressed) 1.0 else 0.0
                ly = if (isWPressed) -1.0 else if (isSPressed) 1.0 else 0.0
                rx = if (isQPressed) -1.0 else if (isEPressed) 1.0 else 0.0
                lt = if (isIntaking) 1.0 else 0.0
                rt = if (isTransferring) 1.0 else 0.0
                btnA = isTeleopMode
                btnB = isFieldCentric
                btnX = isRedAlliance
                btnY = isFlywheelOn

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
            .focusRequester(focusRequester)
            .focusable()
            .clickable { focusRequester.requestFocus() }
            .onKeyEvent { keyEvent ->
                if (keyboardControlEnabled) {
                    val isDown = keyEvent.type == KeyEventType.KeyDown
                    val isUp = keyEvent.type == KeyEventType.KeyUp
                    when (keyEvent.key) {
                        Key.W -> { isWPressed = isDown; true }
                        Key.S -> { isSPressed = isDown; true }
                        Key.A -> { isAPressed = isDown; true }
                        Key.D -> { isDPressed = isDown; true }
                        Key.Q -> { isQPressed = isDown; true }
                        Key.E -> { isEPressed = isDown; true }
                        Key.Enter -> { isTransferring = isDown; true }
                        Key.Spacebar -> {
                            if (isDown && !isSpacePressed) {
                                isSpacePressed = true
                                isTeleopMode = !isTeleopMode
                            } else if (isUp) {
                                isSpacePressed = false
                            }
                            true
                        }
                        Key.C -> {
                            if (isDown && !isCPressed) {
                                isCPressed = true
                                isFieldCentric = !isFieldCentric
                            } else if (isUp) {
                                isCPressed = false
                            }
                            true
                        }
                        Key.R -> {
                            if (isDown && !isRPressed) {
                                isRPressed = true
                                isRedAlliance = !isRedAlliance
                            } else if (isUp) {
                                isRPressed = false
                            }
                            true
                        }
                        Key.ShiftLeft, Key.ShiftRight -> {
                            if (isDown && !isShiftPressed) {
                                isShiftPressed = true
                                isIntaking = !isIntaking
                            } else if (isUp) {
                                isShiftPressed = false
                            }
                            true
                        }
                        Key.F -> {
                            if (isDown && !isFPressed) {
                                isFPressed = true
                                isFlywheelOn = !isFlywheelOn
                            } else if (isUp) {
                                isFPressed = false
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
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
                    onClick = { keyboardControlEnabled = !keyboardControlEnabled },
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
