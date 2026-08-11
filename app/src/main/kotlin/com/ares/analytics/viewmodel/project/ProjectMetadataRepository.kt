package com.ares.analytics.viewmodel.project

import com.areslib.project.AresProjectMetadataCodec
import com.areslib.project.AresProjectMetadataDocument
import java.io.File

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
}
