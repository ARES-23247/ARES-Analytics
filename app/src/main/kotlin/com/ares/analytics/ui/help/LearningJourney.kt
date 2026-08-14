package com.ares.analytics.ui.help

interface LearningProgressView {
    val practicedLessonIds: Set<String>
    val startedLessonIds: Set<String>
    val completedCheckpointIds: Set<String>
    val activeLessonId: String?
}

/** Runtime facts the desktop can observe without asking a student to make a safety claim. */
data class AcademyRuntimeSnapshot(
    val isAvailable: Boolean = false,
    val isLocalSimulatorSelected: Boolean = false,
    val isSimulatorRunning: Boolean = false,
    val isLocalSimulatorOnline: Boolean = false,
    val isNt4Connected: Boolean = false,
) {
    companion object {
        val Unavailable = AcademyRuntimeSnapshot()
    }
}

enum class LearningCheckpointEvidence {
    /** A student must confirm this learning or interpretation themselves. */
    SELF_REPORTED,

    /** The app can see that Local Sim is the selected execution target. */
    LOCAL_SIMULATOR_SELECTED,

    /** The app can see its managed simulator process running. */
    SIMULATOR_RUNNING,

    /** NT4 and the local simulator scanner both identify a live local simulation. */
    LOCAL_SIMULATOR_CONNECTED,

    /** A previously observed simulator process is no longer running. */
    SIMULATOR_STOPPED_AFTER_RUNNING,
}

enum class LearningCheckpointAction {
    SELECT_LOCAL_SIMULATOR,
    START_SIMULATOR,
    OPEN_DASHBOARD,
    STOP_SIMULATOR,
    OPEN_LESSON,
}

data class LearningCheckpoint(
    val id: String,
    val title: String,
    val instruction: String,
    val successText: String,
    val evidence: LearningCheckpointEvidence = LearningCheckpointEvidence.SELF_REPORTED,
    val action: LearningCheckpointAction = LearningCheckpointAction.OPEN_LESSON,
)

enum class LearningLessonStatus(val label: String) {
    NOT_STARTED("Not started"),
    IN_PROGRESS("In progress"),
    PRACTICED("Practiced"),
    RECOMMENDED_LATER("Recommended later"),
}

data class LearningLessonJourneyState(
    val lesson: LearningLesson,
    val status: LearningLessonStatus,
    val prerequisitesMet: Boolean,
    val completedCheckpointIds: Set<String>,
    val currentCheckpoint: LearningCheckpoint?,
) {
    val completedCheckpointCount: Int
        get() = lesson.checkpoints.count { it.id in completedCheckpointIds }
}

/**
 * Pure learning-state rules. These rules never infer physical safety, understanding, or certification
 * from a connection or process status.
 */
object LearningJourneyEvaluator {
    fun observableCheckpointIds(
        runtime: AcademyRuntimeSnapshot,
        previouslyCompleted: Set<String>,
    ): Set<String> = if (!runtime.isAvailable) emptySet() else buildSet {
        if (runtime.isLocalSimulatorSelected) {
            add(FirstMissionCheckpointIds.LOCAL_SIM_SELECTED)
        }
        if (runtime.isSimulatorRunning) {
            add(FirstMissionCheckpointIds.SIMULATOR_RUNNING)
        }
        if (
            runtime.isLocalSimulatorSelected &&
            runtime.isLocalSimulatorOnline &&
            runtime.isNt4Connected
        ) {
            add(FirstMissionCheckpointIds.LOCAL_SIM_CONNECTED)
        }
        if (
            FirstMissionCheckpointIds.SIMULATOR_RUNNING in previouslyCompleted &&
            !runtime.isSimulatorRunning
        ) {
            add(FirstMissionCheckpointIds.SIMULATOR_STOPPED)
        }
    }

    fun lessonState(
        lesson: LearningLesson,
        progress: LearningProgressView,
    ): LearningLessonJourneyState {
        val prerequisitesMet = lesson.prerequisiteLessonIds.all { it in progress.practicedLessonIds }
        val relevantCompleted = progress.completedCheckpointIds.intersect(lesson.checkpoints.mapTo(mutableSetOf()) { it.id })
        val status = when {
            lesson.id in progress.practicedLessonIds -> LearningLessonStatus.PRACTICED
            !prerequisitesMet -> LearningLessonStatus.RECOMMENDED_LATER
            lesson.id in progress.startedLessonIds || relevantCompleted.isNotEmpty() -> LearningLessonStatus.IN_PROGRESS
            else -> LearningLessonStatus.NOT_STARTED
        }
        return LearningLessonJourneyState(
            lesson = lesson,
            status = status,
            prerequisitesMet = prerequisitesMet,
            completedCheckpointIds = relevantCompleted,
            currentCheckpoint = lesson.checkpoints.firstOrNull { it.id !in relevantCompleted },
        )
    }

    fun recommendedLesson(path: LearningPath, progress: LearningProgressView): LearningLesson? {
        val lessons = path.lessonIds.mapNotNull(LearningCatalog::lesson)
        val next = lessons.firstOrNull { it.id !in progress.practicedLessonIds } ?: return null
        return firstUnpracticedPrerequisite(next, progress, linkedSetOf()) ?: next
    }

    private fun firstUnpracticedPrerequisite(
        lesson: LearningLesson,
        progress: LearningProgressView,
        visiting: MutableSet<String>,
    ): LearningLesson? {
        check(visiting.add(lesson.id)) { "Learning prerequisite cycle includes '${lesson.id}'" }
        for (prerequisiteId in lesson.prerequisiteLessonIds) {
            if (prerequisiteId in progress.practicedLessonIds) continue
            val prerequisite = requireNotNull(LearningCatalog.lesson(prerequisiteId)) {
                "Learning lesson '${lesson.id}' references missing prerequisite '$prerequisiteId'"
            }
            return firstUnpracticedPrerequisite(prerequisite, progress, visiting) ?: prerequisite
        }
        visiting.remove(lesson.id)
        return null
    }
}

object FirstMissionCheckpointIds {
    const val LOCAL_SIM_SELECTED = "first-mission.local-sim-selected"
    const val SIMULATOR_RUNNING = "first-mission.simulator-running"
    const val LOCAL_SIM_CONNECTED = "first-mission.local-sim-connected"
    const val IDENTIFIED_DATA_SOURCE = "first-mission.identified-data-source"
    const val SIMULATOR_STOPPED = "first-mission.simulator-stopped"
}
