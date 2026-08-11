package com.ares.analytics.ui.components.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.service.GamepadState
import com.ares.analytics.ui.components.controls.ControlsEditorPanel
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.viewmodel.controls.ControlsEditorViewModel

/** Persistent, project-backed controls editor presented as a full-height workspace drawer. */
@Composable
fun ControllerBindingsSidebar(
    isOpen: Boolean,
    viewModel: ControlsEditorViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    gamepad1State: GamepadState = GamepadState(),
    gamepad2State: GamepadState = GamepadState()
) {
    val state by viewModel.state.collectAsState()
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxHeight().width(1220.dp)
                .background(AresSurfaceElevated)
                .border(1.dp, AresBorder, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .padding(16.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Gamepad, null, tint = AresCyan)
                Text(" Project controls", fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close controls editor")
                }
            }
            ControlsEditorPanel(
                state = state,
                viewModel = viewModel,
                gamepad1State = gamepad1State,
                gamepad2State = gamepad2State,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Simulator keyboard reference remains available while the visual editor is offline. */
@Composable
fun KeyboardBindingsList() {
    Text("Simulator keyboard mappings are configured separately from robot controller schemes.")
}
