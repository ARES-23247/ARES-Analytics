package com.ares.analytics.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.shared.League
import com.ares.analytics.ui.theme.*

enum class NavigationTarget(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Speed),
    CLOUD("Cloud Sync", Icons.Default.Cloud),
    REPLAY("What-If Replay", Icons.Default.VideogameAsset),
    PATH_PLANNER("Path Planner", Icons.Default.Route),
    FIELD_EDITOR("Field Editor", Icons.Default.Layers),
    SYSID("SysId", Icons.Default.Analytics),
    RUN_HISTORY("Run History", Icons.Default.TableChart),
    DATABASE_VIEWER("Database", Icons.Default.Storage),
    TUNING("Tuning", Icons.Default.Tune),
    TRIAGE("Pit Triage", Icons.Default.Build),
    PROFILE("Profile", Icons.Default.Person),
    ADMIN("Admin Panel", Icons.Default.SupervisorAccount)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun Sidebar(
    activeTarget: NavigationTarget,
    isConnected: Boolean,
    adbConnected: Boolean,
    isSimRunning: Boolean,
    league: League,
    onNavigate: (NavigationTarget) -> Unit,
    onToggleTerminal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(AresSurface)
            .border(width = 1.dp, color = AresBorder, shape = RoundedCornerShape(0.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ARES brand logo (turns green if simulation running)
            val logoBgColors = if (isSimRunning) {
                listOf(AresGreen, AresGreen.copy(alpha = 0.7f))
            } else {
                listOf(AresRed, AresRedDark)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(logoBgColors)),
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = if (isSimRunning) AresBackground else AresTextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }

            Spacer(Modifier.height(12.dp))

            // Navigation icons
            NavigationTarget.entries.filter { it != NavigationTarget.PROFILE && it != NavigationTarget.ADMIN }.forEach { target ->
                SidebarIcon(
                    target = target,
                    isActive = activeTarget == target,
                    onClick = { onNavigate(target) }
                )
            }
        }

        // Profile, connection, and terminal toggle at the bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Toggle terminal button
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Toggle Terminal Console & Logs (Ctrl+`)") } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = onToggleTerminal, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Terminal, contentDescription = "Terminal Console", tint = AresTextSecondary, modifier = Modifier.size(20.dp))
                }
            }

            // Connection status indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // NT4 Connection status indicator
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("NT4 Telemetry Connection: ${if (isConnected) "Connected" else "Disconnected"}") } },
                    state = rememberTooltipState()
                ) {
                    ConnectionIndicator(connected = isConnected, label = "NT4")
                }
                
                // ADB Connection status indicator (FTC only)
                if (league == League.FTC) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Android ADB Connection: ${if (adbConnected) "Active" else "Inactive"}") } },
                        state = rememberTooltipState()
                    ) {
                        ConnectionIndicator(connected = adbConnected, label = "ADB", activeColor = AresCyan)
                    }
                }
            }

            SidebarIcon(
                target = NavigationTarget.ADMIN,
                isActive = activeTarget == NavigationTarget.ADMIN,
                onClick = { onNavigate(NavigationTarget.ADMIN) }
            )

            SidebarIcon(
                target = NavigationTarget.PROFILE,
                isActive = activeTarget == NavigationTarget.PROFILE,
                onClick = { onNavigate(NavigationTarget.PROFILE) }
            )
        }
    }
}

@Composable
internal fun SidebarIcon(
    target: NavigationTarget,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (isActive) AresCyan else AresTextTertiary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val bgColor by animateColorAsState(
        targetValue = if (isActive) AresCyanGlow else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = target.icon,
                contentDescription = target.label,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = target.label,
            color = if (isActive) AresCyan else AresTextTertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ConnectionIndicator(
    connected: Boolean,
    label: String,
    activeColor: Color = AresGreen
) {
    val dotColor by animateColorAsState(
        targetValue = if (connected) activeColor else AresTextTertiary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(dotColor)
    )
}
