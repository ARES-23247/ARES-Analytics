package com.ares.analytics.service

import com.ares.analytics.ui.help.AcademyRuntimeSnapshot
import com.ares.analytics.ui.help.FirstMissionCheckpointIds
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LearningProgressServiceTest {
    @Test
    fun `practice progress survives service recreation`() = runTest {
        val tempDir = Files.createTempDirectory("learning-progress-test").toFile()
        val file = File(tempDir, "learning-progress.json")
        LearningProgressService(file).setPracticed("start-simulator", true)

        val reloaded = LearningProgressService(file).progress.value
        assertEquals(setOf("start-simulator"), reloaded.practicedLessonIds)
        assertEquals(setOf("start-simulator"), reloaded.startedLessonIds)
        assertEquals("start-simulator", reloaded.activeLessonId)
        assertEquals(CURRENT_LEARNING_CONTENT_VERSION, reloaded.contentVersion)
    }

    @Test
    fun `corrupt progress fails to an empty safe state`() {
        val tempDir = Files.createTempDirectory("learning-progress-corrupt-test").toFile()
        val file = File(tempDir, "learning-progress.json").apply { writeText("not-json") }
        assertTrue(LearningProgressService(file).progress.value.practicedLessonIds.isEmpty())
    }

    @Test
    fun `version one practice migrates without inventing checkpoint evidence`() {
        val tempDir = Files.createTempDirectory("learning-progress-v1-test").toFile()
        val file = File(tempDir, "learning-progress.json").apply {
            writeText("""{"contentVersion":1,"practicedLessonIds":["start-simulator"]}""")
        }

        val migrated = LearningProgressService(file).progress.value

        assertEquals(CURRENT_LEARNING_CONTENT_VERSION, migrated.contentVersion)
        assertEquals(setOf("start-simulator"), migrated.practicedLessonIds)
        assertEquals(setOf("start-simulator"), migrated.startedLessonIds)
        assertTrue(migrated.completedCheckpointIds.isEmpty())
    }

    @Test
    fun `checkpoint and active lesson progress survive recreation`() = runTest {
        val tempDir = Files.createTempDirectory("learning-checkpoint-test").toFile()
        val file = File(tempDir, "learning-progress.json")
        val service = LearningProgressService(file)
        service.startLesson("safe-subsystem")
        service.setCheckpointCompleted("safe-subsystem.flow", true)

        val reloaded = LearningProgressService(file).progress.value
        assertEquals("safe-subsystem", reloaded.activeLessonId)
        assertTrue("safe-subsystem" in reloaded.startedLessonIds)
        assertTrue("safe-subsystem.flow" in reloaded.completedCheckpointIds)

        val resumed = LearningProgressService(file)
        resumed.clearActiveLesson()
        assertEquals(null, LearningProgressService(file).progress.value.activeLessonId)
        assertTrue("safe-subsystem.flow" in LearningProgressService(file).progress.value.completedCheckpointIds)
    }

    @Test
    fun `runtime observations record simulator facts but not student reflection`() = runTest {
        val tempDir = Files.createTempDirectory("learning-runtime-test").toFile()
        val service = LearningProgressService(File(tempDir, "learning-progress.json"))
        service.observeRuntime(
            AcademyRuntimeSnapshot(
                isAvailable = true,
                isLocalSimulatorSelected = true,
                isSimulatorRunning = true,
                isLocalSimulatorOnline = true,
                isNt4Connected = true,
            ),
        )

        val running = service.progress.value
        assertTrue(FirstMissionCheckpointIds.LOCAL_SIM_SELECTED in running.completedCheckpointIds)
        assertTrue(FirstMissionCheckpointIds.SIMULATOR_RUNNING in running.completedCheckpointIds)
        assertTrue(FirstMissionCheckpointIds.LOCAL_SIM_CONNECTED in running.completedCheckpointIds)
        assertTrue(FirstMissionCheckpointIds.IDENTIFIED_DATA_SOURCE !in running.completedCheckpointIds)

        service.observeRuntime(AcademyRuntimeSnapshot(isAvailable = true, isLocalSimulatorSelected = true))
        assertTrue(FirstMissionCheckpointIds.SIMULATOR_STOPPED in service.progress.value.completedCheckpointIds)
    }

    @Test
    fun `unavailable runtime never changes progress`() = runTest {
        val tempDir = Files.createTempDirectory("learning-runtime-unavailable-test").toFile()
        val service = LearningProgressService(File(tempDir, "learning-progress.json"))
        service.observeRuntime(AcademyRuntimeSnapshot.Unavailable)
        assertEquals(LearningProgress(), service.progress.value)
    }

    @Test
    fun `observable checkpoints cannot be manually asserted`() = runTest {
        val tempDir = Files.createTempDirectory("learning-runtime-manual-test").toFile()
        val service = LearningProgressService(File(tempDir, "learning-progress.json"))
        assertFailsWith<IllegalArgumentException> {
            service.setCheckpointCompleted(FirstMissionCheckpointIds.LOCAL_SIM_CONNECTED, true)
        }
        assertTrue(service.progress.value.completedCheckpointIds.isEmpty())
    }
}
