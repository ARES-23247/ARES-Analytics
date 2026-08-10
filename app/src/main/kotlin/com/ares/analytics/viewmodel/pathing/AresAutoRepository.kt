package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.shared.League
import com.ares.analytics.util.ProjectLayout
import com.areslib.auto.AresAutoCodec
import com.areslib.auto.AutoRoutine
import com.areslib.auto.AutoValidationSeverity
import com.areslib.auto.validateAutoRoutine
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class AutoRevisionSummary(
    val revision: Int,
    val contentHash: String,
    val name: String,
    val file: File
)

data class SavedAutoRevision(
    val routine: AutoRoutine,
    val contentHash: String,
    val currentFile: File,
    val historyFile: File,
    val createdRevision: Boolean
)

/**
 * Offline-first storage for native `.aresauto` documents.
 *
 * Current files are deployable project assets. Every explicit content-changing save also writes an
 * immutable checkpoint under `.ares/history`; autosave/drag events never manufacture revisions.
 * Writes use same-directory temporary files followed by atomic replacement when the filesystem
 * supports it, so a crash cannot leave a partially-written auto selected as current.
 */
class AresAutoRepository {
    fun listAutos(projectPath: String, league: League): List<AutoRoutine> {
        val directory = ProjectLayout.aresAutosDirectory(projectPath, league)
        return directory.listFiles { file -> file.isFile && file.extension == AUTO_EXTENSION }
            .orEmpty()
            .mapNotNull(::decodeOrNull)
            .sortedBy { it.name.lowercase() }
    }

    fun load(projectPath: String, league: League, documentId: String): AutoRoutine {
        requireDocumentId(documentId)
        val file = File(ProjectLayout.aresAutosDirectory(projectPath, league), "$documentId.$AUTO_EXTENSION")
        require(file.isFile) { "Auto '$documentId' does not exist" }
        return AresAutoCodec.decode(file.readText())
    }

    fun save(projectPath: String, league: League, draft: AutoRoutine): SavedAutoRevision {
        val validationErrors = validateAutoRoutine(draft).filter {
            it.severity == AutoValidationSeverity.ERROR
        }
        require(validationErrors.isEmpty()) {
            validationErrors.joinToString(separator = "; ") { it.message }
        }
        requireDocumentId(draft.documentId)

        val currentFile = File(
            ProjectLayout.aresAutosDirectory(projectPath, league),
            "${draft.documentId}.$AUTO_EXTENSION"
        )
        val previous = currentFile.takeIf(File::isFile)?.let { AresAutoCodec.decode(it.readText()) }
        val normalized = when {
            previous == null -> draft.copy(revision = 1, parentContentHash = null)
            sameContent(previous, draft) -> previous
            else -> AresAutoCodec.nextRevision(previous, draft)
        }
        val contentHash = AresAutoCodec.contentHash(normalized)
        val historyDirectory = ProjectLayout.aresAutoHistoryDirectory(projectPath, normalized.documentId)
        val historyFile = File(
            historyDirectory,
            "${normalized.revision.toString().padStart(4, '0')}-${contentHash.take(12)}.$AUTO_EXTENSION"
        )
        val createdRevision = !historyFile.exists()

        if (createdRevision) {
            writeAtomically(historyFile, AresAutoCodec.encode(normalized), replaceExisting = false)
        }
        if (previous != normalized || !currentFile.exists()) {
            writeAtomically(currentFile, AresAutoCodec.encode(normalized), replaceExisting = true)
        }
        return SavedAutoRevision(
            routine = normalized,
            contentHash = contentHash,
            currentFile = currentFile,
            historyFile = historyFile,
            createdRevision = createdRevision
        )
    }

    fun listRevisions(projectPath: String, documentId: String): List<AutoRevisionSummary> {
        requireDocumentId(documentId)
        return ProjectLayout.aresAutoHistoryDirectory(projectPath, documentId)
            .listFiles { file -> file.isFile && file.extension == AUTO_EXTENSION }
            .orEmpty()
            .mapNotNull { file ->
                val routine = decodeOrNull(file) ?: return@mapNotNull null
                AutoRevisionSummary(
                    revision = routine.revision,
                    contentHash = AresAutoCodec.contentHash(routine),
                    name = routine.name,
                    file = file
                )
            }
            .sortedWith(compareByDescending<AutoRevisionSummary> { it.revision }.thenByDescending { it.contentHash })
    }

    /** Restores old content as a new revision, preserving a linear auditable history. */
    fun restore(
        projectPath: String,
        league: League,
        documentId: String,
        contentHash: String
    ): SavedAutoRevision {
        requireDocumentId(documentId)
        require(contentHash.matches(Regex("[a-f0-9]{64}"))) { "Invalid revision hash" }
        val revision = listRevisions(projectPath, documentId).firstOrNull { it.contentHash == contentHash }
            ?: error("Revision $contentHash was not found for '$documentId'")
        val historical = AresAutoCodec.decode(revision.file.readText())
        val current = load(projectPath, league, documentId)
        return save(
            projectPath,
            league,
            historical.copy(
                revision = current.revision,
                parentContentHash = current.parentContentHash
            )
        )
    }

    private fun sameContent(previous: AutoRoutine, draft: AutoRoutine): Boolean =
        previous == draft.copy(
            schemaVersion = previous.schemaVersion,
            documentId = previous.documentId,
            revision = previous.revision,
            parentContentHash = previous.parentContentHash
        )

    private fun decodeOrNull(file: File): AutoRoutine? =
        runCatching { AresAutoCodec.decode(file.readText()) }.getOrNull()

    private fun requireDocumentId(documentId: String) {
        require(documentId.matches(Regex("[a-z0-9][a-z0-9._-]{0,63}"))) {
            "Invalid ARES auto document ID"
        }
    }

    private fun writeAtomically(file: File, content: String, replaceExisting: Boolean) {
        file.parentFile.mkdirs()
        val temporary = Files.createTempFile(file.parentFile.toPath(), ".${file.name}.", ".tmp")
        try {
            Files.writeString(temporary, content)
            val options = if (replaceExisting) {
                arrayOf<java.nio.file.CopyOption>(
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } else {
                arrayOf<java.nio.file.CopyOption>(StandardCopyOption.ATOMIC_MOVE)
            }
            try {
                Files.move(temporary, file.toPath(), *options)
            } catch (_: AtomicMoveNotSupportedException) {
                val fallback = if (replaceExisting) {
                    arrayOf<java.nio.file.CopyOption>(StandardCopyOption.REPLACE_EXISTING)
                } else {
                    emptyArray<java.nio.file.CopyOption>()
                }
                Files.move(temporary, file.toPath(), *fallback)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private companion object {
        const val AUTO_EXTENSION = "aresauto"
    }
}
