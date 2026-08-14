package com.ares.analytics.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationModelTest {
    @Test
    fun `primary navigation has six task sections`() {
        assertEquals(
            listOf("Dashboard", "Robot", "Autonomous", "Analysis", "Data", "Settings"),
            primaryNavigationSections.map { it.label }
        )
    }

    @Test
    fun `daily routes map to one contextual section`() {
        val grouped = primaryNavigationSections.flatMap { section -> section.targets().map { it to section } }.toMap()
        assertEquals(NavigationSection.ROBOT, grouped[NavigationTarget.CONTROLS])
        assertEquals(NavigationSection.ROBOT, grouped[NavigationTarget.TUNING])
        assertEquals(NavigationSection.ROBOT, grouped[NavigationTarget.DRIVEBASE_BUILDER])
        assertEquals(NavigationSection.ROBOT, grouped[NavigationTarget.ROBOT_STUDIO])
        assertEquals(NavigationTarget.ROBOT_STUDIO, NavigationSection.ROBOT.defaultTarget())
        assertEquals(NavigationSection.AUTONOMOUS, grouped[NavigationTarget.FIELD_EDITOR])
        assertFalse(grouped.containsKey(NavigationTarget.MATCH_STRATEGY))
        assertEquals(NavigationSection.DATA, grouped[NavigationTarget.CLOUD])
        assertEquals(NavigationSection.SETTINGS, grouped[NavigationTarget.ADMIN])
        assertEquals(grouped.size, grouped.keys.distinct().size)
    }

    @Test
    fun `developer utilities are progressively disclosed`() {
        val standard = availablePaletteTargets(developerMode = false)
        val developer = availablePaletteTargets(developerMode = true)
        assertTrue(developer.containsAll(developerToolTargets))
        assertFalse(standard.any { it in developerToolTargets })
        assertTrue(standard.contains(NavigationTarget.ACADEMY))
    }

    @Test
    fun `command search matches destination and group`() {
        assertEquals(listOf(NavigationTarget.TUNING), filterNavigationTargets("tuning", false))
        assertTrue(filterNavigationTargets("analysis", false).contains(NavigationTarget.RUN_HISTORY))
        assertTrue(filterNavigationTargets("database", false).isEmpty())
        assertEquals(listOf(NavigationTarget.DATABASE_VIEWER), filterNavigationTargets("database", true))
        assertEquals(listOf(NavigationTarget.ACADEMY), filterNavigationTargets("start here", false))
        assertTrue(filterNavigationTargets("disconnected", false).contains(NavigationTarget.DASHBOARD))
        assertTrue(filterNavigationTargets("gamepad", false).contains(NavigationTarget.CONTROLS))
        assertEquals(listOf(NavigationTarget.DRIVEBASE_BUILDER), filterNavigationTargets("mecanum", false))
        assertEquals(listOf(NavigationTarget.ROBOT_STUDIO), filterNavigationTargets("build robot", false))
    }
}
