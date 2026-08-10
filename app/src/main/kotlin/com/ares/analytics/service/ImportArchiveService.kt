package com.ares.analytics.service

import com.ares.analytics.shared.AppJson
import kotlinx.serialization.decodeFromString
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

enum class ImportArchiveLocation { IMPORTED, QUARANTINE }

data class ImportArchiveEntry(
    val reportPath: String,
    val logPath: String,
    val location: ImportArchiveLocation,
    val report: ImportReport? = null,
    val readError: String? = null,
    val lastModifiedMs: Long = 0L
) {
    val id: String get() = reportPath
}

data class ImportArchiveSnapshot(
    val imported: List<ImportArchiveEntry> = emptyList(),
    val quarantined: List<ImportArchiveEntry> = emptyList()
) {
    val unreadableCount: Int get() = (imported + quarantined).count { it.report == null }
}

/** Reads persisted import evidence and safely requeues quarantined logs for auto-import. */
class ImportArchiveService {

    fun load(projectPath: String): ImportArchiveSnapshot {
        val logsRoot = logsRoot(projectPath)
        return ImportArchiveSnapshot(
            imported = readEntries(File(logsRoot, "imported"), ImportArchiveLocation.IMPORTED),
            quarantined = readEntries(File(logsRoot, "quarantine"), ImportArchiveLocation.QUARANTINE)
        )
    }

    fun retry(projectPath: String, entry: ImportArchiveEntry): File {
        require(entry.location == ImportArchiveLocation.QUARANTINE) {
            "Only quarantined logs can be retried"
        }
        val logsRoot = logsRoot(projectPath).canonicalFile
        val quarantineRoot = File(logsRoot, "quarantine").canonicalFile
        val source = File(entry.logPath).canonicalFile
        require(source.toPath().startsWith(quarantineRoot.toPath())) {
            "Quarantined log is outside the active workspace"
        }
        require(source.isFile) { "Quarantined log is missing: ${source.name}" }

        logsRoot.mkdirs()
        val originalName = entry.report?.sourceName?.let(::safeFileName)
            ?.takeIf(::hasSupportedExtension)
            ?: safeFileName(source.name.substringAfter('_', source.name))
        require(hasSupportedExtension(originalName)) { "Unsupported quarantined log format: $originalName" }

        val destination = File(logsRoot, "retry_${UUID.randomUUID()}_$originalName")
        val partial = File(logsRoot, ".${destination.name}.partial")
        try {
            Files.copy(source.toPath(), partial.toPath(), StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.move(
                    partial.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial.toPath(), destination.toPath())
            }
        } finally {
            partial.delete()
        }
        return destination
    }

    private fun readEntries(directory: File, location: ImportArchiveLocation): List<ImportArchiveEntry> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file ->
            file.isFile && file.name.endsWith(AutoImportService.IMPORT_REPORT_SUFFIX)
        }.orEmpty().map { reportFile ->
            val logFile = File(
                reportFile.parentFile,
                reportFile.name.removeSuffix(AutoImportService.IMPORT_REPORT_SUFFIX)
            )
            try {
                ImportArchiveEntry(
                    reportPath = reportFile.absolutePath,
                    logPath = logFile.absolutePath,
                    location = location,
                    report = AppJson.decodeFromString<ImportReport>(reportFile.readText()),
                    lastModifiedMs = reportFile.lastModified()
                )
            } catch (failure: Exception) {
                ImportArchiveEntry(
                    reportPath = reportFile.absolutePath,
                    logPath = logFile.absolutePath,
                    location = location,
                    readError = failure.message ?: "Unreadable import report",
                    lastModifiedMs = reportFile.lastModified()
                )
            }
        }.sortedByDescending { it.lastModifiedMs }
    }

    private fun logsRoot(projectPath: String): File {
        require(projectPath.isNotBlank()) { "Workspace path is not configured" }
        return File(projectPath, "logs")
    }

    private fun safeFileName(value: String): String =
        File(value).name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun hasSupportedExtension(name: String): Boolean {
        val lower = name.lowercase()
        return AutoImportService.SUPPORTED_EXTENSIONS.any(lower::endsWith)
    }
}
