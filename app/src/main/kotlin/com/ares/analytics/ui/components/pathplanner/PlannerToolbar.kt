package com.ares.analytics.ui.components.pathplanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.ui.theme.*

@Composable
fun PlannerToolbar(
    estimatedDuration: Double
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Path Planner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AresTextPrimary)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = AresCyan.copy(alpha = 0.2f)
            ) {
                Text(
                    text = String.format("~%.2fs", estimatedDuration),
                    color = AresCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        HorizontalDivider(color = AresBorder, modifier = Modifier.padding(vertical = 8.dp))
    }
}
