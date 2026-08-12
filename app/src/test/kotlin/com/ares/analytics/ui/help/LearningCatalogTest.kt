package com.ares.analytics.ui.help

import com.ares.analytics.ui.components.NavigationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearningCatalogTest {
    @Test
    fun `catalog offers a hardware-free first success and real destinations`() {
        val first = LearningCatalog.lessons.first()
        assertEquals(LearningAction.START_SIMULATOR, first.action)
        assertFalse(first.requiresRobot)
        assertEquals(NavigationTarget.DASHBOARD, first.destination)
        assertTrue(LearningCatalog.lessons.all { it.steps.isNotEmpty() && it.successLooksLike.isNotBlank() })
    }

    @Test
    fun `search understands student tasks and concepts`() {
        assertTrue(LearningCatalog.search("disconnected").any { it.id == "read-connection-state" })
        assertTrue(LearningCatalog.search("redux").any { it.id == "safe-subsystem" })
        assertTrue(LearningCatalog.search("sysid", LearningLevel.STARTER).isEmpty())
    }

    @Test
    fun `contextual help opens the lesson for the active workflow`() {
        assertEquals("bring-in-run", LearningCatalog.lessonFor(NavigationTarget.IMPORT_CENTER)?.id)
        assertEquals("bring-in-run", LearningCatalog.lessonFor(NavigationTarget.RUN_HISTORY)?.id)
        assertEquals("first-routine", LearningCatalog.lessonFor(NavigationTarget.PATH_PLANNER)?.id)
        assertEquals("developer-reference", LearningCatalog.lessonFor(NavigationTarget.KDOC_VIEWER)?.id)
        assertEquals(null, LearningCatalog.lessonFor(NavigationTarget.ADMIN))
    }
}
