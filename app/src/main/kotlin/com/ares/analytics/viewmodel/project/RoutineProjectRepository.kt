package com.ares.analytics.viewmodel.project

import com.ares.analytics.shared.League
import com.ares.analytics.util.ProjectLayout
import com.areslib.routine.AresRoutineCodec
import com.areslib.routine.AutonomousRoutineEntryPoint
import com.areslib.routine.DecodedRoutine
import com.areslib.routine.RoutineDocument
import java.io.File

data class ImportedLegacyRoutine(
    val saved: SavedProjectRevision<RoutineDocument>,
    val autonomousEntryPoint: AutonomousRoutineEntryPoint?,
    val migratedFrom: String?
)

/** Offline repository for trigger-neutral `.aresroutine` documents under `.ares/routines`. */
class RoutineProjectRepository : VersionedProjectDocumentStore<RoutineDocument>(
    kind = ProjectDocumentKind.ROUTINE,
    directoryName = "routines",
    historyName = "routines",
    extension = "aresroutine"
) {
    override fun encode(document: RoutineDocument): String = AresRoutineCodec.encode(document)
    override fun decode(json: String): RoutineDocument = AresRoutineCodec.decode(json)
    override fun contentHash(document: RoutineDocument): String = AresRoutineCodec.contentHash(document)
    override fun documentId(document: RoutineDocument): String = document.documentId
    override fun revision(document: RoutineDocument): Int = document.revision
    override fun displayName(document: RoutineDocument): String = document.name
    override fun withRevision(document: RoutineDocument, revision: Int, parentHash: String?): RoutineDocument =
        document.copy(revision = revision, parentContentHash = parentHash)

    override fun sameContent(previous: RoutineDocument, draft: RoutineDocument): Boolean = previous == draft.copy(
        schemaVersion = previous.schemaVersion,
        documentId = previous.documentId,
        revision = previous.revision,
        parentContentHash = previous.parentContentHash
    )

    /**
     * Imports a legacy `.aresauto` in memory, then persists only the neutral routine format.
     * The returned autonomous entry point lets the caller add start metadata to the autonomous
     * catalog instead of accidentally baking it into a reusable macro.
     */
    fun importLegacyAuto(projectPath: String, legacyFile: File): ImportedLegacyRoutine {
        require(legacyFile.isFile && legacyFile.extension.equals("aresauto", ignoreCase = true)) {
            "Choose an existing .aresauto file"
        }
        val decoded = AresRoutineCodec.decodeOrMigrateLegacyAuto(legacyFile.readText())
        require(decoded.migratedFrom != null) { "${legacyFile.name} is not a legacy ARES auto" }
        return ImportedLegacyRoutine(
            saved = save(projectPath, decoded.document),
            autonomousEntryPoint = decoded.autonomousEntryPoint,
            migratedFrom = decoded.migratedFrom
        )
    }

    /** Decodes a native routine or migrates legacy JSON without mutating the project. */
    fun decodeForImport(file: File): DecodedRoutine {
        require(file.isFile) { "Routine import file does not exist" }
        return AresRoutineCodec.decodeOrMigrateLegacyAuto(file.readText())
    }

    /** Deterministically finds deploy-era legacy autos so the GUI can offer one-click migration. */
    fun listLegacyAutos(projectPath: String, league: League): ProjectDocumentListing<File> {
        val directory = ProjectLayout.aresAutosDirectory(projectPath, league)
        if (!directory.isDirectory) return ProjectDocumentListing(emptyList(), emptyList())
        val valid = mutableListOf<File>()
        val diagnostics = mutableListOf<ProjectDocumentDiagnostic>()
        directory.listFiles { file -> file.isFile && file.extension.equals("aresauto", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
            .forEach { file ->
                runCatching { AresRoutineCodec.decodeOrMigrateLegacyAuto(file.readText()) }
                    .onSuccess { decoded ->
                        if (decoded.migratedFrom == null) {
                            diagnostics += ProjectDocumentDiagnostic(
                                ProjectDocumentKind.LEGACY_AUTO,
                                file,
                                "File uses a native routine payload but has the legacy .aresauto extension"
                            )
                        } else {
                            valid += file
                        }
                    }
                    .onFailure { error ->
                        diagnostics += ProjectDocumentDiagnostic(
                            ProjectDocumentKind.LEGACY_AUTO,
                            file,
                            error.message ?: "Legacy auto could not be decoded"
                        )
                    }
            }
        return ProjectDocumentListing(valid, diagnostics)
    }
}
