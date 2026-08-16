package com.ares.analytics.service

import com.ares.analytics.ui.help.AcademyRuntimeSnapshot
import com.ares.analytics.ui.help.LearningCatalog
import com.ares.analytics.ui.help.LearningCheckpointEvidence
import com.ares.analytics.ui.help.LearningJourneyEvaluator
import com.ares.analytics.ui.help.LearningProgressView
import com.ares.analytics.ui.help.LearningRubricRating
import com.ares.analytics.ui.help.AcademyClassroomToolkit
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
    val selectedPathId: String? = null,
    val studentDisplayName: String = "",
    val checkpointReflections: Map<String, String> = emptyMap(),
    val mentorNotes: Map<String, String> = emptyMap(),
    val rubricRatings: Map<String, LearningRubricRating> = emptyMap(),
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

    suspend fun selectPath(pathId: String) = withContext(Dispatchers.IO) {
        requireNotNull(LearningCatalog.path(pathId)) { "Unknown learning path '$pathId'" }
        updateProgress { current ->
            current.copy(contentVersion = CURRENT_LEARNING_CONTENT_VERSION, selectedPathId = pathId)
        }
    }

    suspend fun updateStudentDisplayName(name: String) = withContext(Dispatchers.IO) {
        val normalized = name.trim().take(80)
        updateProgress { current ->
            current.copy(contentVersion = CURRENT_LEARNING_CONTENT_VERSION, studentDisplayName = normalized)
        }
    }

    /** Starts a separate local learner record after the UI has confirmed export/reset intent. */
    suspend fun startNewStudent(name: String) = withContext(Dispatchers.IO) {
        val normalized = name.trim().take(80)
        require(normalized.isNotEmpty()) { "Enter a student display name" }
        updateProgress {
            LearningProgress(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                studentDisplayName = normalized,
                selectedPathId = LearningCatalog.paths.first().id,
            )
        }
    }

    /** Records a student's own explanation; it never converts that reflection into observed runtime evidence. */
    suspend fun recordReflection(checkpointId: String, reflection: String) = withContext(Dispatchers.IO) {
        val checkpoint = requireNotNull(
            LearningCatalog.lessons.asSequence()
                .flatMap { it.checkpoints.asSequence() }
                .firstOrNull { it.id == checkpointId },
        ) { "Unknown learning checkpoint '$checkpointId'" }
        require(checkpoint.evidence == LearningCheckpointEvidence.SELF_REPORTED) {
            "Only a student-reflection checkpoint accepts written reflection"
        }
        val normalized = reflection.trim()
        require(normalized.isNotEmpty()) { "Write a short reflection before recording this checkpoint" }
        require(normalized.length <= MAX_LEARNING_NOTE_LENGTH) { "Reflection is too long" }
        updateProgress { current ->
            current.copy(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                completedCheckpointIds = current.completedCheckpointIds + checkpointId,
                checkpointReflections = current.checkpointReflections + (checkpointId to normalized),
            )
        }
    }

    suspend fun updateMentorNote(lessonId: String, note: String) = withContext(Dispatchers.IO) {
        requireNotNull(LearningCatalog.lesson(lessonId)) { "Unknown lesson '$lessonId'" }
        val normalized = note.trim()
        require(normalized.length <= MAX_LEARNING_NOTE_LENGTH) { "Mentor note is too long" }
        updateProgress { current ->
            current.copy(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                mentorNotes = if (normalized.isEmpty()) current.mentorNotes - lessonId
                else current.mentorNotes + (lessonId to normalized),
            )
        }
    }

    suspend fun setRubricRating(criterionId: String, rating: LearningRubricRating) = withContext(Dispatchers.IO) {
        require(AcademyClassroomToolkit.rubricCriteria.any { it.id == criterionId }) {
            "Unknown learning rubric criterion '$criterionId'"
        }
        updateProgress { current ->
            current.copy(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                rubricRatings = if (rating == LearningRubricRating.NOT_REVIEWED) {
                    current.rubricRatings - criterionId
                } else {
                    current.rubricRatings + (criterionId to rating)
                },
            )
        }
    }

    suspend fun resetLesson(lessonId: String) = withContext(Dispatchers.IO) {
        val lesson = requireNotNull(LearningCatalog.lesson(lessonId)) { "Unknown lesson '$lessonId'" }
        val checkpointIds = lesson.checkpoints.mapTo(mutableSetOf()) { it.id }
        updateProgress { current ->
            current.copy(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                practicedLessonIds = current.practicedLessonIds - lessonId,
                startedLessonIds = current.startedLessonIds - lessonId,
                completedCheckpointIds = current.completedCheckpointIds - checkpointIds,
                activeLessonId = current.activeLessonId?.takeUnless { it == lessonId },
                checkpointReflections = current.checkpointReflections - checkpointIds,
                mentorNotes = current.mentorNotes - lessonId,
            )
        }
    }

    suspend fun resetPath(pathId: String) = withContext(Dispatchers.IO) {
        val path = requireNotNull(LearningCatalog.path(pathId)) { "Unknown learning path '$pathId'" }
        val lessonIds = path.lessonIds.toSet()
        val checkpointIds = lessonIds.asSequence()
            .mapNotNull(LearningCatalog::lesson)
            .flatMap { it.checkpoints.asSequence() }
            .mapTo(mutableSetOf()) { it.id }
        updateProgress { current ->
            current.copy(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                practicedLessonIds = current.practicedLessonIds - lessonIds,
                startedLessonIds = current.startedLessonIds - lessonIds,
                completedCheckpointIds = current.completedCheckpointIds - checkpointIds,
                activeLessonId = current.activeLessonId?.takeUnless { it in lessonIds },
                checkpointReflections = current.checkpointReflections - checkpointIds,
                mentorNotes = current.mentorNotes - lessonIds,
            )
        }
    }

    suspend fun exportMentorReport(
        destination: File,
        pathId: String,
        mentorName: String,
    ) = withContext(Dispatchers.IO) {
        val report = AcademyClassroomToolkit.markdownReport(
            progress = _progress.value,
            pathId = pathId,
            mentorName = mentorName,
        )
        writeFileAtomically(destination) { temporary -> temporary.writeText(report) }
    }

    suspend fun setCheckpointCompleted(checkpointId: String, completed: Boolean) = withContext(Dispatchers.IO) {
        require(checkpointId.isNotBlank()) { "Checkpoint ID must not be blank" }
        val checkpoint = LearningCatalog.lessons.asSequence()
            .flatMap { it.checkpoints.asSequence() }
            .firstOrNull { it.id == checkpointId }
        require(checkpoint?.evidence == LearningCheckpointEvidence.SELF_REPORTED) {
            "Only a known student-reflection checkpoint can be changed manually"
        }
        require(!completed) { "Use recordReflection to complete a student checkpoint with written evidence" }
        updateProgress { current ->
            current.copy(
                contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                completedCheckpointIds = current.completedCheckpointIds - checkpointId,
                checkpointReflections = current.checkpointReflections - checkpointId,
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
                val observedLessonIds = LearningCatalog.lessons.asSequence()
                    .filter { lesson -> lesson.checkpoints.any { it.id in observed } }
                    .map { it.id }
                    .toSet()
                current.copy(
                    contentVersion = CURRENT_LEARNING_CONTENT_VERSION,
                    completedCheckpointIds = current.completedCheckpointIds + observed,
                    startedLessonIds = current.startedLessonIds + observedLessonIds,
                    activeLessonId = current.activeLessonId ?: observedLessonIds.singleOrNull(),
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

const val CURRENT_LEARNING_CONTENT_VERSION = 5
private const val MAX_LEARNING_NOTE_LENGTH = 4_000
