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
import com.areslib.subsystem.SubsystemMeasurementDocument
import com.areslib.subsystem.SubsystemTemplate
import com.areslib.subsystem.SubsystemSafetyDocument
import com.areslib.subsystem.SubsystemPlatform
import com.areslib.subsystem.SubsystemStateFieldDocument
import com.areslib.subsystem.SubsystemValueType
import com.areslib.codegen.SubsystemStarterPlan
import com.areslib.codegen.SubsystemArtifactGroup
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

    @Test
    fun `capability template selection creates a safe explicit draft`() {
        val root = Files.createTempDirectory("ares-subsystem-template").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)

        viewModel.selectTemplate(SubsystemTemplate.HOMED_MECHANISM)
        viewModel.newSubsystem()

        val state = viewModel.state.value
        assertEquals(SubsystemTemplate.HOMED_MECHANISM, state.draft?.template)
        assertTrue(state.draft?.safety?.requiresHoming == true)
        assertTrue(state.draft?.generateMockIo == true)
        assertTrue(state.draft?.generateTest == true)
        assertFalse(state.generatedPlumbingExpanded)
        assertTrue(state.previewFiles.all { it.description.isNotBlank() })
        assertTrue(state.previewFiles.all { it.moduleName.isNotBlank() && it.projectRelativePath.isNotBlank() })
        assertEquals(
            SubsystemArtifactGroup.entries.toSet(),
            state.previewFiles.mapTo(linkedSetOf()) { it.group },
        )
        viewModel.close()
    }

    @Test
    fun `unsafe opt-outs are visible warnings without hiding structural errors`() {
        val root = Files.createTempDirectory("ares-subsystem-safety-warnings").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)

        viewModel.edit { document ->
            document.copy(
                safety = document.safety.copy(
                    requiresConfigurationHealth = false,
                    latchOutputFaults = false,
                    requiresExplicitNeutralRecovery = false,
                    telemetryEnabled = false,
                    zeroAllocationPeriodic = false,
                )
            )
        }

        val warnings = viewModel.state.value.problems.filter { it.severity == SubsystemProblemSeverity.WARNING }
        assertTrue(warnings.any { it.path == "safety.requiresConfigurationHealth" })
        assertTrue(warnings.any { it.path == "safety.latchOutputFaults" })
        assertTrue(warnings.any { it.path == "safety.requiresExplicitNeutralRecovery" })
        assertTrue(warnings.any { it.path == "safety.telemetryEnabled" })
        assertTrue(warnings.any { it.path == "safety.zeroAllocationPeriodic" })
        viewModel.close()
    }

    @Test
    fun `changed starter requires a structured diff and explicit confirmation`() {
        val root = Files.createTempDirectory("ares-subsystem-starter-diff").toFile()
        val generator = FakeGenerator()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC, projectGenerator = generator)
        val starter = viewModel.state.value.previewFiles.first {
            it.ownership == com.areslib.codegen.SubsystemArtifactOwnership.GENERATED_STARTER
        }
        val existing = root.resolve(starter.projectRelativePath)
        existing.parentFile.mkdirs()
        val customized = starter.content.lines().toMutableList().also {
            it[1] = "// reviewed team customization"
        }.joinToString("\n")
        existing.writeText(customized)

        viewModel.edit { it.copy(description = "Force preview refresh") }
        viewModel.generate()

        val pending = viewModel.state.value.pendingStarterReplacements
        assertTrue(pending.any { it.path == starter.path })
        assertTrue(
            pending.flatMap { it.diff }.any { it.kind == SubsystemDiffLineKind.REMOVED },
            "Expected removed lines in ${pending.map { it.path to it.diff }}",
        )
        assertTrue(
            pending.flatMap { it.diff }.any { it.kind == SubsystemDiffLineKind.ADDED },
            "Expected added lines in ${pending.map { it.path to it.diff }}",
        )
        assertEquals(null, generator.projectPath)

        viewModel.confirmStarterReplacement()
        assertEquals(root.canonicalPath, generator.projectPath)
        viewModel.close()
    }

    @Test
    fun `user-owned source without starter header is protected from replacement`() {
        val root = Files.createTempDirectory("ares-subsystem-user-owned").toFile()
        val generator = FakeGenerator()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC, projectGenerator = generator)
        val starter = viewModel.state.value.previewFiles.first {
            it.ownership == com.areslib.codegen.SubsystemArtifactOwnership.GENERATED_STARTER
        }
        val existing = root.resolve(starter.projectRelativePath)
        existing.parentFile.mkdirs()
        existing.writeText("// ARES OWNERSHIP: USER-OWNED\nclass TeamMechanism\n")

        viewModel.edit { it.copy(description = "Refresh protection plan") }
        viewModel.generate()

        val state = viewModel.state.value
        assertTrue(state.hasProtectedUserOwnedConflict)
        assertTrue(state.pendingStarterReplacements.isEmpty())
        assertEquals(null, generator.projectPath)
        assertTrue(state.status.orEmpty().contains("USER-OWNED"))
        viewModel.close()
    }

    @Test
    fun `structured diff is deterministic and bounds unchanged context`() {
        val diff = structuredLineDiff("a\nb\nold\ny\nz", "a\nb\nnew\ny\nz", contextLines = 1)

        assertEquals(
            listOf(
                SubsystemDiffLine(SubsystemDiffLineKind.CONTEXT, "b"),
                SubsystemDiffLine(SubsystemDiffLineKind.REMOVED, "old"),
                SubsystemDiffLine(SubsystemDiffLineKind.ADDED, "new"),
                SubsystemDiffLine(SubsystemDiffLineKind.CONTEXT, "y"),
            ),
            diff,
        )
    }

    private fun minimalSubsystem(name: String) = com.areslib.subsystem.SubsystemDocument(
        documentId = "indexer",
        name = name,
        platform = SubsystemPlatform.FTC,
        hardware = listOf(
            SubsystemHardwareDocument(
                "beam", "Beam break", SubsystemHardwareKind.DIGITAL_INPUT,
                SubsystemHardwareConnection(hardwareMapName = "beam"),
                measurements = listOf(SubsystemMeasurementDocument("hasPiece", SubsystemMeasurementSource.DIGITAL_STATE)),
            )
        ),
        stateFields = listOf(
            SubsystemStateFieldDocument(
                "hasPiece", "Has piece", SubsystemValueType.BOOLEAN, SubsystemFieldRole.STATUS,
                defaultBoolean = false,
            )
        ),
        template = SubsystemTemplate.SENSOR_ONLY_SUBSYSTEM,
        safety = SubsystemSafetyDocument(
            latchOutputFaults = false,
            requiresExplicitNeutralRecovery = false,
            requiresCurrentMonitoring = false,
        ),
    )

    private class FakeGenerator : AresProjectGenerator {
        override val aresGenerationState: StateFlow<AresGenerationState> = MutableStateFlow(AresGenerationState())
        var projectPath: String? = null
        var league: League? = null
        var replacementToken: String? = null

        override fun generateAresProject(projectPath: String, league: League) {
            this.projectPath = java.io.File(projectPath).canonicalPath
            this.league = league
        }

        override fun previewSubsystemStarters(projectPath: String, league: League) = SubsystemStarterPlan(emptyList(), null)

        override fun applySubsystemStarters(projectPath: String, league: League, confirmationToken: String?) {
            this.projectPath = java.io.File(projectPath).canonicalPath
            this.league = league
            replacementToken = confirmationToken
        }
    }
}
