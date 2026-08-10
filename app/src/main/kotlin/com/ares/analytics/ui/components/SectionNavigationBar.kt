package com.ares.analytics.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresCyanGlow
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary

@Composable
fun SectionNavigationBar(
    activeTarget: NavigationTarget,
    onNavigate: (NavigationTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val section = activeTarget.section()
    val targets = section?.targets().orEmpty()
    val title = section?.label ?: activeTarget.groupLabel()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AresSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AresBorder.copy(alpha = 0.55f))
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(section?.icon ?: activeTarget.icon, null, tint = AresCyan, modifier = Modifier.size(19.dp))
            Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (targets.size > 1) {
                Spacer(Modifier.size(6.dp))
                targets.forEach { target ->
                    val selected = target == activeTarget
                    Row(
                        Modifier.clip(RoundedCornerShape(7.dp))
                            .background(if (selected) AresCyanGlow else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onNavigate(target) }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(target.icon, null, tint = if (selected) AresCyan else AresTextTertiary, modifier = Modifier.size(16.dp))
                        Text(target.label, color = if (selected) AresCyan else AresTextSecondary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            } else if (section == null) {
                Text("/", color = AresTextTertiary, modifier = Modifier.padding(horizontal = 3.dp))
                Text(activeTarget.label, color = AresTextSecondary, fontSize = 12.sp)
            }
        }
    }
}
