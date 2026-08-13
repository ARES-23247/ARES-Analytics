package com.ares.analytics.ui.screens

import com.ares.analytics.ui.theme.AresOnAccent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ares.analytics.service.LearningProgressService
import com.ares.analytics.ui.components.NavigationTarget
import com.ares.analytics.ui.help.LearningAction
import com.ares.analytics.ui.help.LearningCatalog
import com.ares.analytics.ui.help.LearningLesson
import com.ares.analytics.ui.help.LearningLevel
import com.ares.analytics.ui.theme.AresAmber
import com.ares.analytics.ui.theme.AresBackground
import com.ares.analytics.ui.theme.AresBorder
import com.ares.analytics.ui.theme.AresCyan
import com.ares.analytics.ui.theme.AresCyanGlow
import com.ares.analytics.ui.theme.AresGreen
import com.ares.analytics.ui.theme.AresSurface
import com.ares.analytics.ui.theme.AresSurfaceElevated
import com.ares.analytics.ui.theme.AresTextPrimary
import com.ares.analytics.ui.theme.AresTextSecondary
import com.ares.analytics.ui.theme.AresTextTertiary
import com.ares.analytics.ui.theme.AresBrandDestination
import com.ares.analytics.ui.theme.openAresBrandDestination
import kotlinx.coroutines.launch

/**
 * Task-based learning center backed by real ARES workflows.
 *
 * “Practiced” is intentionally self-reported and local. It never implies certification, code
 * correctness, or physical robot verification.
 */
@Composable
fun AcademyScreen(
    progressService: LearningProgressService,
    onOpenScreen: (NavigationTarget) -> Unit,
    onStartSimulator: () -> Unit,
    initialLessonId: String? = null,
) {
    val progress by progressService.progress.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LearningLevel?>(LearningLevel.STARTER) }
    var selectedLessonId by remember { mutableStateOf(LearningCatalog.lessons.first().id) }
    LaunchedEffect(initialLessonId) {
        val requested = LearningCatalog.lessons.firstOrNull { it.id == initialLessonId }
        if (requested != null) {
            selectedLessonId = requested.id
            selectedLevel = requested.level
            query = ""
        }
    }
    val matches = remember(query, selectedLevel) { LearningCatalog.search(query, selectedLevel) }
    val selectedLesson = LearningCatalog.lessons.firstOrNull { it.id == selectedLessonId }
        ?: matches.firstOrNull()

    Row(
        modifier = Modifier.fillMaxSize().background(AresBackground).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.width(360.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LearningHeader(progress.practicedLessonIds.size, LearningCatalog.lessons.size)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("What do you want to learn?") },
                placeholder = { Text("Try: simulator, disconnected, logs, Redux…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LearningLevel.entries.forEach { level ->
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = if (selectedLevel == level) null else level },
                        label = { Text(level.label, fontSize = 12.sp) },
                    )
                }
            }
            Text(
                selectedLevel?.explanation ?: "All learning levels",
                color = AresTextSecondary,
                fontSize = 12.sp,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matches, key = LearningLesson::id) { lesson ->
                    LessonListCard(
                        lesson = lesson,
                        practiced = lesson.id in progress.practicedLessonIds,
                        selected = lesson.id == selectedLesson?.id,
                        onClick = { selectedLessonId = lesson.id },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            color = AresSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AresBorder),
        ) {
            if (selectedLesson == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No lessons match that search.", color = AresTextSecondary)
                }
            } else {
                LessonDetail(
                    lesson = selectedLesson,
                    practiced = selectedLesson.id in progress.practicedLessonIds,
                    onLaunch = {
                        when (selectedLesson.action) {
                            LearningAction.OPEN_SCREEN -> onOpenScreen(selectedLesson.destination)
                            LearningAction.START_SIMULATOR -> onStartSimulator()
                        }
                    },
                    onPracticedChange = { practiced ->
                        scope.launch { progressService.setPracticed(selectedLesson.id, practiced) }
                    },
                )
            }
        }
    }
}

@Composable
private fun LearningHeader(practiced: Int, total: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AresSurface),
        border = BorderStroke(1.dp, AresBorder),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.School, null, tint = AresCyan, modifier = Modifier.size(26.dp))
                Text("Help & Learn", color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Text(
                "Learn by completing real tasks in the app—from first simulation to safe subsystem design.",
                color = AresTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text("Practiced locally: $practiced of $total", color = AresGreen, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AresResourceButton(AresBrandDestination.TEAM_WEBSITE)
                AresResourceButton(AresBrandDestination.TEAM_GITHUB)
            }
        }
    }
}

@Composable
private fun AresResourceButton(destination: AresBrandDestination) {
    OutlinedButton(onClick = { openAresBrandDestination(destination) }) {
        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(destination.buttonLabel, fontSize = 11.sp)
    }
}

@Composable
private fun LessonListCard(lesson: LearningLesson, practiced: Boolean, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) AresCyanGlow else AresSurface),
        border = BorderStroke(1.dp, if (selected) AresCyan else AresBorder),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (practiced) Icons.Default.CheckCircle else if (lesson.requiresRobot) Icons.Default.Lock else Icons.Default.PlayArrow,
                null,
                tint = if (practiced) AresGreen else if (lesson.requiresRobot) AresAmber else AresCyan,
                modifier = Modifier.size(19.dp),
            )
            Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(lesson.title, color = AresTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(lesson.outcome, color = AresTextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Text(
                    "${lesson.track.label} • ${lesson.durationMinutes} min${if (lesson.requiresRobot) " • Robot later" else " • No robot needed"}",
                    color = AresTextTertiary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun LessonDetail(
    lesson: LearningLesson,
    practiced: Boolean,
    onLaunch: () -> Unit,
    onPracticedChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(lesson.level.label, color = AresCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(lesson.title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(6.dp))
            Text(lesson.outcome, color = AresTextSecondary, fontSize = 15.sp, lineHeight = 22.sp)
        }
        item { HorizontalDivider(color = AresBorder) }
        item { LessonSection("Before you start", lesson.beforeYouStart) }
        item { LessonSection("Do this", lesson.steps, numbered = true) }
        lesson.safetyNote?.let { note ->
            item {
                Surface(
                    color = AresAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AresAmber.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Safety note", color = AresAmber, fontWeight = FontWeight.Bold)
                        Text(note, color = AresTextPrimary, lineHeight = 20.sp)
                    }
                }
            }
        }
        item {
            Surface(color = AresSurfaceElevated, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("What success looks like", color = AresGreen, fontWeight = FontWeight.Bold)
                    Text(lesson.successLooksLike, color = AresTextPrimary, lineHeight = 20.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = AresCyan, contentColor = AresOnAccent),
                ) {
                    Icon(if (lesson.action == LearningAction.START_SIMULATOR) Icons.Default.PlayArrow else Icons.AutoMirrored.Filled.Launch, null)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (lesson.action == LearningAction.START_SIMULATOR) "Start local simulator" else "Open ${lesson.destination.label}",
                        color = AresBackground,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(onClick = { onPracticedChange(!practiced) }) {
                    Text(if (practiced) "Remove practiced mark" else "Mark as practiced")
                }
            }
            Text(
                "Practice marks are private reminders—not grades, certification, or proof of robot safety.",
                color = AresTextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
    }
}

@Composable
private fun LessonSection(title: String, lines: List<String>, numbered: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AresTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        lines.forEachIndexed { index, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                Text(if (numbered) "${index + 1}." else "•", color = AresCyan, fontWeight = FontWeight.Bold)
                Text(line, color = AresTextSecondary, lineHeight = 20.sp)
            }
        }
    }
}
