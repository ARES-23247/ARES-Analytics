package com.ares.analytics.viewmodel.project

import com.areslib.auto.AresAutoCodec
import com.areslib.auto.AutoPose
import com.areslib.auto.AutoRoutine
import com.areslib.auto.AutoStep
import com.areslib.pathing.CommandKey
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LegacyRoutineMigrationTest {
    @Test
    fun `legacy auto becomes neutral routine and separate autonomous metadata`() {
        val project = Files.createTempDirectory("ares-legacy-migration-").toFile()
        try {
            val legacy = AutoRoutine(
                documentId = "old-score",
                name = "Old score",
                startingPose = AutoPose(1.0, 2.0, 0.3),
                steps = listOf(AutoStep.command(CommandKey("intake.stop")))
            )
            val source = File(project, "old-score.aresauto").apply {
                writeText(AresAutoCodec.encode(legacy))
            }

            val imported = RoutineProjectRepository().importLegacyAuto(project.path, source)

            assertEquals("aresauto-v1", imported.migratedFrom)
            assertEquals("old-score", imported.saved.document.documentId)
            assertEquals("intake.stop", imported.saved.document.steps.single().actionKey)
            val entry = assertNotNull(imported.autonomousEntryPoint)
            assertEquals("old-score", entry.routineId)
            assertEquals(1.0, entry.startingPose.xMeters)
            assertTrue(File(project, ".ares/routines/old-score.aresroutine").isFile)
            assertTrue(source.isFile, "migration must not delete the student's source file")
        } finally {
            project.deleteRecursively()
        }
    }
}
