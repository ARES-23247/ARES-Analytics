package com.ares.analytics.viewmodel.project

import com.areslib.project.AresProjectMetadataCodec
import com.areslib.project.AresProjectMetadataDocument
import java.io.File

data class SavedProjectMetadata(
    val document: AresProjectMetadataDocument,
    val contentHash: String,
    val historyFile: File?,
    val created: Boolean,
)

/** Canonical, Git-tracked robot and field geometry at `.ares/project.json`. */
class ProjectMetadataRepository {
    fun file(projectPath: String): File = resolveProjectPath(projectPath, ".ares/project.json")

    fun load(projectPath: String): Result<AresProjectMetadataDocument> {
        val file = file(projectPath)
        if (!file.isFile) return Result.failure(NoSuchElementException("Project metadata does not exist at ${file.path}"))
        return runCatching { AresProjectMetadataCodec.decode(file.readText()) }
    }

    fun save(projectPath: String, document: AresProjectMetadataDocument): String {
        val encoded = AresProjectMetadataCodec.encode(document)
        AtomicProjectFileWriter.write(file(projectPath), encoded, replaceExisting = true)
        return AresProjectMetadataCodec.contentHash(document)
    }

    /**
     * Saves only after the caller reviewed a proposal based on [expectedContentHash].
     * A corrupt or concurrently changed current file is preserved and causes a visible failure.
     */
    fun saveReviewed(
        projectPath: String,
        expectedContentHash: String?,
        document: AresProjectMetadataDocument,
    ): SavedProjectMetadata {
        val normalized = AresProjectMetadataCodec.decode(AresProjectMetadataCodec.encode(document))
        val target = file(projectPath)
        return ProjectDocumentWriteLocks.withLock(target) {
            val previous = target.takeIf(File::isFile)?.let { current ->
                AresProjectMetadataCodec.decode(current.readText())
            }
            val actualHash = previous?.let(AresProjectMetadataCodec::contentHash)
            require(actualHash == expectedContentHash) {
                "Project identity changed after preview. Reload it, review the new diff, and try again."
            }

            val proposedHash = AresProjectMetadataCodec.contentHash(normalized)
            if (proposedHash == actualHash) {
                return@withLock SavedProjectMetadata(normalized, proposedHash, historyFile = null, created = false)
            }

            val historyFile = previous?.let { old ->
                val oldHash = requireNotNull(actualHash)
                val history = resolveProjectPath(projectPath, ".ares/history/project/$oldHash.json")
                val oldContent = AresProjectMetadataCodec.encode(old)
                when {
                    !history.exists() -> AtomicProjectFileWriter.write(history, oldContent, replaceExisting = false)
                    history.readText() != oldContent -> error(
                        "Project identity history collision at ${history.path}; no files were replaced.",
                    )
                }
                history
            }
            AtomicProjectFileWriter.write(target, AresProjectMetadataCodec.encode(normalized), replaceExisting = previous != null)
            SavedProjectMetadata(normalized, proposedHash, historyFile, created = previous == null)
        }
    }
}
