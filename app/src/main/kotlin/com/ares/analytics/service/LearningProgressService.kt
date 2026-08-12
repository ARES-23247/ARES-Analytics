package com.ares.analytics.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Durable, local-only progress for the in-app Help & Learn lessons. */
@Serializable
data class LearningProgress(
    val contentVersion: Int = CURRENT_LEARNING_CONTENT_VERSION,
    val practicedLessonIds: Set<String> = emptySet(),
)

/**
 * Stores self-reported lesson practice without claiming certification or hardware verification.
 * Content-version changes retain known lesson IDs and allow the UI to identify updated material.
 */
class LearningProgressService(
    private val progressFile: File = File(
        System.getProperty("user.home"),
        ".ares-analytics/learning-progress.json",
    ),
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val writeMutex = Mutex()
    private val _progress = MutableStateFlow(loadProgress())
    val progress: StateFlow<LearningProgress> = _progress.asStateFlow()

    suspend fun setPracticed(lessonId: String, practiced: Boolean) = withContext(Dispatchers.IO) {
        require(lessonId.isNotBlank()) { "Lesson ID must not be blank" }
        writeMutex.withLock {
            val current = _progress.value
            val updatedIds = if (practiced) {
                current.practicedLessonIds + lessonId
            } else {
                current.practicedLessonIds - lessonId
            }
            val updated = LearningProgress(practicedLessonIds = updatedIds)
            writeFileAtomically(progressFile) { temporary ->
                temporary.writeText(json.encodeToString(updated))
            }
            _progress.value = updated
        }
    }

    private fun loadProgress(): LearningProgress {
        if (!progressFile.isFile) return LearningProgress()
        return runCatching { json.decodeFromString<LearningProgress>(progressFile.readText()) }
            .getOrElse { LearningProgress() }
    }
}

const val CURRENT_LEARNING_CONTENT_VERSION = 1
