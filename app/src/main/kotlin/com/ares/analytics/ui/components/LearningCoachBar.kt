@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ares.analytics.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.LearningProgressService
import com.ares.analytics.ui.help.AcademyRuntimeSnapshot
import com.ares.analytics.ui.help.LearningCheckpointAction
import com.ares.analytics.ui.help.LearningCatalog
import com.ares.analytics.ui.help.LearningJourneyEvaluator
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresOnAccent
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import kotlinx.coroutines.launch

/**
 * Compact, persistent lesson context for MainScreen. The host owns navigation and process actions;
 * this component never enables, deploys to, or sends motion to a physical robot.
 */
@Composable
fun LearningCoachBar(
    progressService: LearningProgressService,
    runtime: AcademyRuntimeSnapshot,
    onOpenAcademy: (lessonId: String) -> Unit,
    onSelectLocalSimulator: () -> Unit,
    onStartSimulator: () -> Unit,
    onOpenDashboard: () -> Unit,
    onStopSimulator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by progressService.progress.collectAsState()
    val scope = rememberCoroutineScope()
    val lesson = progress.activeLessonId?.let(LearningCatalog::lesson) ?: return
    val journey = LearningJourneyEvaluator.lessonState(lesson, progress)
    val checkpoint = journey.currentCheckpoint

    LaunchedEffect(runtime) {
        progressService.observeRuntime(runtime)
    }

    Surface(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = "Robot Academy coach for ${lesson.title}"
            stateDescription = "${journey.status.label}. ${journey.completedCheckpointCount} of ${lesson.checkpoints.size} checkpoints recorded."
        },
        color = AresSurface,
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(10.dp),
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 760.dp
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CoachSummary(lesson.title, journey.status.label, journey.completedCheckpointCount, lesson.checkpoints.size, checkpoint?.title)
                    CoachActions(checkpoint?.action, lesson.id, onOpenAcademy, onSelectLocalSimulator, onStartSimulator, onOpenDashboard, onStopSimulator) {
                        scope.launch { progressService.clearActiveLesson() }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CoachSummary(
                        lesson.title,
                        journey.status.label,
                        journey.completedCheckpointCount,
                        lesson.checkpoints.size,
                        checkpoint?.title,
                        Modifier.weight(1f),
                    )
                    CoachActions(checkpoint?.action, lesson.id, onOpenAcademy, onSelectLocalSimulator, onStartSimulator, onOpenDashboard, onStopSimulator) {
                        scope.launch { progressService.clearActiveLesson() }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachSummary(
    title: String,
    status: String,
    completed: Int,
    total: Int,
    checkpointTitle: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.School, contentDescription = null, tint = AresCyan, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(9.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Robot Academy · $title", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "$status · $completed of $total checkpoints${checkpointTitle?.let { " · Next: $it" }.orEmpty()}",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
            Text(
                if (checkpointTitle == null) "All checkpoints are recorded; reflection is not a safety certification."
                else "ARES records only observable simulator facts automatically.",
                color = AresTextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CoachActions(
    action: LearningCheckpointAction?,
    lessonId: String,
    onOpenAcademy: (String) -> Unit,
    onSelectLocalSimulator: () -> Unit,
    onStartSimulator: () -> Unit,
    onOpenDashboard: () -> Unit,
    onStopSimulator: () -> Unit,
    onHide: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val observed = action == null
        Icon(
            if (observed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (observed) AresGreen else AresCyan,
            modifier = Modifier.size(18.dp),
        )
        Text(if (observed) "Recorded" else "Next step", color = AresTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (action != null && action != LearningCheckpointAction.OPEN_LESSON) {
            Button(
                onClick = when (action) {
                    LearningCheckpointAction.SELECT_LOCAL_SIMULATOR -> onSelectLocalSimulator
                    LearningCheckpointAction.START_SIMULATOR -> onStartSimulator
                    LearningCheckpointAction.OPEN_DASHBOARD -> onOpenDashboard
                    LearningCheckpointAction.STOP_SIMULATOR -> onStopSimulator
                    LearningCheckpointAction.OPEN_LESSON -> error("Handled by the lesson button")
                },
                colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
            ) {
                Text(action.buttonLabel(), fontWeight = FontWeight.Bold)
            }
        }
        OutlinedButton(onClick = { onOpenAcademy(lessonId) }) {
            Text(if (action == LearningCheckpointAction.OPEN_LESSON) "View instructions" else "Lesson")
        }
        OutlinedButton(onClick = onHide) { Text("Hide coach") }
    }
}

private fun LearningCheckpointAction.buttonLabel(): String = when (this) {
    LearningCheckpointAction.SELECT_LOCAL_SIMULATOR -> "Select Local Sim"
    LearningCheckpointAction.START_SIMULATOR -> "Start simulator"
    LearningCheckpointAction.OPEN_DASHBOARD -> "Open Dashboard"
    LearningCheckpointAction.STOP_SIMULATOR -> "Stop simulator"
    LearningCheckpointAction.OPEN_LESSON -> "View instructions"
}
