package com.ares.analytics.viewmodel.project

import com.ares.analytics.shared.League
import com.ares.analytics.shared.WorkspaceConfig
import com.areslib.project.AresCoordinateConvention
import com.areslib.project.AresLeague
import com.areslib.project.AresProjectMetadataCodec
import com.areslib.project.AresProjectMetadataDocument
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectIdentityViewModelTest {
    @Test
    fun `new project suggests stable identity but requires measured robot dimensions`() = runTest {
        withProject { project ->
            val viewModel = ProjectIdentityViewModel(
                scope = this,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )

            viewModel.load(workspace(project))
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("team23247-robot-one-decode", state.draft.projectId)
            assertEquals("", state.draft.robotLengthMeters)
            assertEquals("", state.draft.robotWidthMeters)
            assertEquals("3.6576", state.draft.fieldLengthMeters)
            assertEquals("3.6576", state.draft.fieldWidthMeters)
            assertFalse(state.canReview)
            assertTrue(state.message.orEmpty().contains("No canonical project identity"))

            viewModel.review()
            assertTrue(viewModel.state.value.messageIsError)
            assertTrue(viewModel.state.value.message.orEmpty().contains("Fix every project identity error"))
        }
    }

    @Test
    fun `review is read only and apply creates exactly the previewed project identity`() = runTest {
        withProject { project ->
            val repository = ProjectMetadataRepository()
            val viewModel = ProjectIdentityViewModel(
                scope = this,
                repository = repository,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            viewModel.load(workspace(project))
            advanceUntilIdle()
            viewModel.update(ProjectIdentityField.ROBOT_LENGTH, "0.45")
            viewModel.update(ProjectIdentityField.ROBOT_WIDTH, "0.43")

            viewModel.review()

            val proposal = assertNotNull(viewModel.state.value.proposal)
            assertFalse(repository.file(project.path).exists())
            assertTrue(proposal.changes.any { it.label == "Robot length (m)" })

            viewModel.applyReviewed()
            advanceUntilIdle()

            val saved = repository.load(project.path).getOrThrow()
            assertEquals(proposal.proposedContentHash, AresProjectMetadataCodec.contentHash(saved))
            assertNull(viewModel.state.value.proposal)
            assertTrue(viewModel.state.value.message.orEmpty().contains("Created .ares/project.json"))
        }
    }

    @Test
    fun `editing after preview invalidates the proposal and cannot write stale values`() = runTest {
        withProject { project ->
            val repository = ProjectMetadataRepository()
            val viewModel = ProjectIdentityViewModel(
                scope = this,
                repository = repository,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            viewModel.load(workspace(project))
            advanceUntilIdle()
            viewModel.update(ProjectIdentityField.ROBOT_LENGTH, "0.45")
            viewModel.update(ProjectIdentityField.ROBOT_WIDTH, "0.43")
            viewModel.review()
            assertNotNull(viewModel.state.value.proposal)

            viewModel.update(ProjectIdentityField.ROBOT_LENGTH, "0.46")
            viewModel.applyReviewed()
            advanceUntilIdle()

            assertNull(viewModel.state.value.proposal)
            assertFalse(repository.file(project.path).exists())
        }
    }

    @Test
    fun `saved project id is locked and league mismatch is protected`() = runTest {
        withProject { project ->
            val repository = ProjectMetadataRepository()
            repository.save(project.path, metadata(AresLeague.FTC))
            val dispatcher = StandardTestDispatcher(testScheduler)
            val viewModel = ProjectIdentityViewModel(this, repository, dispatcher)
            viewModel.load(workspace(project, League.FTC))
            advanceUntilIdle()

            viewModel.update(ProjectIdentityField.PROJECT_ID, "renamed-project")
            assertEquals("test-project", viewModel.state.value.draft.projectId)
            assertTrue(viewModel.state.value.message.orEmpty().contains("stable"))

            viewModel.load(workspace(project, League.FRC))
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.protectedError)
            assertFalse(viewModel.state.value.canReview)
        }
    }

    @Test
    fun `corrupt current project identity is visible and never replaced`() = runTest {
        withProject { project ->
            val file = File(project, ".ares/project.json").apply {
                parentFile.mkdirs()
                writeText("student recovery evidence")
            }
            val viewModel = ProjectIdentityViewModel(
                scope = this,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )

            viewModel.load(workspace(project))
            advanceUntilIdle()

            assertNotNull(viewModel.state.value.protectedError)
            assertFalse(viewModel.state.value.canReview)
            assertEquals("student recovery evidence", file.readText())
        }
    }

    @Test
    fun `empty object reports missing fields without leaking a Kotlin null error`() {
        val error = runCatching { decodeProjectMetadata("{}") }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error.message.orEmpty().contains("missing required fields"))
        assertTrue(error.message.orEmpty().contains("projectId"))
        assertFalse(error.message.orEmpty().contains("Regex.matches"))
        assertFalse(error.message.orEmpty().contains("non-null is null"))
    }

    @Test
    fun `reviewed repair preserves invalid bytes before replacing canonical identity`() = runTest {
        withProject { project ->
            val invalidBytes = byteArrayOf(0x7b, 0x7d) // Exact "{}" recovery evidence.
            val file = File(project, ".ares/project.json").apply {
                parentFile.mkdirs()
                writeBytes(invalidBytes)
            }
            val viewModel = ProjectIdentityViewModel(
                scope = this,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            val config = workspace(project).copy(robotLengthMeters = 0.45, robotWidthMeters = 0.43)

            viewModel.load(config)
            advanceUntilIdle()

            val loaded = viewModel.state.value
            assertNotNull(loaded.protectedContentHash)
            assertTrue(loaded.protectedError.orEmpty().contains("missing required fields"))
            assertNull(loaded.message, "An invalid file must not also report a successful load")
            assertTrue(loaded.canReview)
            assertTrue(file.readBytes().contentEquals(invalidBytes))

            viewModel.review()
            val proposal = assertNotNull(viewModel.state.value.proposal)
            assertEquals(loaded.protectedContentHash, proposal.expectedInvalidRawContentHash)
            assertTrue(file.readBytes().contentEquals(invalidBytes), "Preview must be read-only")

            viewModel.applyReviewed()
            advanceUntilIdle()

            val repaired = AresProjectMetadataCodec.decode(file.readText())
            assertEquals("team23247-robot-one-decode", repaired.projectId)
            assertEquals(0.45, repaired.robotLengthMeters)
            assertTrue(viewModel.state.value.message.orEmpty().contains("Repaired .ares/project.json"))
            val recovery = File(project, ".ares/recovery/project")
                .listFiles()
                .orEmpty()
                .single()
            assertTrue(recovery.readBytes().contentEquals(invalidBytes))
        }
    }

    @Test
    fun `reviewed repair aborts when invalid file changes after preview`() = runTest {
        withProject { project ->
            val file = File(project, ".ares/project.json").apply {
                parentFile.mkdirs()
                writeText("{}")
            }
            val viewModel = ProjectIdentityViewModel(
                scope = this,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
            val config = workspace(project).copy(robotLengthMeters = 0.45, robotWidthMeters = 0.43)
            viewModel.load(config)
            advanceUntilIdle()
            viewModel.review()
            assertNotNull(viewModel.state.value.proposal)

            file.writeText("{\"changed\":true}")
            viewModel.applyReviewed()
            advanceUntilIdle()

            assertEquals("{\"changed\":true}", file.readText())
            assertTrue(viewModel.state.value.message.orEmpty().contains("changed after preview"))
            assertFalse(File(project, ".ares/recovery/project").exists())
        }
    }

    @Test
    fun `validation rejects nonpositive geometry and robots larger than the field`() {
        val invalidNumber = validateProjectIdentityDraft(
            League.FTC,
            ProjectIdentityDraft("test-project", "0", "0.4", "3.6576", "3.6576"),
        )
        val outsideField = validateProjectIdentityDraft(
            League.FTC,
            ProjectIdentityDraft("test-project", "4.0", "0.4", "3.6576", "3.6576"),
        )

        assertNotNull(invalidNumber.fieldErrors[ProjectIdentityField.ROBOT_LENGTH])
        assertNull(invalidNumber.document)
        assertTrue(outsideField.generalErrors.isNotEmpty())
        assertNull(outsideField.document)
    }

    private fun workspace(project: File, league: League = League.FTC) = WorkspaceConfig(
        id = "workspace-one",
        teamId = "23247",
        seasonId = "decode",
        robotId = "robot-one",
        projectPath = project.path,
        league = league,
    )

    private fun metadata(league: AresLeague) = AresProjectMetadataDocument(
        projectId = "test-project",
        league = league,
        coordinateConvention = if (league == AresLeague.FTC) {
            AresCoordinateConvention.CENTER_ORIGIN_CCW
        } else {
            AresCoordinateConvention.BLUE_CORNER_ORIGIN_CCW
        },
        robotLengthMeters = .45,
        robotWidthMeters = .43,
        fieldLengthMeters = if (league == AresLeague.FTC) 3.6576 else 16.541,
        fieldWidthMeters = if (league == AresLeague.FTC) 3.6576 else 8.211,
    )

    private suspend fun TestScope.withProject(block: suspend TestScope.(File) -> Unit) {
        val project = Files.createTempDirectory("ares-project-identity-").toFile()
        try {
            block(project)
        } finally {
            project.deleteRecursively()
        }
    }
}
