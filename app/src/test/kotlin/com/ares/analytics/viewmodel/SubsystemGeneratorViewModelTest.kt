package com.ares.analytics.viewmodel

import com.ares.analytics.service.AresGenerationState
import com.ares.analytics.service.AresProjectGenerator
import com.ares.analytics.shared.League
import com.ares.analytics.viewmodel.project.AresProjectDocuments
import com.ares.analytics.viewmodel.project.CapabilityCatalogProjectRepository
import com.ares.analytics.viewmodel.project.SubsystemProjectRepository
import com.areslib.catalog.CapabilityCatalogDocument
import com.areslib.subsystem.SubsystemFieldRole
import com.areslib.subsystem.SubsystemHardwareConnection
import com.areslib.subsystem.SubsystemHardwareDocument
import com.areslib.subsystem.SubsystemHardwareKind
import com.areslib.subsystem.SubsystemMeasurementSource
import com.areslib.subsystem.SubsystemPlatform
import com.areslib.subsystem.SubsystemStateFieldDocument
import com.areslib.subsystem.SubsystemValueType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubsystemGeneratorViewModelTest {
    @Test
    fun `new project previews DSL saves revision and invokes offline generation`() {
        val root = Files.createTempDirectory("ares-subsystem-editor").toFile()
        val generator = FakeGenerator()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC, projectGenerator = generator)

        val initial = viewModel.state.value
        assertTrue(initial.dirty)
        assertTrue(initial.previewFiles.any { it.content.contains("val document = subsystem(") })
        assertTrue(initial.canSave)

        viewModel.generate()

        val saved = viewModel.state.value
        assertFalse(saved.dirty)
        assertEquals(1, saved.draft?.revision)
        assertEquals(root.canonicalPath, generator.projectPath)
        assertEquals(League.FTC, generator.league)
        assertTrue(root.resolve(".ares/subsystems/new-subsystem.aressubsystem").isFile)

        CapabilityCatalogProjectRepository().save(
            root.path,
            CapabilityCatalogDocument(projectId = "test-project"),
        )
        val mergedActions = AresProjectDocuments().load(root.path).capabilityCatalog?.actions.orEmpty()
        assertTrue(mergedActions.any { it.key == "subsystem.new-subsystem.set.target" })
        viewModel.close()
    }

    @Test
    fun `repository creates immutable revisions for subsystem DSL documents`() {
        val root = Files.createTempDirectory("ares-subsystem-revisions").toFile()
        val repository = SubsystemProjectRepository()
        val original = minimalSubsystem("Indexer")
        val first = repository.save(root.path, original)
        val second = repository.save(root.path, original.copy(name = "IndexerV2"))

        assertEquals(1, first.document.revision)
        assertEquals(2, second.document.revision)
        assertEquals(2, repository.listRevisions(root.path, original.documentId).size)
        assertEquals("IndexerV2", repository.load(root.path, original.documentId).name)
    }

    private fun minimalSubsystem(name: String) = com.areslib.subsystem.SubsystemDocument(
        documentId = "indexer",
        name = name,
        platform = SubsystemPlatform.FTC,
        hardware = listOf(
            SubsystemHardwareDocument(
                "beam", "Beam break", SubsystemHardwareKind.DIGITAL_INPUT,
                SubsystemHardwareConnection(hardwareMapName = "beam"),
                measurementFieldId = "hasPiece",
                measurementSource = SubsystemMeasurementSource.DIGITAL_STATE,
            )
        ),
        stateFields = listOf(
            SubsystemStateFieldDocument(
                "hasPiece", "Has piece", SubsystemValueType.BOOLEAN, SubsystemFieldRole.STATUS,
                defaultBoolean = false,
            )
        ),
    )

    private class FakeGenerator : AresProjectGenerator {
        override val aresGenerationState: StateFlow<AresGenerationState> = MutableStateFlow(AresGenerationState())
        var projectPath: String? = null
        var league: League? = null

        override fun generateAresProject(projectPath: String, league: League) {
            this.projectPath = java.io.File(projectPath).canonicalPath
            this.league = league
        }
    }
}
