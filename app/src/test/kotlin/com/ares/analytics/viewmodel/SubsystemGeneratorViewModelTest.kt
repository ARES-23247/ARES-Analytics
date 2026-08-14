package com.ares.analytics.viewmodel

import com.ares.analytics.service.AresGenerationState
import com.ares.analytics.service.AresProjectGenerator
import com.ares.analytics.service.SubsystemDesignAssistant
import com.ares.analytics.service.SubsystemDesignProposal
import com.ares.analytics.shared.League
import com.ares.analytics.viewmodel.project.AresProjectDocuments
import com.ares.analytics.viewmodel.project.CapabilityCatalogProjectRepository
import com.ares.analytics.viewmodel.project.SubsystemProjectRepository
import com.areslib.catalog.CapabilityCatalogDocument
import com.areslib.subsystem.SubsystemFieldRole
import com.areslib.subsystem.SubsystemFollowerTransform
import com.areslib.subsystem.SubsystemHardwareConnection
import com.areslib.subsystem.SubsystemHardwareDocument
import com.areslib.subsystem.SubsystemHardwareKind
import com.areslib.subsystem.SubsystemHomingMethod
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
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubsystemGeneratorViewModelTest {
    @Test
    fun `register existing Kotlin creates protected hand-authored metadata without starter previews`() {
        val root = Files.createTempDirectory("ares-hand-authored-registration").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)

        viewModel.registerHandAuthoredSubsystem()

        val state = viewModel.state.value
        assertEquals(
            com.areslib.subsystem.SubsystemImplementationKind.HAND_AUTHORED,
            state.draft?.document?.implementation?.kind,
        )
        assertEquals(com.areslib.subsystem.SubsystemSourceOwnership.USER_OWNED, state.draft?.document?.implementation?.ownership)
        assertEquals(":TeamCode", state.draft?.document?.implementation?.modulePath)
        assertTrue(state.draft?.document?.implementation?.sourceFiles.orEmpty().all { it.startsWith("TeamCode/src/main/java/") })
        assertTrue(state.previewFiles.isEmpty(), "Hand-authored source must never enter starter replacement preview")
        assertTrue(state.dirty)

        viewModel.close()
    }

    @Test
    fun `hand-authored subsystem can declare reorder and delete tuning metadata without generating source`() {
        val root = Files.createTempDirectory("ares-hand-authored-tuning").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)
        viewModel.registerHandAuthoredSubsystem()

        viewModel.addTuningParameter()
        val first = viewModel.state.value.draft!!.document.tuningParameters.single()
        viewModel.addTuningParameter()
        val second = viewModel.state.value.draft!!.document.tuningParameters.last()
        viewModel.moveTuningParameter(second.uid, -1)

        var state = viewModel.state.value
        assertEquals(SubsystemBuilderStage.PURPOSE, state.activeStage)
        assertEquals(listOf(second.uid, first.uid), state.draft!!.document.tuningParameters.map { it.uid })
        assertTrue(state.previewFiles.isEmpty(), "Tuning metadata must not create hand-authored Kotlin starters")

        viewModel.navigateToProblem("tuningParameters[0].key")
        assertEquals(SubsystemBuilderStage.TUNING, viewModel.state.value.activeStage)
        assertEquals(second.uid, viewModel.state.value.selectedTuningParameterUid)
        viewModel.removeTuningParameter(second.uid)
        state = viewModel.state.value
        assertEquals(listOf(first.uid), state.draft!!.document.tuningParameters.map { it.uid })
        viewModel.close()
    }

    @Test
    fun `guided builder stages advance deterministically and remain directly selectable`() {
        val root = Files.createTempDirectory("ares-subsystem-stages").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)

        assertEquals(SubsystemBuilderStage.PURPOSE, viewModel.state.value.activeStage)
        viewModel.previousStage()
        assertEquals(SubsystemBuilderStage.PURPOSE, viewModel.state.value.activeStage)

        viewModel.nextStage()
        assertEquals(SubsystemBuilderStage.HARDWARE, viewModel.state.value.activeStage)
        viewModel.selectStage(SubsystemBuilderStage.SIMULATION_AND_TESTING)
        assertEquals(SubsystemBuilderStage.SIMULATION_AND_TESTING, viewModel.state.value.activeStage)
        viewModel.nextStage()
        assertEquals(SubsystemBuilderStage.REVIEW, viewModel.state.value.activeStage)
        viewModel.nextStage()
        assertEquals(SubsystemBuilderStage.REVIEW, viewModel.state.value.activeStage)

        viewModel.close()
    }

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
        assertEquals(1, saved.draft?.document?.revision)
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
        val second = repository.save(root.path, original.copy(displayName = "Indexer V2"))

        assertEquals(1, first.document.revision)
        assertEquals(2, second.document.revision)
        assertEquals(2, repository.listRevisions(root.path, original.documentId).size)
        assertEquals("Indexer V2", repository.load(root.path, original.documentId).displayName)
    }

    @Test
    fun `capability template selection creates a safe explicit draft`() {
        val root = Files.createTempDirectory("ares-subsystem-template").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)

        viewModel.selectTemplate(SubsystemTemplate.HOMED_MECHANISM)
        viewModel.newSubsystem()

        val state = viewModel.state.value
        assertEquals(SubsystemTemplate.HOMED_MECHANISM, state.draft?.document?.template)
        assertEquals(SubsystemHomingMethod.DIGITAL_SENSOR, state.draft?.document?.safety?.homing?.method)
        assertTrue(state.draft?.document?.generateMockIo == true)
        assertTrue(state.draft?.document?.generateTest == true)
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
    fun `template picker creates the requested archetype and closes`() {
        val root = Files.createTempDirectory("ares-subsystem-picker").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)

        viewModel.setTemplatePickerVisible(true)
        viewModel.newSubsystem(SubsystemTemplate.SENSOR_ONLY_SUBSYSTEM)

        val state = viewModel.state.value
        assertFalse(state.showTemplatePicker)
        assertEquals(SubsystemTemplate.SENSOR_ONLY_SUBSYSTEM, state.draft?.document?.template)
        assertEquals(state.draft?.document?.hardware?.firstOrNull()?.uid, state.selectedHardwareUid)
        viewModel.close()
    }

    @Test
    fun `sandbox gains update the selected controller and reject nonfinite input`() {
        val root = Files.createTempDirectory("ares-subsystem-gains").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)
        val loopId = viewModel.state.value.draft!!.document.controlLoops.single().loopId

        viewModel.applyControlLoopGains(loopId, 1.2, 0.3, 0.04, 0.5, 2.1, 0.7)

        val loop = viewModel.state.value.draft!!.document.controlLoops.single()
        assertEquals(1.2, loop.kP)
        assertEquals(0.3, loop.kI)
        assertEquals(0.04, loop.kD)
        assertEquals(0.5, loop.feedforward.kS)
        assertEquals(2.1, loop.feedforward.kV)
        assertEquals(0.7, loop.feedforward.kG)
        assertTrue(viewModel.state.value.dirty)
        assertFailsWith<IllegalArgumentException> {
            viewModel.applyControlLoopGains(loopId, Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
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

    @Test
    fun `adding a motor scaffolds natural cached state and undo restores the prior document`() {
        val root = Files.createTempDirectory("ares-subsystem-natural-state").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)
        val before = viewModel.state.value.draft?.document

        viewModel.addHardware(SubsystemHardwareKind.MOTOR)

        val edited = viewModel.state.value
        val fields = edited.draft?.document?.stateFields.orEmpty()
        assertTrue(fields.any { it.fieldId.endsWith("Position") })
        assertTrue(fields.any { it.fieldId.endsWith("Velocity") })
        assertTrue(fields.any { it.fieldId.endsWith("CurrentAmps") })
        assertTrue(fields.any { it.role == SubsystemFieldRole.TARGET })
        assertTrue(edited.canUndo)

        viewModel.undo()
        assertEquals(before, viewModel.state.value.draft?.document)
        assertTrue(viewModel.state.value.canRedo)
        viewModel.close()
    }

    @Test
    fun `invalid AI proposal remains review only and cannot be applied`() {
        val root = Files.createTempDirectory("ares-subsystem-ai-invalid-").toFile()
        File(root, ".ares/subsystems").mkdirs()
        val assistant = SubsystemDesignAssistant { current, _ ->
            SubsystemDesignProposal(
                summary = "Unsafe incomplete proposal",
                explanations = emptyList(),
                candidate = current.copy(displayName = ""),
            )
        }
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC, designAssistant = assistant)
        val before = viewModel.state.value.draft!!.document

        viewModel.requestAiProposal("Make a mechanism")
        waitFor { !viewModel.state.value.aiProposalInProgress }

        val review = viewModel.state.value.aiProposal!!
        assertFalse(review.canApply)
        assertTrue(review.problems.any { it.severity == SubsystemProblemSeverity.ERROR })
        viewModel.applyAiProposal()
        assertEquals(before, viewModel.state.value.draft!!.document)
        assertTrue(viewModel.state.value.aiProposalError!!.contains("validation", ignoreCase = true))
        viewModel.close()
    }

    @Test
    fun `stall homing selection creates bounded current evidence and navigates safety errors`() {
        val root = Files.createTempDirectory("ares-subsystem-stall-homing").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)
        viewModel.addHardware(SubsystemHardwareKind.MOTOR)

        viewModel.setHomingMethod(SubsystemHomingMethod.CURRENT_STALL)

        val homing = viewModel.state.value.draft?.document?.safety?.homing
        assertEquals(SubsystemHomingMethod.CURRENT_STALL, homing?.method)
        assertEquals(-2.0, homing?.searchOutput)
        assertEquals(250L, homing?.dwellMs)
        assertEquals(3_000L, homing?.timeoutMs)
        assertTrue(homing?.evidence.orEmpty().any { it.fieldId.endsWith("currentAmps", ignoreCase = true) })
        assertTrue(viewModel.state.value.draft?.document?.safety?.requiresCurrentMonitoring == true)

        viewModel.navigateToProblem("safety.homing.evidence")
        assertEquals(SubsystemBuilderStage.SAFETY, viewModel.state.value.activeStage)
        viewModel.close()
    }

    @Test
    fun `hardware reversal and follower direction remain separate and survive leader rename`() {
        val root = Files.createTempDirectory("ares-subsystem-follower-direction").toFile()
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC)
        while (viewModel.state.value.draft!!.document.hardware.count { it.kind == SubsystemHardwareKind.MOTOR } < 2) {
            viewModel.addHardware(SubsystemHardwareKind.MOTOR)
        }
        val motors = viewModel.state.value.draft!!.document.hardware.filter { it.kind == SubsystemHardwareKind.MOTOR }
        val leader = motors.first()
        val follower = motors.last()

        viewModel.updateHardware(follower.hardwareId) { it.copy(inverted = true) }
        viewModel.setHardwareFollower(follower.hardwareId, leader.hardwareId, SubsystemFollowerTransform.INVERTED)

        var document = viewModel.state.value.draft!!.document
        assertTrue(document.hardware.single { it.uid == follower.uid }.inverted)
        assertEquals(
            SubsystemFollowerTransform.INVERTED,
            document.hardware.single { it.uid == follower.uid }.following?.transform,
        )
        assertTrue(document.controlLoops.none { it.actuatorId == follower.hardwareId })

        viewModel.renameHardwareId(leader.hardwareId, "primaryMotor")
        document = viewModel.state.value.draft!!.document
        assertEquals("primaryMotor", document.hardware.single { it.uid == follower.uid }.following?.leaderId)
        viewModel.close()
    }

    @Test
    fun `AI proposal is review only preserves ownership and applies as one undoable form edit`() {
        val root = Files.createTempDirectory("ares-subsystem-ai-proposal").toFile()
        lateinit var requestedBase: com.areslib.subsystem.SubsystemDocument
        val assistant = SubsystemDesignAssistant { current, request ->
            requestedBase = current
            assertEquals("Add a safe reversed follower motor", request)
            SubsystemDesignProposal(
                summary = "Add a clearly named mechanism proposal.",
                explanations = listOf("The form remains locally validated."),
                candidate = current.copy(
                    displayName = "AI Proposed Mechanism",
                    uid = "untrusted-replacement",
                    implementation = current.implementation.copy(
                        ownership = com.areslib.subsystem.SubsystemSourceOwnership.USER_OWNED,
                    ),
                    tuningParameters = current.tuningParameters.map {
                        it.copy(uid = "untrusted.parameter", key = "untrusted.parameter", componentUid = "untrusted.owner")
                    },
                ),
            )
        }
        val viewModel = SubsystemGeneratorViewModel(root.path, League.FTC, designAssistant = assistant)
        viewModel.addTuningParameter()
        val before = viewModel.state.value.draft!!.document

        viewModel.requestAiProposal("Add a safe reversed follower motor")
        waitFor { !viewModel.state.value.aiProposalInProgress }

        val review = viewModel.state.value.aiProposal
        assertTrue(review != null)
        assertTrue(review!!.canApply)
        assertEquals(before.uid, review.proposal.candidate.uid)
        assertEquals(before.implementation, review.proposal.candidate.implementation)
        assertEquals(before.tuningParameters.single().uid, review.proposal.candidate.tuningParameters.single().uid)
        assertEquals(before.tuningParameters.single().key, review.proposal.candidate.tuningParameters.single().key)
        assertEquals(before.tuningParameters.single().componentUid, review.proposal.candidate.tuningParameters.single().componentUid)
        assertTrue(review.diff.any { it.kind == SubsystemDiffLineKind.ADDED })
        assertEquals(before, requestedBase)
        assertEquals(before, viewModel.state.value.draft!!.document, "Review must not mutate the form")

        viewModel.applyAiProposal()
        assertEquals("AI Proposed Mechanism", viewModel.state.value.draft!!.document.displayName)
        assertTrue(viewModel.state.value.dirty)
        assertTrue(viewModel.state.value.canUndo)
        viewModel.undo()
        assertEquals(before, viewModel.state.value.draft!!.document)
        viewModel.close()
    }

    private fun minimalSubsystem(kotlinTypeName: String) = com.areslib.subsystem.SubsystemDocument(
        documentId = "indexer",
        displayName = kotlinTypeName.replace(Regex("(?<=[a-z])(?=[A-Z])"), " "),
        kotlinTypeName = kotlinTypeName,
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

    private fun waitFor(timeoutMs: Long = 3_000L, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for asynchronous view-model work" }
            Thread.sleep(10L)
        }
    }
}
