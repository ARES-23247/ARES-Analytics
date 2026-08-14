package com.ares.analytics.ui.help

import com.ares.analytics.service.LearningProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningJourneyTest {
    @Test
    fun `observable evaluator requires an explicit available runtime`() {
        assertTrue(
            LearningJourneyEvaluator.observableCheckpointIds(
                AcademyRuntimeSnapshot.Unavailable,
                setOf(FirstMissionCheckpointIds.SIMULATOR_RUNNING),
            ).isEmpty(),
        )
    }

    @Test
    fun `local connection requires selected online simulator and NT4`() {
        val incomplete = LearningJourneyEvaluator.observableCheckpointIds(
            AcademyRuntimeSnapshot(
                isAvailable = true,
                isLocalSimulatorSelected = true,
                isSimulatorRunning = true,
                isLocalSimulatorOnline = false,
                isNt4Connected = true,
            ),
            emptySet(),
        )
        assertTrue(FirstMissionCheckpointIds.SIMULATOR_RUNNING in incomplete)
        assertFalse(FirstMissionCheckpointIds.LOCAL_SIM_CONNECTED in incomplete)

        val connected = LearningJourneyEvaluator.observableCheckpointIds(
            AcademyRuntimeSnapshot(
                isAvailable = true,
                isLocalSimulatorSelected = true,
                isSimulatorRunning = true,
                isLocalSimulatorOnline = true,
                isNt4Connected = true,
            ),
            incomplete,
        )
        assertTrue(FirstMissionCheckpointIds.LOCAL_SIM_CONNECTED in connected)
    }

    @Test
    fun `stopped fact is recorded only after a running process was observed`() {
        val stoppedBeforeRun = LearningJourneyEvaluator.observableCheckpointIds(
            AcademyRuntimeSnapshot(isAvailable = true),
            emptySet(),
        )
        assertFalse(FirstMissionCheckpointIds.SIMULATOR_STOPPED in stoppedBeforeRun)

        val stoppedAfterRun = LearningJourneyEvaluator.observableCheckpointIds(
            AcademyRuntimeSnapshot(isAvailable = true),
            setOf(FirstMissionCheckpointIds.SIMULATOR_RUNNING),
        )
        assertTrue(FirstMissionCheckpointIds.SIMULATOR_STOPPED in stoppedAfterRun)
    }

    @Test
    fun `lesson prerequisites guide sequence without claiming certification`() {
        val lesson = LearningCatalog.lesson("read-connection-state") ?: error("Missing lesson")
        val waiting = LearningJourneyEvaluator.lessonState(lesson, LearningProgress())
        assertEquals(LearningLessonStatus.RECOMMENDED_LATER, waiting.status)
        assertFalse(waiting.prerequisitesMet)

        val available = LearningJourneyEvaluator.lessonState(
            lesson,
            LearningProgress(practicedLessonIds = setOf("start-simulator")),
        )
        assertEquals(LearningLessonStatus.NOT_STARTED, available.status)
        assertTrue(available.prerequisitesMet)
    }

    @Test
    fun `recommended lesson follows practiced prerequisites`() {
        val path = LearningCatalog.path("first-mission") ?: error("Missing path")
        assertEquals("start-simulator", LearningJourneyEvaluator.recommendedLesson(path, LearningProgress())?.id)
        assertEquals(
            "read-connection-state",
            LearningJourneyEvaluator.recommendedLesson(
                path,
                LearningProgress(practicedLessonIds = setOf("start-simulator")),
            )?.id,
        )
        assertNull(
            LearningJourneyEvaluator.recommendedLesson(
                path,
                LearningProgress(practicedLessonIds = path.lessonIds.toSet()),
            ),
        )
    }

    @Test
    fun `role paths lead a new student through prerequisites outside the selected path`() {
        val builder = LearningCatalog.path("robot-builder") ?: error("Missing robot builder path")

        assertEquals(
            "start-simulator",
            LearningJourneyEvaluator.recommendedLesson(builder, LearningProgress())?.id,
        )
        assertEquals(
            "read-connection-state",
            LearningJourneyEvaluator.recommendedLesson(
                builder,
                LearningProgress(practicedLessonIds = setOf("start-simulator")),
            )?.id,
        )
        assertEquals(
            "robot-studio-tour",
            LearningJourneyEvaluator.recommendedLesson(
                builder,
                LearningProgress(practicedLessonIds = setOf("start-simulator", "read-connection-state")),
            )?.id,
        )
    }
}
