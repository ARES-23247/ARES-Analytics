package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresThemeSettings

@Composable
fun DashboardCommandBar(
    profileName: String,
    isEditing: Boolean,
    onToggleEditing: () -> Unit,
    onAddWidget: () -> Unit,
    onResetLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controlHeight = if (AresThemeSettings.touchOptimizedMode) 48.dp else 38.dp
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AresSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isEditing) AresCyan.copy(alpha = 0.65f) else AresBorder.copy(alpha = 0.55f))
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 760.dp
            val controls: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isEditing) {
                        OutlinedButton(onClick = onResetLayout, modifier = Modifier.height(controlHeight)) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reset")
                        }
                        Button(
                            onClick = onAddWidget,
                            modifier = Modifier.height(controlHeight),
                            colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresBackground)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add widget")
                        }
                    }
                    OutlinedButton(onClick = onToggleEditing, modifier = Modifier.height(controlHeight)) {
                        Icon(if (isEditing) Icons.Default.Check else Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEditing) "Done" else "Edit layout")
                    }
                }
            }
            val title: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$profileName dashboard", color = AresTextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Text(if (isEditing) "Layout editing" else "Operational view", color = if (isEditing) AresCyan else AresTextSecondary)
                }
            }
            if (compact) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    title()
                    controls()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    title()
                    controls()
                }
            }
        }
    }
}
