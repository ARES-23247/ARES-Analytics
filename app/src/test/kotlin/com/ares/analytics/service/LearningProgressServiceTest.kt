package com.ares.analytics.service

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LearningProgressServiceTest {
    @Test
    fun `practice progress survives service recreation`() = runTest {
        val tempDir = Files.createTempDirectory("learning-progress-test").toFile()
        val file = File(tempDir, "learning-progress.json")
        LearningProgressService(file).setPracticed("start-simulator", true)

        val reloaded = LearningProgressService(file).progress.value
        assertEquals(setOf("start-simulator"), reloaded.practicedLessonIds)
        assertEquals(CURRENT_LEARNING_CONTENT_VERSION, reloaded.contentVersion)
    }

    @Test
    fun `corrupt progress fails to an empty safe state`() {
        val tempDir = Files.createTempDirectory("learning-progress-corrupt-test").toFile()
        val file = File(tempDir, "learning-progress.json").apply { writeText("not-json") }
        assertTrue(LearningProgressService(file).progress.value.practicedLessonIds.isEmpty())
    }
}
