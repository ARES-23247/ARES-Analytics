package com.ares.analytics.service

import com.ares.analytics.ui.help.AcademyRuntimeSnapshot
import com.ares.analytics.ui.help.LearningCatalog
import com.ares.analytics.ui.help.LearningCheckpointEvidence
import com.ares.analytics.ui.help.LearningJourneyEvaluator
import com.ares.analytics.ui.help.LearningProgressView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Durable, local-only progress for the in-app Help & Learn lessons. */
@Serializable
data class LearningProgress(
    val contentVersion: Int = CURRENT_LEARNING_CONTENT_VERSION,
    override val practicedLessonIds: Set<String> = emptySet(),
    override val startedLessonIds: Set<String> = emptySet(),
    override val completedCheckpointIds: Set<String> = emptySet(),
    override val activeLessonId: String? = null,
) : LearningProgressView

/**
 * Stores self-reported lesson practice without claiming certification or hardware verification.
 * Content-version changes retain known lesson IDs and allow the UI to identify updated material.
 */
class LearningProgressService(
    private val progressFile: File = File(
        System.getProperty("user.home"),
        ".ares-analytics/learning-progress.json",
    ),
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val writeMutex = Mutex()
    private val _progress = MutableStateFlow(loadProgress())
    val progress: StateFlow<LearningProgress> = _progress.asStateFlow()

    suspend fun setPracticed(lessonId: String, practiced: Boolean) = withContext(Dispatchers.IO) {
        require(lessonId.isNotBlank()) { "Lesson ID must not be blank" }
        updateProgress { current ->
            current.copy(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                practicedLessonIds = if (practiced) {
                    current.practicedLessonIds + lessonId
                } else {
                    current.practicedLessonIds - lessonId
                },
                startedLessonIds = current.startedLessonIds + lessonId,
                activeLessonId = lessonId,
            )
        }
    }

    suspend fun startLesson(lessonId: String) = withContext(Dispatchers.IO) {
        require(lessonId.isNotBlank()) { "Lesson ID must not be blank" }
        updateProgress { current ->
            current.copy(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                startedLessonIds = current.startedLessonIds + lessonId,
                activeLessonId = lessonId,
            )
        }
    }

    suspend fun setCheckpointCompleted(checkpointId: String, completed: Boolean) = withContext(Dispatchers.IO) {
        require(checkpointId.isNotBlank()) { "Checkpoint ID must not be blank" }
        val checkpoint = LearningCatalog.lessons.asSequence()
            .flatMap { it.checkpoints.asSequence() }
            .firstOrNull { it.id == checkpointId }
        require(checkpoint?.evidence == LearningCheckpointEvidence.SELF_REPORTED) {
            "Only a known student-reflection checkpoint can be changed manually"
        }
        updateProgress { current ->
            current.copy(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                completedCheckpointIds = if (completed) {
                    current.completedCheckpointIds + checkpointId
                } else {
                    current.completedCheckpointIds - checkpointId
                },
            )
        }
    }

    suspend fun clearActiveLesson() = withContext(Dispatchers.IO) {
        updateProgress { current -> current.copy(activeLessonId = null) }
    }

    /** Records only process/connection facts. It never marks reflection, safety, or hardware checks. */
    suspend fun observeRuntime(runtime: AcademyRuntimeSnapshot) = withContext(Dispatchers.IO) {
        updateProgress { current ->
            val observed = LearningJourneyEvaluator.observableCheckpointIds(
                runtime = runtime,
                previouslyCompleted = current.completedCheckpointIds,
            )
            if (observed.isEmpty() || current.completedCheckpointIds.containsAll(observed)) {
                current
            } else {
                current.copy(
                    contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                    completedCheckpointIds = current.completedCheckpointIds + observed,
                    startedLessonIds = current.startedLessonIds + FIRST_MISSION_LESSON_ID,
                    activeLessonId = current.activeLessonId ?: FIRST_MISSION_LESSON_ID,
                )
            }
        }
    }

    private suspend fun updateProgress(transform: (LearningProgress) -> LearningProgress) {
        writeMutex.withLock {
            val current = _progress.value
            val updated = transform(current)
            if (updated == current) return@withLock
            writeFileAtomically(progressFile) { temporary ->
                temporary.writeText(json.encodeToString(updated))
            }
            _progress.value = updated
        }
    }

    private fun loadProgress(): LearningProgress {
        if (!progressFile.isFile) return LearningProgress()
        return runCatching { migrate(json.decodeFromString<LearningProgress>(progressFile.readText())) }
            .getOrElse { LearningProgress() }
    }

    private fun migrate(progress: LearningProgress): LearningProgress = when {
        progress.contentVersion >= CURRENT_LEARNING_CONTENT_VERSION -> progress
        else -> progress.copy(
            contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
            // A v1 practiced lesson was necessarily opened, but it did not have trustworthy
            // checkpoint-level evidence. Preserve the reminder without inventing completion.
            startedLessonIds = progress.startedLessonIds + progress.practicedLessonIds,
        )
    }
}

const val CURRENT_LEARNING_CONTENT_VERSION = 2
private const val FIRST_MISSION_LESSON_ID = "start-simulator"
