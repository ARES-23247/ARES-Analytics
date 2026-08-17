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
    fun `every workflow screen except profile and admin has contextual help`() {
        val intentionallyUnmapped = setOf(NavigationTarget.PROFILE, NavigationTarget.ADMIN)
        NavigationTarget.entries
            .filter { it !in intentionallyUnmapped }
            .forEach { target ->
                assertTrue(
                    LearningCatalog.lessonFor(target) != null,
                    "Missing contextual lesson for ${target.name}",
                )
            }
        assertEquals("understand-offline-sync", LearningCatalog.lessonFor(NavigationTarget.CLOUD)?.id)
        assertEquals("edit-field-documents", LearningCatalog.lessonFor(NavigationTarget.FIELD_EDITOR)?.id)
        assertEquals("read-driver-coaching", LearningCatalog.lessonFor(NavigationTarget.MATCH_STRATEGY)?.id)
        assertEquals("compare-run-evidence", LearningCatalog.lessonFor(NavigationTarget.GUIDED_RUN_ANALYSIS)?.id)
        assertEquals("query-stored-telemetry", LearningCatalog.lessonFor(NavigationTarget.DATABASE_VIEWER)?.id)
        assertEquals("review-hardware-addresses", LearningCatalog.lessonFor(NavigationTarget.HARDWARE_SETUP)?.id)
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
    fun `subsystem mission uses real builder evidence and truthful graduation gates`() {
        val lesson = LearningCatalog.lesson("safe-subsystem") ?: error("Missing subsystem mission")

        assertEquals("Build a homed position mechanism", lesson.title)
        assertTrue(lesson.checkpoints.count { it.evidence != LearningCheckpointEvidence.SELF_REPORTED } >= 6)
        assertTrue(lesson.checkpoints.any { it.action == LearningCheckpointAction.OPEN_HOMING_LAB })
        assertTrue(lesson.checkpoints.any { it.action == LearningCheckpointAction.OPEN_STATE_FLOW_LAB })
        assertTrue(lesson.safetyNote.orEmpty().contains("does not prove"))
        assertTrue(lesson.steps.any { it.startsWith("Predict:") })
        assertTrue(lesson.steps.any { it.startsWith("Graduate:") })
    }

    @Test
    fun `controller mission binds the generated mechanism with project evidence`() {
        val lesson = LearningCatalog.lesson("map-one-control") ?: error("Missing controls mission")

        assertEquals("Control the mechanism you created", lesson.title)
        assertEquals(NavigationTarget.CONTROLS, lesson.destination)
        assertFalse(lesson.requiresRobot)
        assertTrue("safe-subsystem" in lesson.prerequisiteLessonIds)
        assertTrue(lesson.checkpoints.count { it.evidence != LearningCheckpointEvidence.SELF_REPORTED } >= 6)
        assertTrue(lesson.checkpoints.any { it.action == LearningCheckpointAction.OPEN_CONTROLS })
        assertTrue(lesson.steps.any { it.startsWith("Predict:") })
        assertTrue(lesson.steps.any { it.startsWith("Trace:") })
        assertTrue(lesson.safetyNote.orEmpty().contains("does not prove a controller is connected"))
        assertTrue(lesson.successLooksLike.contains("generated successfully"))
    }

    @Test
    fun `tuning mission is offline project backed and separates review from hardware validation`() {
        val lesson = LearningCatalog.lesson("tuning-evidence") ?: error("Missing tuning mission")

        assertEquals("Propose one reversible tuning change", lesson.title)
        assertEquals(NavigationTarget.TUNING, lesson.destination)
        assertFalse(lesson.requiresRobot)
        assertTrue("control-response-lab" in lesson.prerequisiteLessonIds)
        assertTrue("safe-subsystem" in lesson.prerequisiteLessonIds)
        assertTrue(lesson.checkpoints.count { it.evidence != LearningCheckpointEvidence.SELF_REPORTED } >= 6)
        assertTrue(lesson.checkpoints.any { it.action == LearningCheckpointAction.OPEN_TUNING })
        assertTrue(lesson.steps.any { it.startsWith("Predict:") })
        assertTrue(lesson.steps.any { it.contains("feedforward") })
        assertTrue(lesson.steps.any { it.contains("never pushes NT4") })
        assertTrue(lesson.safetyNote.orEmpty().contains("does not prove"))
    }

    @Test
    fun `superstructure mission uses production semantics while preserving the physics boundary`() {
        val lesson = LearningCatalog.lesson("coordinate-mechanisms") ?: error("Missing superstructure mission")

        assertEquals("Coordinate several mechanisms safely", lesson.title)
        assertEquals(NavigationTarget.SUPERSTRUCTURE_STUDIO, lesson.destination)
        assertFalse(lesson.requiresRobot)
        assertEquals(40, lesson.durationMinutes)
        assertTrue(lesson.checkpoints.count { it.evidence != LearningCheckpointEvidence.SELF_REPORTED } >= 8)
        assertTrue(lesson.checkpoints.any { it.action == LearningCheckpointAction.OPEN_SUPERSTRUCTURE_STUDIO })
        assertTrue(lesson.steps.any { it.startsWith("Predict:") })
        assertTrue(lesson.steps.any { it.contains("Inject stale") })
        assertTrue(lesson.safetyNote.orEmpty().contains("not mechanism physics"))
    }

    @Test
    fun `autonomous mission uses canonical project evidence and names every evidence boundary`() {
        val lesson = LearningCatalog.lesson("first-routine") ?: error("Missing autonomous mission")

        assertEquals(NavigationTarget.PATH_PLANNER, lesson.destination)
        assertFalse(lesson.requiresRobot)
        assertTrue(lesson.checkpoints.count { it.evidence != LearningCheckpointEvidence.SELF_REPORTED } >= 7)
        assertTrue(lesson.checkpoints.any { it.action == LearningCheckpointAction.OPEN_AUTONOMOUS })
        assertTrue(lesson.steps.any { it.startsWith("Predict:") })
        assertTrue(lesson.steps.any { it.contains("kinematic preview") })
        assertTrue(lesson.steps.any { it.startsWith("Graduate:") })
        assertTrue(lesson.safetyNote.orEmpty().contains("do not model wheel slip"))
    }

    @Test
    fun `run missions use workspace evidence and keep conclusions student owned`() {
        val importLesson = LearningCatalog.lesson("bring-in-run") ?: error("Missing import mission")
        val analysisLesson = LearningCatalog.lesson("compare-run-evidence") ?: error("Missing analysis mission")

        assertEquals(NavigationTarget.IMPORT_CENTER, importLesson.destination)
        assertTrue(importLesson.checkpoints.count { it.evidence != LearningCheckpointEvidence.SELF_REPORTED } >= 3)
        assertTrue(importLesson.checkpoints.any { it.action == LearningCheckpointAction.OPEN_GUIDED_ANALYSIS })
        assertTrue(importLesson.steps.any { it.startsWith("Predict:") })
        assertTrue(importLesson.steps.any { it.contains("quarantined") })

        assertEquals(NavigationTarget.GUIDED_RUN_ANALYSIS, analysisLesson.destination)
        assertTrue(analysisLesson.checkpoints.count { it.evidence != LearningCheckpointEvidence.SELF_REPORTED } >= 5)
        assertTrue(analysisLesson.checkpoints.any { it.id == RunAnalysisMissionCheckpointIds.CLAIM && it.evidence == LearningCheckpointEvidence.SELF_REPORTED })
        assertTrue(analysisLesson.steps.any { it.contains("possible causes separate") })
        assertTrue(analysisLesson.steps.any { it.contains("Export the Markdown") })
        assertTrue(analysisLesson.safetyNote.orEmpty().contains("cannot prove"))
    }

    @Test
    fun `generated Kotlin graduation uses consumer build simulation and student owned boundaries`() {
        val lesson = LearningCatalog.lesson("generated-kotlin-graduation") ?: error("Missing graduation mission")
        val builderPath = LearningCatalog.path("robot-builder") ?: error("Missing robot builder path")

        assertEquals(NavigationTarget.ROBOT_STUDIO, lesson.destination)
        assertFalse(lesson.requiresRobot)
        assertTrue(setOf("robot-studio-tour", "safe-subsystem", "map-one-control").all { it in lesson.prerequisiteLessonIds })
        assertTrue(lesson.checkpoints.count { it.evidence != LearningCheckpointEvidence.SELF_REPORTED } >= 5)
        assertTrue(lesson.checkpoints.any { it.action == LearningCheckpointAction.OPEN_DEVELOPER_REFERENCE })
        assertTrue(lesson.checkpoints.any { it.action == LearningCheckpointAction.OPEN_GUIDED_ANALYSIS })
        assertTrue(lesson.steps.any { it.contains("GENERATED—DO NOT EDIT") })
        assertTrue(lesson.steps.any { it.contains("Verify & build") })
        assertTrue(lesson.steps.any { it.contains("project simulator") })
        assertTrue(lesson.safetyNote.orEmpty().contains("do not validate wiring"))
        assertTrue("generated-kotlin-graduation" in builderPath.lessonIds)
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
