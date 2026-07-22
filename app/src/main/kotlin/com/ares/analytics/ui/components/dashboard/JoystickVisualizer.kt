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
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun JoystickVisualizer(
    currentFrame: ReplayFrame?,
    nt4ClientService: Nt4ClientService? = null,
    services: com.ares.analytics.di.ServiceRegistry? = null,
    onOpenKeybindings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val keyboardState = services?.keyboardDriveState ?: remember { com.ares.analytics.di.KeyboardDriveState() }
    val keyboardControlEnabled = keyboardState.enabled
    val gamepad1StateFlow = services?.gamepadService?.gamepad1State
    val gamepad2StateFlow = services?.gamepadService?.gamepad2State

    // Keyboard/Gamepad publishing loop
    LaunchedEffect(keyboardControlEnabled) {
        if (keyboardControlEnabled && nt4ClientService != null) {
            var heartbeat = 0L
            var lastVx: Double? = null
            var lastVy: Double? = null
            var lastOmega: Double? = null
            var lastQ: Boolean? = null
            var lastE: Boolean? = null
            var lastShift: Boolean? = null
            var lastA: Boolean? = null
            var lastB: Boolean? = null
            var lastX: Boolean? = null
            var lastY: Boolean? = null
            
            // Publish static configurations once
            nt4ClientService.publishInputBoolean(1007, true) // isTeleopMode
            nt4ClientService.publishInputBoolean(1008, false) // isFieldCentric
            nt4ClientService.publishInputBoolean(1009, true) // isRedAlliance

            while (true) {
                val g1 = gamepad1StateFlow?.value

                val (vx, vy, omega) = if (keyboardState.useGamepad && g1 != null && g1.connected) {
                    val activeVx = g1.leftStickY.toDouble() * 4.0
                    val activeVy = g1.leftStickX.toDouble() * -4.0
                    val activeOmega = g1.rightStickX.toDouble() * -4.0
                    Triple(activeVx, activeVy, activeOmega)
                } else {
                    val activeVx = when {
                        keyboardState.isWPressed -> 4.0
                        keyboardState.isSPressed -> -4.0
                        else -> 0.0
                    }
                    val activeVy = when {
                        keyboardState.isAPressed -> 4.0
                        keyboardState.isDPressed -> -4.0
                        else -> 0.0
                    }
                    val activeOmega = when {
                        keyboardState.isLeftPressed -> 4.0
                        keyboardState.isRightPressed -> -4.0
                        else -> 0.0
                    }
                    Triple(activeVx, activeVy, activeOmega)
                }
                val qPressed = if (keyboardState.useGamepad && g1 != null && g1.connected) g1.leftBumper else keyboardState.isQPressed
                val ePressed = if (keyboardState.useGamepad && g1 != null && g1.connected) g1.rightBumper else keyboardState.isEPressed
                val shiftPressed = if (keyboardState.useGamepad && g1 != null && g1.connected) g1.rightTrigger > 0.5f else keyboardState.isShiftPressed
                val aPressed = if (keyboardState.useGamepad && g1 != null && g1.connected) g1.a else keyboardState.isJPressed
                val bPressed = if (keyboardState.useGamepad && g1 != null && g1.connected) g1.b else keyboardState.isLPressed
                val xPressed = if (keyboardState.useGamepad && g1 != null && g1.connected) g1.x else keyboardState.isUPressed
                val yPressed = if (keyboardState.useGamepad && g1 != null && g1.connected) g1.y else keyboardState.isIPressed

                if (vx != lastVx) { nt4ClientService.publishInputDouble(1001, vx); lastVx = vx }
                if (vy != lastVy) { nt4ClientService.publishInputDouble(1002, vy); lastVy = vy }
                if (omega != lastOmega) { nt4ClientService.publishInputDouble(1003, omega); lastOmega = omega }
                
                if (qPressed != lastQ) { nt4ClientService.publishInputBoolean(1004, qPressed); lastQ = qPressed }
                if (ePressed != lastE) { nt4ClientService.publishInputBoolean(1005, ePressed); lastE = ePressed }
                if (shiftPressed != lastShift) { nt4ClientService.publishInputBoolean(1006, shiftPressed); lastShift = shiftPressed }
                
                if (aPressed != lastA) { nt4ClientService.publishInputBoolean(1016, aPressed); lastA = aPressed }
                if (bPressed != lastB) { nt4ClientService.publishInputBoolean(1017, bPressed); lastB = bPressed }
                if (xPressed != lastX) { nt4ClientService.publishInputBoolean(1018, xPressed); lastX = xPressed }
                if (yPressed != lastY) { nt4ClientService.publishInputBoolean(1019, yPressed); lastY = yPressed }

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
                "Gamepad Monitor",
                color = AresTextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenKeybindings,
                    colors = ButtonDefaults.buttonColors(containerColor = AresSurfaceElevated),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("🎮 View Mappings", color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { keyboardState.useGamepad = !keyboardState.useGamepad },
                    colors = ButtonDefaults.buttonColors(containerColor = if (keyboardState.useGamepad) AresCyan else AresSurfaceElevated),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (keyboardState.useGamepad) "Mode: Controller" else "Mode: Keyboard", color = AresTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (nt4ClientService != null) {
                    Button(
                        onClick = { 
                            when {
                                !keyboardControlEnabled -> {
                                    keyboardState.enabled = true
                                    keyboardState.useGamepad = true
                                }
                                keyboardState.useGamepad -> {
                                    keyboardState.useGamepad = false
                                }
                                else -> {
                                    keyboardState.enabled = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (keyboardControlEnabled) AresGreen else AresCyan
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            when {
                                !keyboardControlEnabled -> "🔌 Live Telemetry"
                                keyboardState.useGamepad -> "🎮 Local Gamepad"
                                else -> "⌨️ Local Keyboard"
                            },
                            color = AresBackground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }



        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SingleGamepadVisualizer(
                title = "Gamepad 1 (Driver)",
                gamepadId = "Gamepad1",
                currentFrame = currentFrame,
                nt4ClientService = nt4ClientService,
                keyboardControlEnabled = keyboardControlEnabled,
                keyboardState = keyboardState,
                gamepadStateFlow = gamepad1StateFlow,
                modifier = Modifier.weight(1f)
            )
            SingleGamepadVisualizer(
                title = "Gamepad 2 (Operator)",
                gamepadId = "Gamepad2",
                currentFrame = currentFrame,
                nt4ClientService = nt4ClientService,
                keyboardControlEnabled = false,
                keyboardState = null,
                gamepadStateFlow = gamepad2StateFlow,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
fun SingleGamepadVisualizer(
    title: String,
    gamepadId: String,
    currentFrame: ReplayFrame?,
    nt4ClientService: Nt4ClientService?,
    keyboardControlEnabled: Boolean,
    keyboardState: com.ares.analytics.di.KeyboardDriveState?,
    gamepadStateFlow: kotlinx.coroutines.flow.StateFlow<com.ares.analytics.service.GamepadState>?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
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
    var lb by remember { mutableStateOf(false) }
    var rb by remember { mutableStateOf(false) }
    var btnStart by remember { mutableStateOf(false) }
    var btnBack by remember { mutableStateOf(false) }
    var btnLS by remember { mutableStateOf(false) }
    var btnRS by remember { mutableStateOf(false) }

    when {
        currentFrame != null -> {
            lx = currentFrame.values["$gamepadId/LeftStick_X"] ?: 0.0
            ly = currentFrame.values["$gamepadId/LeftStick_Y"] ?: 0.0
            rx = currentFrame.values["$gamepadId/RightStick_X"] ?: 0.0
            ry = currentFrame.values["$gamepadId/RightStick_Y"] ?: 0.0
            lt = currentFrame.values["$gamepadId/LeftTrigger"] ?: 0.0
            rt = currentFrame.values["$gamepadId/RightTrigger"] ?: 0.0
            btnA = (currentFrame.values["$gamepadId/A"] ?: 0.0) > 0.5
            btnB = (currentFrame.values["$gamepadId/B"] ?: 0.0) > 0.5
            btnX = (currentFrame.values["$gamepadId/X"] ?: 0.0) > 0.5
            btnY = (currentFrame.values["$gamepadId/Y"] ?: 0.0) > 0.5
            dpadUp = (currentFrame.values["$gamepadId/DpadUp"] ?: 0.0) > 0.5
            dpadDown = (currentFrame.values["$gamepadId/DpadDown"] ?: 0.0) > 0.5
            dpadLeft = (currentFrame.values["$gamepadId/DpadLeft"] ?: 0.0) > 0.5
            dpadRight = (currentFrame.values["$gamepadId/DpadRight"] ?: 0.0) > 0.5
            lb = (currentFrame.values["$gamepadId/LeftBumper"] ?: 0.0) > 0.5
            rb = (currentFrame.values["$gamepadId/RightBumper"] ?: 0.0) > 0.5
            btnStart = (currentFrame.values["$gamepadId/Start"] ?: currentFrame.values["$gamepadId/Options"] ?: 0.0) > 0.5
            btnBack = (currentFrame.values["$gamepadId/Back"] ?: currentFrame.values["$gamepadId/Share"] ?: 0.0) > 0.5
            btnLS = (currentFrame.values["$gamepadId/LeftStickButton"] ?: 0.0) > 0.5
            btnRS = (currentFrame.values["$gamepadId/RightStickButton"] ?: 0.0) > 0.5
        }
        nt4ClientService != null && !keyboardControlEnabled -> {
            LaunchedEffect(Unit) {
                scope.launch {
                    nt4ClientService.telemetryFlow.collect { frame ->
                        val key = frame.key
                        val value = frame.value as? Double ?: return@collect
                        when (key) {
                            "$gamepadId/LeftStick_X" -> lx = value
                            "$gamepadId/LeftStick_Y" -> ly = value
                            "$gamepadId/RightStick_X" -> rx = value
                            "$gamepadId/RightStick_Y" -> ry = value
                            "$gamepadId/LeftTrigger" -> lt = value
                            "$gamepadId/RightTrigger" -> rt = value
                            "$gamepadId/A" -> btnA = value > 0.5
                            "$gamepadId/B" -> btnB = value > 0.5
                            "$gamepadId/X" -> btnX = value > 0.5
                            "$gamepadId/Y" -> btnY = value > 0.5
                            "$gamepadId/DpadUp" -> dpadUp = value > 0.5
                            "$gamepadId/DpadDown" -> dpadDown = value > 0.5
                            "$gamepadId/DpadLeft" -> dpadLeft = value > 0.5
                            "$gamepadId/DpadRight" -> dpadRight = value > 0.5
                            "$gamepadId/LeftBumper" -> lb = value > 0.5
                            "$gamepadId/RightBumper" -> rb = value > 0.5
                            "$gamepadId/Start", "$gamepadId/Options" -> btnStart = value > 0.5
                            "$gamepadId/Back", "$gamepadId/Share" -> btnBack = value > 0.5
                            "$gamepadId/LeftStickButton" -> btnLS = value > 0.5
                            "$gamepadId/RightStickButton" -> btnRS = value > 0.5
                        }
                    }
                }
            }
        }
    }

    if (keyboardControlEnabled && (keyboardState != null || gamepadStateFlow != null)) {
        LaunchedEffect(Unit) {
            while (true) {
                val g = gamepadStateFlow?.value
                when {
                    keyboardState?.useGamepad != false && g != null && g.connected -> {
                        lx = g.leftStickX.toDouble()
                        ly = -g.leftStickY.toDouble()
                        rx = g.rightStickX.toDouble()
                        ry = -g.rightStickY.toDouble()
                        lb = g.leftBumper
                        rb = g.rightBumper
                        lt = g.leftTrigger.toDouble()
                        rt = g.rightTrigger.toDouble()
                        btnA = g.a
                        btnB = g.b
                        btnX = g.x
                        btnY = g.y
                        dpadUp = g.dpadUp
                        dpadDown = g.dpadDown
                        dpadLeft = g.dpadLeft
                        dpadRight = g.dpadRight
                    }
                    keyboardState != null -> {
                        lx = when {
                            keyboardState.isAPressed -> -1.0
                            keyboardState.isDPressed -> 1.0
                            else -> 0.0
                        }
                        ly = when {
                            keyboardState.isWPressed -> -1.0
                            keyboardState.isSPressed -> 1.0
                            else -> 0.0
                        }
                        rx = when {
                            keyboardState.isLeftPressed -> -1.0
                            keyboardState.isRightPressed -> 1.0
                            else -> 0.0
                        }
                        ry = when {
                            keyboardState.isUpPressed -> -1.0
                            keyboardState.isDownPressed -> 1.0
                            else -> 0.0
                        }
                        lb = keyboardState.isQPressed
                        rb = keyboardState.isEPressed
                        lt = if (keyboardState.isSpacePressed) 1.0 else 0.0
                        rt = if (keyboardState.isShiftPressed) 1.0 else 0.0
                        btnA = keyboardState.isJPressed
                        btnB = keyboardState.isLPressed
                        btnX = keyboardState.isUPressed
                        btnY = keyboardState.isIPressed
                    }
                }
                kotlinx.coroutines.delay(20)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(AresBackground, RoundedCornerShape(8.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = AresTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Scale the controller to fit half the width now
                val scale = minOf(size.width / 380f, size.height / 200f).coerceAtMost(1f)

                // Draw PS4 Body using Path
                val bodyPath = androidx.compose.ui.graphics.Path().apply {
                    // Center body
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(
                        left = cx - 110f * scale,
                        top = cy - 50f * scale,
                        right = cx + 110f * scale,
                        bottom = cy + 40f * scale,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f * scale)
                    ))
                }
                val leftGrip = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(
                        left = cx - 140f * scale,
                        top = cy - 30f * scale,
                        right = cx - 80f * scale,
                        bottom = cy + 90f * scale,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f * scale)
                    ))
                }
                val rightGrip = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(
                        left = cx + 80f * scale,
                        top = cy - 30f * scale,
                        right = cx + 140f * scale,
                        bottom = cy + 90f * scale,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f * scale)
                    ))
                }
                val combinedGrips = androidx.compose.ui.graphics.Path().apply {
                    op(bodyPath, leftGrip, androidx.compose.ui.graphics.PathOperation.Union)
                }
                val finalBodyPath = androidx.compose.ui.graphics.Path().apply {
                    op(combinedGrips, rightGrip, androidx.compose.ui.graphics.PathOperation.Union)
                }

                drawPath(
                    path = finalBodyPath,
                    color = AresSurfaceElevated
                )
                drawPath(
                    path = finalBodyPath,
                    color = if (keyboardControlEnabled) AresGreen else AresBorder,
                    style = Stroke(width = 2.5f * scale)
                )

                // D-Pad (Top Left)
                val dpadCenter = Offset(cx - 85f * scale, cy - 10f * scale)
                val dpadSize = 12f * scale
                drawRect(color = AresSurface, topLeft = Offset(dpadCenter.x - dpadSize * 2.5f, dpadCenter.y - dpadSize / 2f), size = Size(dpadSize * 5f, dpadSize))
                drawRect(color = AresSurface, topLeft = Offset(dpadCenter.x - dpadSize / 2f, dpadCenter.y - dpadSize * 2.5f), size = Size(dpadSize, dpadSize * 5f))
                drawRect(color = if (dpadLeft) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x - dpadSize * 2.5f, dpadCenter.y - dpadSize / 2f), size = Size(dpadSize, dpadSize))
                drawRect(color = if (dpadRight) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x + dpadSize * 1.5f, dpadCenter.y - dpadSize / 2f), size = Size(dpadSize, dpadSize))
                drawRect(color = if (dpadUp) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x - dpadSize / 2f, dpadCenter.y - dpadSize * 2.5f), size = Size(dpadSize, dpadSize))
                drawRect(color = if (dpadDown) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x - dpadSize / 2f, dpadCenter.y + dpadSize * 1.5f), size = Size(dpadSize, dpadSize))

                // Face Buttons (Top Right)
                val buttonsCenter = Offset(cx + 85f * scale, cy - 10f * scale)
                val btnRadius = 9f * scale
                val btnOffset = 18f * scale
                drawCircle(color = if (btnY) AresGold else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y - btnOffset))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y - btnOffset), style = Stroke(width = 1.5f * scale))
                drawCircle(color = if (btnA) AresGold else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y + btnOffset))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y + btnOffset), style = Stroke(width = 1.5f * scale))
                drawCircle(color = if (btnX) AresRed else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x - btnOffset, buttonsCenter.y))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x - btnOffset, buttonsCenter.y), style = Stroke(width = 1.5f * scale))
                drawCircle(color = if (btnB) AresCyan else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x + btnOffset, buttonsCenter.y))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x + btnOffset, buttonsCenter.y), style = Stroke(width = 1.5f * scale))

                // Left Stick (Bottom Left Grip)
                val stickRadius = 24f * scale
                val leftStickCenter = Offset(cx - 50f * scale, cy + 40f * scale)
                drawCircle(color = AresSurface, radius = stickRadius, center = leftStickCenter)
                if (btnLS) drawCircle(color = AresCyan.copy(alpha = 0.3f), radius = stickRadius, center = leftStickCenter)
                drawCircle(color = AresBorder, radius = stickRadius, center = leftStickCenter, style = Stroke(width = 1.5f * scale))
                val lNodeX = leftStickCenter.x + (lx * stickRadius * 0.7).toFloat()
                val lNodeY = leftStickCenter.y + (ly * stickRadius * 0.7).toFloat()
                drawCircle(color = if (keyboardControlEnabled) AresGreen else AresCyan, radius = 10f * scale, center = Offset(lNodeX, lNodeY))

                // Right Stick (Bottom Right Grip)
                val rightStickCenter = Offset(cx + 50f * scale, cy + 40f * scale)
                drawCircle(color = AresSurface, radius = stickRadius, center = rightStickCenter)
                if (btnRS) drawCircle(color = AresCyan.copy(alpha = 0.3f), radius = stickRadius, center = rightStickCenter)
                drawCircle(color = AresBorder, radius = stickRadius, center = rightStickCenter, style = Stroke(width = 1.5f * scale))
                val rNodeX = rightStickCenter.x + (rx * stickRadius * 0.7).toFloat()
                val rNodeY = rightStickCenter.y + (ry * stickRadius * 0.7).toFloat()
                drawCircle(color = if (keyboardControlEnabled) AresGreen else AresCyan, radius = 10f * scale, center = Offset(rNodeX, rNodeY))

                // Triggers (Top Edge L2/R2)
                val triggerW = 50f * scale
                val triggerH = 14f * scale
                val triggerTop = cy - 74f * scale
                drawRect(color = AresSurface, topLeft = Offset(cx - 110f * scale, triggerTop), size = Size(triggerW, triggerH))
                drawRect(color = if (keyboardControlEnabled) AresGreen else AresCyan, topLeft = Offset(cx - 110f * scale, triggerTop), size = Size(triggerW * lt.toFloat(), triggerH))
                drawRect(color = AresBorder, topLeft = Offset(cx - 110f * scale, triggerTop), size = Size(triggerW, triggerH), style = Stroke(width = 1.5f * scale))
                
                drawRect(color = AresSurface, topLeft = Offset(cx + 60f * scale, triggerTop), size = Size(triggerW, triggerH))
                drawRect(color = if (keyboardControlEnabled) AresGreen else AresCyan, topLeft = Offset(cx + 60f * scale, triggerTop), size = Size(triggerW * rt.toFloat(), triggerH))
                drawRect(color = AresBorder, topLeft = Offset(cx + 60f * scale, triggerTop), size = Size(triggerW, triggerH), style = Stroke(width = 1.5f * scale))

                // Bumpers (Top Edge L1/R1, directly below triggers)
                val bumperTop = cy - 60f * scale
                val bumperH = 10f * scale
                drawRoundRect(color = if (lb) AresCyan else AresSurface, topLeft = Offset(cx - 110f * scale, bumperTop), size = Size(triggerW, bumperH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale, 4f * scale))
                drawRoundRect(color = AresBorder, topLeft = Offset(cx - 110f * scale, bumperTop), size = Size(triggerW, bumperH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale, 4f * scale), style = Stroke(width = 1.5f * scale))

                drawRoundRect(color = if (rb) AresCyan else AresSurface, topLeft = Offset(cx + 60f * scale, bumperTop), size = Size(triggerW, bumperH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale, 4f * scale))
                drawRoundRect(color = AresBorder, topLeft = Offset(cx + 60f * scale, bumperTop), size = Size(triggerW, bumperH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale, 4f * scale), style = Stroke(width = 1.5f * scale))

                // Touchpad (Center Top)
                val touchPadW = 70f * scale
                val touchPadH = 45f * scale
                drawRoundRect(color = AresSurface, topLeft = Offset(cx - touchPadW / 2f, cy - 45f * scale), size = Size(touchPadW, touchPadH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale))
                drawRoundRect(color = AresBorder, topLeft = Offset(cx - touchPadW / 2f, cy - 45f * scale), size = Size(touchPadW, touchPadH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale), style = Stroke(width = 1.5f * scale))

                // Share / Options (Small buttons beside touchpad)
                val centerBtnW = 10f * scale
                val centerBtnH = 16f * scale
                // Share
                drawRoundRect(color = if (btnBack) AresCyan else AresSurface, topLeft = Offset(cx - 50f * scale, cy - 35f * scale), size = Size(centerBtnW, centerBtnH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale))
                drawRoundRect(color = AresBorder, topLeft = Offset(cx - 50f * scale, cy - 35f * scale), size = Size(centerBtnW, centerBtnH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale), style = Stroke(width = 1.5f * scale))
                // Options
                drawRoundRect(color = if (btnStart) AresCyan else AresSurface, topLeft = Offset(cx + 40f * scale, cy - 35f * scale), size = Size(centerBtnW, centerBtnH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale))
                drawRoundRect(color = AresBorder, topLeft = Offset(cx + 40f * scale, cy - 35f * scale), size = Size(centerBtnW, centerBtnH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale), style = Stroke(width = 1.5f * scale))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "L: (${"%.2f".format(lx)}, ${"%.2f".format(ly)})", color = AresTextSecondary, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(text = "R: (${"%.2f".format(rx)}, ${"%.2f".format(ry)})", color = AresTextSecondary, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(text = "T: ${"%.2f".format(lt)} | ${"%.2f".format(rt)}", color = AresTextSecondary, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}
