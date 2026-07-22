package com.ares.analytics.ui.components.pathplanner.cards

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*

@Composable
fun CollapsibleSection(
    title: String,
    badgeCount: Int? = null,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, AresBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = AresSurfaceElevated),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
                    if (badgeCount != null) {
                        Surface(
                            shape = CircleShape,
                            color = AresCyan.copy(alpha = 0.2f),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(badgeCount.toString(), fontSize = 10.sp, color = AresCyan, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AresCyan.copy(alpha = 0.2f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(badgeText, fontSize = 10.sp, color = AresCyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand",
                    tint = AresTextSecondary
                )
            }
            if (expanded) {
                HorizontalDivider(color = AresBorder)
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}
