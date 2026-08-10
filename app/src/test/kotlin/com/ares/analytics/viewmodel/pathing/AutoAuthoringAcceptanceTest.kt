package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.shared.League
import com.ares.analytics.util.ProjectLayout
import com.areslib.auto.AresAutoFileLoader
import com.areslib.auto.AutoDriveStep
import com.areslib.auto.AutoPose
import com.areslib.auto.AutoRoutine
import com.areslib.auto.AutoStep
import com.areslib.pathing.CommandKey
import com.areslib.pathing.TrajectoryPreset
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Exercises the complete offline visual-authoring boundary without launching Compose or a robot. */
class AutoAuthoringAcceptanceTest {
    @Test
    fun `student can discover actions save reload revise and deploy a legal FTC auto`() {
        val project = Files.createTempDirectory("ares-auto-authoring-").toFile()
        try {
            createRobotProject(project)
            val scan = AutoCapabilityScanner().scan(project.path, League.FTC)
            assertTrue(scan.warnings.isEmpty(), scan.warnings.joinToString())
            assertEquals(setOf("intake.stop", "lights.green"), scan.catalog.map { it.key.value }.toSet())

            val robot = RobotDimensions(lengthMeters = 0.45, widthMeters = 0.40)
            val routine = AutoRoutine(
                documentId = "student-score-and-park",
                name = "Student score and park",
                startingPose = clampAutoPose(AutoPose(-1.0, -1.0, 0.0), League.FTC, robot),
                steps = listOf(
                    AutoStep.drive(
                        AutoDriveStep(
                            target = clampAutoPose(AutoPose(0.5, -0.5, 0.0), League.FTC, robot),
                            preset = TrajectoryPreset.SAFE,
                            arrivalCommands = listOf("lights.green")
                        )
                    ),
                    AutoStep.command(CommandKey("intake.stop"))
                )
            )
            assertTrue(validateAutoFieldBounds(routine, League.FTC, robot).isEmpty())
            assertTrue(referencedCommands(routine).all { key -> scan.catalog.any { it.key.value == key } })

            val repository = AresAutoRepository()
            val firstSave = repository.save(project.path, League.FTC, routine)
            assertTrue(firstSave.createdRevision)
            assertEquals(1, firstSave.routine.revision)
            assertEquals(firstSave.routine, repository.load(project.path, League.FTC, routine.documentId))

            val duplicateSave = repository.save(project.path, League.FTC, firstSave.routine)
            assertFalse(duplicateSave.createdRevision, "saving unchanged content must not create history noise")

            val revised = firstSave.routine.copy(
                steps = firstSave.routine.steps + AutoStep.wait(250.milliseconds)
            )
            val secondSave = repository.save(project.path, League.FTC, revised)
            assertEquals(2, secondSave.routine.revision)
            assertEquals(firstSave.contentHash, secondSave.routine.parentContentHash)
            assertEquals(2, repository.listRevisions(project.path, routine.documentId).size)

            val deployed = AresAutoFileLoader.load(
                documentId = routine.documentId,
                directories = listOf(ProjectLayout.aresAutosDirectory(project.path, League.FTC))
            )
            assertEquals(secondSave.routine, deployed)
        } finally {
            project.deleteRecursively()
        }
    }

    private fun createRobotProject(project: File) {
        File(project, "TeamCode/src/main/java/example/AutoActions.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package example
                import com.areslib.pathing.CommandKey
                val intakeStop = CommandKey("intake.stop")
                """.trimIndent()
            )
        }
        File(project, "TeamCode/src/main/assets/ares/auto-capabilities.json").apply {
            parentFile.mkdirs()
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "actions": [
                    {
                      "key": "intake.stop",
                      "displayName": "Stop intake",
                      "description": "Stops the intake roller.",
                      "category": "Intake"
                    },
                    {
                      "key": "lights.green",
                      "displayName": "Lights green",
                      "description": "Shows that the robot is ready.",
                      "category": "Indicators"
                    }
                  ]
                }
                """.trimIndent()
            )
        }
    }

    private fun referencedCommands(routine: AutoRoutine): Set<String> = buildSet {
        fun visit(step: AutoStep) {
            step.commandKey?.let(::add)
            step.drive?.let { drive ->
                addAll(drive.duringCommands)
                addAll(drive.arrivalCommands)
                drive.markers.forEach { marker -> add(marker.commandKey) }
            }
            step.children.forEach(::visit)
        }
        routine.steps.forEach(::visit)
    }
}
