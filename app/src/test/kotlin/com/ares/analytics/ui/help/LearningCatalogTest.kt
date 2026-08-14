package com.ares.analytics.ui.help

import com.ares.analytics.ui.components.NavigationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearningCatalogTest {
    @Test
    fun `catalog offers a hardware-free first success and real destinations`() {
        val first = LearningCatalog.lessons.first()
        assertEquals(LearningAction.START_SIMULATOR, first.action)
        assertFalse(first.requiresRobot)
        assertEquals(NavigationTarget.DASHBOARD, first.destination)
        assertTrue(LearningCatalog.lessons.all { it.steps.isNotEmpty() && it.successLooksLike.isNotBlank() })
        assertTrue(LearningCatalog.lessons.all { it.checkpoints.isNotEmpty() })
        assertTrue(first.checkpoints.isNotEmpty())
    }

    @Test
    fun `search understands student tasks and concepts`() {
        assertTrue(LearningCatalog.search("disconnected").any { it.id == "read-connection-state" })
        assertTrue(LearningCatalog.search("redux").any { it.id == "safe-subsystem" })
        assertTrue(LearningCatalog.search("sysid", LearningLevel.STARTER).isEmpty())
    }

    @Test
    fun `contextual help opens the lesson for the active workflow`() {
        assertEquals("bring-in-run", LearningCatalog.lessonFor(NavigationTarget.IMPORT_CENTER)?.id)
        assertEquals("bring-in-run", LearningCatalog.lessonFor(NavigationTarget.RUN_HISTORY)?.id)
        assertEquals("first-routine", LearningCatalog.lessonFor(NavigationTarget.PATH_PLANNER)?.id)
        assertEquals("drivebase-blueprint", LearningCatalog.lessonFor(NavigationTarget.DRIVEBASE_BUILDER)?.id)
        assertEquals("robot-studio-tour", LearningCatalog.lessonFor(NavigationTarget.ROBOT_STUDIO)?.id)
        assertEquals("robot-studio-tour", LearningCatalog.lessonFor(NavigationTarget.PROJECT_IDENTITY)?.id)
        assertEquals("developer-reference", LearningCatalog.lessonFor(NavigationTarget.KDOC_VIEWER)?.id)
        assertEquals(null, LearningCatalog.lessonFor(NavigationTarget.ADMIN))
    }

    @Test
    fun `paths and prerequisites reference stable catalog lessons`() {
        val lessonIds = LearningCatalog.lessons.map { it.id }
        assertEquals(lessonIds.size, lessonIds.toSet().size)
        assertEquals(LearningCatalog.paths.size, LearningCatalog.paths.map { it.id }.toSet().size)
        assertEquals(
            LearningCatalog.lessons.flatMap { it.checkpoints }.size,
            LearningCatalog.lessons.flatMap { it.checkpoints }.map { it.id }.toSet().size,
        )
        assertTrue(LearningCatalog.paths.all { path ->
            path.lessonIds.isNotEmpty() && path.lessonIds.all { it in lessonIds }
        })
        assertTrue(LearningCatalog.lessons.all { lesson -> lesson.prerequisiteLessonIds.all { it in lessonIds } })
    }

    @Test
    fun `first mission starts without a robot and keeps interpretation human confirmed`() {
        val firstMission = LearningCatalog.path("first-mission") ?: error("Missing first mission")
        val firstLesson = LearningCatalog.lesson(firstMission.lessonIds.first()) ?: error("Missing first lesson")
        assertFalse(firstLesson.requiresRobot)
        assertEquals(LearningAction.START_SIMULATOR, firstLesson.action)
        assertTrue(firstLesson.checkpoints.any { it.evidence == LearningCheckpointEvidence.SELF_REPORTED })
        assertTrue(firstLesson.checkpoints.any { it.evidence != LearningCheckpointEvidence.SELF_REPORTED })
    }

    @Test
    fun `every interactive lab has guidance and a lesson`() {
        LearningLab.entries.forEach { lab ->
            val guide = LearningCatalog.labGuide(lab)
            assertTrue(guide.tryThis.isNotEmpty())
            assertTrue(guide.reflectionQuestions.isNotEmpty())
            assertTrue(LearningCatalog.lessons.any { it.lab == lab && it.action == LearningAction.OPEN_LAB })
        }
    }

    @Test
    fun `homing lab teaches freshness stall evidence and neutral recovery`() {
        val lesson = LearningCatalog.lesson("homing-safety-lab") ?: error("Missing homing safety lab")

        assertEquals(LearningLab.HOMING_SAFETY, lesson.lab)
        assertFalse(lesson.requiresRobot)
        assertTrue("current stall" in lesson.keywords)
        assertTrue("neutral recovery" in lesson.keywords)
        assertTrue("safe-subsystem" in lesson.prerequisiteLessonIds)
    }

    @Test
    fun `state flow lab covers controller redux devices and telemetry units`() {
        val lesson = LearningCatalog.lesson("state-flow-lab") ?: error("Missing state flow lab")

        assertEquals(LearningLab.STATE_FLOW, lesson.lab)
        assertFalse(lesson.requiresRobot)
        assertTrue("controller input" in lesson.keywords)
        assertTrue("immutable state" in lesson.keywords)
        assertTrue("telemetry units" in lesson.keywords)
    }

    @Test
    fun `autonomous lab teaches validation before the real routine builder`() {
        val lesson = LearningCatalog.lesson("autonomous-safety-lab") ?: error("Missing autonomous safety lab")
        val path = LearningCatalog.path("autonomous-developer") ?: error("Missing autonomous developer path")

        assertEquals(LearningLab.AUTONOMOUS_SAFETY, lesson.lab)
        assertFalse(lesson.requiresRobot)
        assertTrue("starting pose" in lesson.keywords)
        assertTrue("resources" in lesson.keywords)
        assertTrue("failure behavior" in lesson.keywords)
        assertTrue(path.lessonIds.indexOf("autonomous-safety-lab") < path.lessonIds.indexOf("first-routine"))
        assertTrue("autonomous-safety-lab" in (LearningCatalog.lesson("first-routine")?.prerequisiteLessonIds ?: emptySet()))
    }

    @Test
    fun `robot studio lesson teaches compile only build evidence`() {
        val lesson = LearningCatalog.lesson("robot-studio-tour") ?: error("Missing Robot Studio lesson")

        assertTrue(lesson.steps.any { it.contains("Verify & build") && it.contains("no deployment") })
        assertTrue(lesson.successLooksLike.contains("compile-only"))
        assertTrue(lesson.safetyNote.orEmpty().contains("never installs or deploys"))
    }
}
