package com.ares.analytics.ui.components.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.AdvancedAnalyticsReport
import com.ares.analytics.service.AdvancedAnalyticsService
import com.ares.analytics.service.OperationResult
import com.ares.analytics.ui.components.core.AnalyticsCard
import com.ares.analytics.ui.components.core.CardHeader
import com.ares.analytics.ui.theme.AresAmber
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresError
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary

@Composable
fun AdvancedAnalyticsCard(
    analyticsService: AdvancedAnalyticsService,
    sessionId: String?,
    modifier: Modifier = Modifier
) {
    var result by remember(sessionId) { mutableStateOf<OperationResult<AdvancedAnalyticsReport>?>(null) }
    LaunchedEffect(sessionId) {
        result = if (sessionId == null) {
            OperationResult.Unavailable("NO_SESSION", "Select a recorded session to analyze")
        } else {
            analyticsService.analyzeAgainstRecent(sessionId)
        }
    }

    AnalyticsCard(modifier = modifier.fillMaxWidth(), backgroundColor = AresSurfaceElevated) {
        CardHeader("Advanced Analytics", Icons.Default.Insights, AresCyan)
        when (val current = result) {
            null -> CircularProgressIndicator(color = AresCyan)
            is OperationResult.Unavailable -> Text(current.message, color = AresTextSecondary, fontSize = 12.sp)
            is OperationResult.Failure -> Text(current.message, color = AresError, fontSize = 12.sp)
            is OperationResult.Success -> AnalyticsReportContent(current.value)
        }
    }
}

@Composable
private fun AnalyticsReportContent(report: AdvancedAnalyticsReport) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ReportMetric("DRIVER", report.driverScore?.total?.let { "%.0f".format(it) } ?: "--", AresGreen)
        ReportMetric("REGRESSIONS", report.regressions.size.toString(), if (report.regressions.isEmpty()) AresGreen else AresAmber)
        ReportMetric("PATH CELLS", report.pathHeatmap.size.toString(), AresCyan)
        ReportMetric("SUGGESTIONS", report.tuningSuggestions.size.toString(), AresCyan)
    }
    report.diagnostics.take(3).forEach { insight ->
        Text(
            text = "• ${insight.message}",
            color = when (insight.severity.name) {
                "CRITICAL" -> AresError
                "WARNING" -> AresAmber
                else -> AresTextSecondary
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    report.tuningSuggestions.firstOrNull()?.let { suggestion ->
        Text(
            "Top recommendation (${(suggestion.confidence * 100).toInt()}%): ${suggestion.recommendation}",
            color = AresTextTertiary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ReportMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, color = AresTextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
