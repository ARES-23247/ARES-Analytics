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
    onOpenKeybindings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val keyboardState = services?.keyboardDriveState ?: remember { com.ares.analytics.di.KeyboardDriveState() }
    val keyboardControlEnabled = keyboardState.enabled

    // Keyboard controller publishing loop
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
                modifier = Modifier.weight(1f)
            )
            SingleGamepadVisualizer(
                title = "Gamepad 2 (Operator)",
                gamepadId = "Gamepad2",
                currentFrame = currentFrame,
                nt4ClientService = nt4ClientService,
                keyboardControlEnabled = false,
                keyboardState = null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SingleGamepadVisualizer(
    title: String,
    gamepadId: String,
    currentFrame: ReplayFrame?,
    nt4ClientService: Nt4ClientService?,
    keyboardControlEnabled: Boolean,
    keyboardState: com.ares.analytics.di.KeyboardDriveState?,
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

    if (currentFrame != null) {
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
    } else if (nt4ClientService != null && !keyboardControlEnabled) {
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

    if (keyboardControlEnabled && keyboardState != null) {
        LaunchedEffect(Unit) {
            while (true) {
                lx = if (keyboardState.isAPressed) -1.0 else if (keyboardState.isDPressed) 1.0 else 0.0
                ly = if (keyboardState.isWPressed) -1.0 else if (keyboardState.isSPressed) 1.0 else 0.0
                rx = if (keyboardState.isQPressed) -1.0 else if (keyboardState.isEPressed) 1.0 else 0.0
                ry = 0.0
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
                val bodyW = 340f * scale
                val bodyH = 180f * scale

                drawRoundRect(
                    color = AresSurfaceElevated,
                    topLeft = Offset(cx - bodyW / 2f, cy - bodyH / 2f),
                    size = Size(bodyW, bodyH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f * scale, 40f * scale)
                )
                drawRoundRect(
                    color = if (keyboardControlEnabled) AresGreen else AresBorder,
                    topLeft = Offset(cx - bodyW / 2f, cy - bodyH / 2f),
                    size = Size(bodyW, bodyH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f * scale, 40f * scale),
                    style = Stroke(width = 2.5f * scale)
                )

                val stickRadius = 32f * scale
                val leftStickCenter = Offset(cx - 85f * scale, cy + 15f * scale)
                val rightStickCenter = Offset(cx + 45f * scale, cy + 15f * scale)

                drawCircle(color = AresSurface, radius = stickRadius, center = leftStickCenter)
                if (btnLS) drawCircle(color = AresCyan.copy(alpha = 0.3f), radius = stickRadius, center = leftStickCenter)
                drawCircle(color = AresBorder, radius = stickRadius, center = leftStickCenter, style = Stroke(width = 1.5f * scale))
                val lNodeX = leftStickCenter.x + (lx * stickRadius * 0.7).toFloat()
                val lNodeY = leftStickCenter.y + (ly * stickRadius * 0.7).toFloat()
                drawCircle(color = if (keyboardControlEnabled) AresGreen else AresCyan, radius = 12f * scale, center = Offset(lNodeX, lNodeY))

                drawCircle(color = AresSurface, radius = stickRadius, center = rightStickCenter)
                if (btnRS) drawCircle(color = AresCyan.copy(alpha = 0.3f), radius = stickRadius, center = rightStickCenter)
                drawCircle(color = AresBorder, radius = stickRadius, center = rightStickCenter, style = Stroke(width = 1.5f * scale))
                val rNodeX = rightStickCenter.x + (rx * stickRadius * 0.7).toFloat()
                val rNodeY = rightStickCenter.y + (ry * stickRadius * 0.7).toFloat()
                drawCircle(color = if (keyboardControlEnabled) AresGreen else AresCyan, radius = 12f * scale, center = Offset(rNodeX, rNodeY))

                val dpadCenter = Offset(cx - 135f * scale, cy - 25f * scale)
                val dpadSize = 16f * scale
                drawRect(color = AresSurface, topLeft = Offset(dpadCenter.x - dpadSize * 2.5f, dpadCenter.y - dpadSize / 2f), size = Size(dpadSize * 5f, dpadSize))
                drawRect(color = AresSurface, topLeft = Offset(dpadCenter.x - dpadSize / 2f, dpadCenter.y - dpadSize * 2.5f), size = Size(dpadSize, dpadSize * 5f))
                drawRect(color = if (dpadLeft) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x - dpadSize * 2.5f, dpadCenter.y - dpadSize / 2f), size = Size(dpadSize, dpadSize))
                drawRect(color = if (dpadRight) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x + dpadSize * 1.5f, dpadCenter.y - dpadSize / 2f), size = Size(dpadSize, dpadSize))
                drawRect(color = if (dpadUp) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x - dpadSize / 2f, dpadCenter.y - dpadSize * 2.5f), size = Size(dpadSize, dpadSize))
                drawRect(color = if (dpadDown) AresCyan else AresBorder, topLeft = Offset(dpadCenter.x - dpadSize / 2f, dpadCenter.y + dpadSize * 1.5f), size = Size(dpadSize, dpadSize))

                val buttonsCenter = Offset(cx + 105f * scale, cy - 25f * scale)
                val btnRadius = 12f * scale
                val btnOffset = 24f * scale
                drawCircle(color = if (btnY) AresGold else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y - btnOffset))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y - btnOffset), style = Stroke(width = 1.5f * scale))
                drawCircle(color = if (btnA) AresGold else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y + btnOffset))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x, buttonsCenter.y + btnOffset), style = Stroke(width = 1.5f * scale))
                drawCircle(color = if (btnX) AresRed else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x - btnOffset, buttonsCenter.y))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x - btnOffset, buttonsCenter.y), style = Stroke(width = 1.5f * scale))
                drawCircle(color = if (btnB) AresCyan else AresSurface, radius = btnRadius, center = Offset(buttonsCenter.x + btnOffset, buttonsCenter.y))
                drawCircle(color = AresBorder, radius = btnRadius, center = Offset(buttonsCenter.x + btnOffset, buttonsCenter.y), style = Stroke(width = 1.5f * scale))

                val triggerW = 60f * scale
                val triggerH = 14f * scale
                // Triggers
                drawRect(color = AresSurface, topLeft = Offset(cx - 120f * scale, cy - bodyH / 2f - 22f * scale), size = Size(triggerW, triggerH))
                drawRect(color = if (keyboardControlEnabled) AresGreen else AresCyan, topLeft = Offset(cx - 120f * scale, cy - bodyH / 2f - 22f * scale), size = Size(triggerW * lt.toFloat(), triggerH))
                drawRect(color = AresBorder, topLeft = Offset(cx - 120f * scale, cy - bodyH / 2f - 22f * scale), size = Size(triggerW, triggerH), style = Stroke(width = 1.5f * scale))
                
                drawRect(color = AresSurface, topLeft = Offset(cx + 60f * scale, cy - bodyH / 2f - 22f * scale), size = Size(triggerW, triggerH))
                drawRect(color = if (keyboardControlEnabled) AresGreen else AresCyan, topLeft = Offset(cx + 60f * scale, cy - bodyH / 2f - 22f * scale), size = Size(triggerW * rt.toFloat(), triggerH))
                drawRect(color = AresBorder, topLeft = Offset(cx + 60f * scale, cy - bodyH / 2f - 22f * scale), size = Size(triggerW, triggerH), style = Stroke(width = 1.5f * scale))

                // Bumpers
                val bumperW = 60f * scale
                val bumperH = 10f * scale
                drawRect(color = if (lb) AresCyan else AresSurface, topLeft = Offset(cx - 120f * scale, cy - bodyH / 2f - 4f * scale), size = Size(bumperW, bumperH))
                drawRect(color = AresBorder, topLeft = Offset(cx - 120f * scale, cy - bodyH / 2f - 4f * scale), size = Size(bumperW, bumperH), style = Stroke(width = 1.5f * scale))

                drawRect(color = if (rb) AresCyan else AresSurface, topLeft = Offset(cx + 60f * scale, cy - bodyH / 2f - 4f * scale), size = Size(bumperW, bumperH))
                drawRect(color = AresBorder, topLeft = Offset(cx + 60f * scale, cy - bodyH / 2f - 4f * scale), size = Size(bumperW, bumperH), style = Stroke(width = 1.5f * scale))

                // Start / Back / Touchpad
                val centerBtnW = 20f * scale
                val centerBtnH = 12f * scale
                // Back / Share
                drawRoundRect(color = if (btnBack) AresCyan else AresSurface, topLeft = Offset(cx - 40f * scale, cy - 30f * scale), size = Size(centerBtnW, centerBtnH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale))
                drawRoundRect(color = AresBorder, topLeft = Offset(cx - 40f * scale, cy - 30f * scale), size = Size(centerBtnW, centerBtnH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale), style = Stroke(width = 1.5f * scale))
                
                // Start / Options
                drawRoundRect(color = if (btnStart) AresCyan else AresSurface, topLeft = Offset(cx + 20f * scale, cy - 30f * scale), size = Size(centerBtnW, centerBtnH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale))
                drawRoundRect(color = AresBorder, topLeft = Offset(cx + 20f * scale, cy - 30f * scale), size = Size(centerBtnW, centerBtnH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale), style = Stroke(width = 1.5f * scale))

                // Touchpad
                val touchPadW = 80f * scale
                val touchPadH = 50f * scale
                drawRoundRect(color = AresSurface, topLeft = Offset(cx - touchPadW / 2f, cy - touchPadH / 2f - 50f * scale), size = Size(touchPadW, touchPadH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale))
                drawRoundRect(color = AresBorder, topLeft = Offset(cx - touchPadW / 2f, cy - touchPadH / 2f - 50f * scale), size = Size(touchPadW, touchPadH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale), style = Stroke(width = 1.5f * scale))
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
