package com.ares.analytics.ui.components.terminal

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.ControllerBinding
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.*

@Composable
fun ControllerBindingsSidebar(
    isOpen: Boolean,
    league: League,
    bindings: List<ControllerBinding>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(1200.dp) // Increased from 900dp for larger view
                .background(AresSurfaceElevated)
                .border(1.dp, AresBorder, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = null,
                        tint = AresCyan
                    )
                    Text(
                        text = "Visual Controller Mappings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AresTextPrimary
                    )
                }
                
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AresTextSecondary)
                }
            }
            
            HorizontalDivider(color = AresBorder, modifier = Modifier.padding(vertical = 12.dp))

            if (bindings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No keybindings detected in active workspace.",
                        color = AresTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                val uniqueFiles = bindings.map { it.sourceFile }.distinct().sorted()
                var selectedOpMode by remember(bindings) { mutableStateOf(uniqueFiles.firstOrNull()) }
                var dropdownExpanded by remember { mutableStateOf(false) }

                if (uniqueFiles.size > 1) {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AresTextPrimary)
                        ) {
                            Text("OpMode: ${selectedOpMode ?: "Unknown"}")
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(AresSurfaceElevated)
                        ) {
                            uniqueFiles.forEach { file ->
                                DropdownMenuItem(
                                    text = { Text(file, color = AresTextPrimary) },
                                    onClick = {
                                        selectedOpMode = file
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                val activeBindings = bindings.filter { it.sourceFile == selectedOpMode }
                val gamepad1Bindings = activeBindings.filter { it.gamepadId == "gamepad1" || !it.gamepadId.contains("2") && !it.gamepadId.contains("operator") }
                val gamepad2Bindings = activeBindings.filter { it.gamepadId == "gamepad2" || it.gamepadId.contains("2") || it.gamepadId.contains("operator") }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    if (gamepad1Bindings.isNotEmpty()) {
                        ControllerVisualizer(
                            title = "Driver (Gamepad 1)",
                            league = league,
                            bindings = gamepad1Bindings
                        )
                    }
                    
                    if (gamepad2Bindings.isNotEmpty()) {
                        ControllerVisualizer(
                            title = "Operator (Gamepad 2)",
                            league = league,
                            bindings = gamepad2Bindings
                        )
                    }

                    KeyboardBindingsList()
                }
            }
        }
    }
}

@Composable
fun ControllerVisualizer(
    title: String,
    league: League,
    bindings: List<ControllerBinding>
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = AresCyan,
            fontSize = 18.sp
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(800.dp) // Increased from 600dp
                .background(AresBackground, RoundedCornerShape(12.dp))
                .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val resourceName = if (league == League.FRC) "drawable/frc_controller.png" else "drawable/ftc_controller.png"
            
            Image(
                painter = painterResource(resourceName),
                contentDescription = "Controller Graphic",
                modifier = Modifier.fillMaxSize(0.9f), // Increased from 0.8f
                contentScale = ContentScale.Fit
            )
            
            // Map buttons to approx screen coordinates (offset from center)
            bindings.forEach { binding ->
                val offset = getButtonOffset(binding.button, league)
                if (offset != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = offset.first, y = offset.second)
                    ) {
                        BindingTag(binding)
                    }
                } else {
                    // Fallback for unmapped buttons (put them at the top)
                    Box(modifier = Modifier.align(Alignment.TopCenter)) {
                        BindingTag(binding)
                    }
                }
            }
        }
    }
}

@Composable
fun BindingTag(binding: ControllerBinding) {
    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            .border(1.dp, AresCyan, RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = binding.button.uppercase(),
            color = AresGold,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = binding.action,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// Approximate offsets (X, Y) from the center of the controller image
private fun getButtonOffset(button: String, league: League, scale: Float = 1.3f): Pair<Dp, Dp>? {
    val isXbox = league == League.FRC
    
    val baseOffset = when (button.lowercase()) {
        "a", "cross" -> Pair(180.dp, 60.dp)
        "b", "circle" -> Pair(240.dp, 0.dp)
        "x", "square" -> Pair(120.dp, 0.dp)
        "y", "triangle" -> Pair(180.dp, (-60).dp)
        
        "dpadup" -> Pair(if (isXbox) (-90).dp else (-180).dp, if (isXbox) 90.dp else (-60).dp)
        "dpaddown" -> Pair(if (isXbox) (-90).dp else (-180).dp, if (isXbox) 180.dp else 60.dp)
        "dpadleft" -> Pair(if (isXbox) (-135).dp else (-240).dp, if (isXbox) 135.dp else 0.dp)
        "dpadright" -> Pair(if (isXbox) (-45).dp else (-120).dp, if (isXbox) 135.dp else 0.dp)
        
        "left_bumper", "leftbumper" -> Pair((-180).dp, (-210).dp)
        "right_bumper", "rightbumper" -> Pair(180.dp, (-210).dp)
        "left_trigger", "lefttrigger" -> Pair((-180).dp, (-270).dp)
        "right_trigger", "righttrigger" -> Pair(180.dp, (-270).dp)
        
        "start", "options" -> Pair(60.dp, (-90).dp)
        "back", "share" -> Pair((-60).dp, (-90).dp)
        "touchpad" -> Pair(0.dp, (-110).dp)
        
        "left_stick_button", "leftstick", "leftstickx", "leftsticky" -> Pair(if (isXbox) (-180).dp else (-90).dp, if (isXbox) (-60).dp else 90.dp)
        "right_stick_button", "rightstick", "rightstickx", "rightsticky" -> Pair(if (isXbox) 90.dp else 90.dp, 90.dp)
        
        else -> null
    }
    
    return baseOffset?.let { Pair(it.first * scale, it.second * scale) }
}

@Composable
fun KeyboardBindingsList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AresSurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.dp, AresBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Simulator Keyboard Bindings",
            fontWeight = FontWeight.Bold,
            color = AresCyan,
            fontSize = 18.sp
        )

        val keys = listOf(
            "Left Stick" to "W / A / S / D",
            "Right Stick" to "Arrow Keys",
            "A / Cross" to "J",
            "B / Circle" to "L",
            "X / Square" to "U",
            "Y / Triangle" to "I",
            "Left Bumper" to "Q",
            "Right Bumper" to "E",
            "Left Trigger" to "Space",
            "Right Trigger" to "Shift"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { (action, key) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = action,
                        color = AresTextSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = key,
                        color = AresTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                HorizontalDivider(color = AresBorder.copy(alpha = 0.5f))
            }
        }
    }
}
