package com.ares.analytics.ui.controls

import com.ares.analytics.ui.components.controls.advancedBindingSummary
import com.ares.analytics.ui.components.controls.actionAccessibleLabel
import com.ares.analytics.ui.components.controls.actionBrowserGroups
import com.ares.analytics.ui.components.controls.actionCatalogSummary
import com.ares.analytics.ui.components.controls.hasAdvancedBindingSettings
import com.areslib.catalog.ActionDescriptor
import com.areslib.controls.ControlBindingDocument
import com.areslib.controls.ControlEvent
import com.areslib.controls.ControlSourceDocument
import com.areslib.controls.ControlSourceKind
import com.areslib.controls.ControlTargetDocument
import com.areslib.controls.ControlTargetKind
import com.areslib.controls.ControlTimingDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlsEditorPresentationTest {
    private val actions = listOf(
        ActionDescriptor(
            key = "intake.collect",
            displayName = "Collect game piece",
            description = "Starts the intake.",
            category = "Intake"
        ),
        ActionDescriptor(
            key = "SetIndicatorColor_GREEN",
            displayName = "Primary light: Green",
            description = "Sets the primary indicator light to green.",
            category = "Primary indicator"
        ),
        ActionDescriptor(
            key = "prism.setEffect",
            displayName = "Set Prism effect",
            description = "Changes the goBILDA Prism LED effect.",
            category = "Prism"
        )
    )

    private fun binding(timing: ControlTimingDocument = ControlTimingDocument()) = ControlBindingDocument(
        bindingId = "intake",
        displayName = "Run intake",
        source = ControlSourceDocument(ControlSourceKind.BUTTON, "driver", listOf("a")),
        event = ControlEvent.PRESS,
        target = ControlTargetDocument(ControlTargetKind.ACTION, "intake.start"),
        timing = timing
    )

    @Test
    fun `default timing stays collapsed with a plain-language summary`() {
        val binding = binding()

        assertFalse(hasAdvancedBindingSettings(binding))
        assertTrue(advancedBindingSummary(binding).contains("safe defaults"))
    }

    @Test
    fun `non-default safety timing is surfaced automatically`() {
        val binding = binding(ControlTimingDocument(maximumActiveSeconds = 2.0, cooldownSeconds = 0.25))

        assertTrue(hasAdvancedBindingSettings(binding))
        assertTrue(advancedBindingSummary(binding).contains("maximum active time"))
        assertTrue(advancedBindingSummary(binding).contains("cooldown"))
    }

    @Test
    fun `blank action search shows the entire catalog grouped with counts`() {
        val groups = actionBrowserGroups(actions, "")

        assertEquals(actions.size, groups.sumOf { it.actions.size })
        assertEquals(listOf("Intake", "Primary indicator", "Prism"), groups.map { it.category })
        assertEquals("3 actions in 3 categories", actionCatalogSummary(actions))
    }

    @Test
    fun `lighting aliases find indicator and Prism actions without relying on color swatches`() {
        assertEquals(
            setOf("SetIndicatorColor_GREEN", "prism.setEffect"),
            actionBrowserGroups(actions, "LED").flatMap { it.actions }.map { it.key }.toSet()
        )
        assertEquals(
            listOf("SetIndicatorColor_GREEN"),
            actionBrowserGroups(actions, "color green").flatMap { it.actions }.map { it.key }
        )
        assertEquals(
            listOf("prism.setEffect"),
            actionBrowserGroups(actions, "Prism").flatMap { it.actions }.map { it.key }
        )
    }

    @Test
    fun `action labels remain explicit and preserve stable catalog keys`() {
        val indicator = actions[1]

        assertEquals("SetIndicatorColor_GREEN", actionBrowserGroups(actions, "light").flatMap { it.actions }[0].key)
        assertTrue(actionAccessibleLabel(indicator).contains("Primary light: Green"))
        assertTrue(actionAccessibleLabel(indicator).contains("Sets the primary indicator light to green"))
    }
}
